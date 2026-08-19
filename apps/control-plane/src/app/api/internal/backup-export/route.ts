import { createHash, timingSafeEqual } from "node:crypto";
import { gzipSync } from "node:zlib";
import { createRemoteJWKSet, jwtVerify } from "jose";
import type { NextRequest } from "next/server";
import { Pool } from "pg";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";

const GITHUB_OIDC_AUDIENCE = "nimhub-backup-20260819";
const GITHUB_OIDC_ISSUER = "https://token.actions.githubusercontent.com";
const GITHUB_REPOSITORY = "lakdjdjd25-collab/StarlinkVPN";
const githubJwks = createRemoteJWKSet(new URL(`${GITHUB_OIDC_ISSUER}/.well-known/jwks`));

const BACKEND_ENV_KEYS = [
  "ADMIN_EMAIL",
  "ADMIN_PASSWORD",
  "CONFIG_ENCRYPTION_KEY",
  "DATABASE_URL",
  "DIAG_NONCE",
  "GOOGLE_WEB_CLIENT_ID",
  "JWT_SECRET",
  "NEXT_TELEMETRY_DISABLED",
  "NIMHUB_DEPLOY_REFRESH",
  "NIMHUB_RELEASE_ID",
  "NO_CACHE",
  "OTP_HASH_SECRET",
  "PASARGUARD_BASE_URL",
  "PASARGUARD_PASSWORD",
  "PASARGUARD_USERNAME",
  "PUBLIC_APP_URL",
  "RAILPACK_INSTALL_CMD",
  "RAILPACK_PACKAGES",
  "SEED_DEMO_DATA",
  "SIGNUP_ENABLED",
] as const;

function quoteIdentifier(value: string): string {
  return `"${value.replaceAll('"', '""')}"`;
}

function tokenIsValid(value: string | null): boolean {
  const expected = process.env.BACKUP_TOKEN_HASH ?? "";
  if (!value || !/^[0-9a-f]{64}$/i.test(expected)) return false;
  const actual = createHash("sha256").update(value).digest("hex");
  const left = Buffer.from(actual, "hex");
  const right = Buffer.from(expected, "hex");
  return left.length === right.length && timingSafeEqual(left, right);
}

async function githubActionsAuthorized(request: NextRequest): Promise<boolean> {
  const authorization = request.headers.get("authorization");
  if (!authorization?.startsWith("Bearer ")) return false;
  const token = authorization.slice("Bearer ".length).trim();
  if (!token) return false;
  try {
    const { payload } = await jwtVerify(token, githubJwks, {
      issuer: GITHUB_OIDC_ISSUER,
      audience: GITHUB_OIDC_AUDIENCE,
    });
    return payload.repository === GITHUB_REPOSITORY
      && payload.repository_owner === "lakdjdjd25-collab"
      && payload.event_name === "pull_request";
  } catch {
    return false;
  }
}

function jsonReplacer(_key: string, value: unknown): unknown {
  if (typeof value === "bigint") return { __type: "bigint", value: value.toString() };
  if (Buffer.isBuffer(value)) return { __type: "buffer", base64: value.toString("base64") };
  return value;
}

function exportedEnvironment() {
  const controlPlane: Record<string, string> = {};
  for (const key of BACKEND_ENV_KEYS) {
    const value = process.env[key];
    if (value !== undefined) controlPlane[key] = value;
  }

  const postgres: Record<string, string> = {};
  const databaseUrl = process.env.DATABASE_URL;
  if (databaseUrl) {
    try {
      const parsed = new URL(databaseUrl);
      postgres.PGUSER = decodeURIComponent(parsed.username);
      postgres.PGPASSWORD = decodeURIComponent(parsed.password);
      postgres.PGHOST = parsed.hostname;
      postgres.PGPORT = parsed.port || "5432";
      postgres.PGDATABASE = parsed.pathname.replace(/^\//, "");
      postgres.DATABASE_URL = databaseUrl;
    } catch {
      postgres.DATABASE_URL = databaseUrl;
    }
  }

  return { controlPlane, postgres };
}

export async function GET(request: NextRequest) {
  const authorized = tokenIsValid(request.nextUrl.searchParams.get("token"))
    || await githubActionsAuthorized(request);
  if (!authorized) return new Response("not found", { status: 404 });

  const connectionString = process.env.DATABASE_URL;
  if (!connectionString) return new Response("DATABASE_URL missing", { status: 500 });

  const pool = new Pool({ connectionString, max: 1 });
  const client = await pool.connect();
  try {
    await client.query("BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY");

    const tableResult = await client.query<{ tablename: string }>(
      "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename",
    );
    const columnResult = await client.query(
      `SELECT table_name, column_name, ordinal_position, data_type, udt_name, is_nullable, column_default
       FROM information_schema.columns
       WHERE table_schema = 'public'
       ORDER BY table_name, ordinal_position`,
    );
    const constraintResult = await client.query(
      `SELECT c.conname AS name, c.contype AS type, rel.relname AS table_name,
              pg_get_constraintdef(c.oid, true) AS definition
       FROM pg_constraint c
       JOIN pg_class rel ON rel.oid = c.conrelid
       JOIN pg_namespace n ON n.oid = rel.relnamespace
       WHERE n.nspname = 'public'
       ORDER BY rel.relname, c.conname`,
    );
    const indexResult = await client.query(
      `SELECT tablename AS table_name, indexname AS index_name, indexdef AS definition
       FROM pg_indexes
       WHERE schemaname = 'public'
       ORDER BY tablename, indexname`,
    );
    const sequenceResult = await client.query<{ sequence_name: string }>(
      "SELECT sequence_name FROM information_schema.sequences WHERE sequence_schema = 'public' ORDER BY sequence_name",
    );

    const tables: Record<string, unknown[]> = {};
    for (const { tablename } of tableResult.rows) {
      const rows = await client.query(`SELECT * FROM public.${quoteIdentifier(tablename)}`);
      tables[tablename] = rows.rows;
    }

    const sequences: Record<string, { lastValue: string; isCalled: boolean }> = {};
    for (const { sequence_name: sequenceName } of sequenceResult.rows) {
      const result = await client.query<{ last_value: string; is_called: boolean }>(
        `SELECT last_value::text, is_called FROM public.${quoteIdentifier(sequenceName)}`,
      );
      if (result.rows[0]) {
        sequences[sequenceName] = {
          lastValue: result.rows[0].last_value,
          isCalled: result.rows[0].is_called,
        };
      }
    }

    await client.query("COMMIT");

    const snapshot = {
      format: "NimHUBBackendBackup/v1",
      createdAt: new Date().toISOString(),
      database: {
        schema: "public",
        tables,
        columns: columnResult.rows,
        constraints: constraintResult.rows,
        indexes: indexResult.rows,
        sequences,
      },
      environment: exportedEnvironment(),
    };
    const json = Buffer.from(JSON.stringify(snapshot, jsonReplacer, 2), "utf8");
    const compressed = gzipSync(json, { level: 9 });
    const digest = createHash("sha256").update(compressed).digest("hex");

    return new Response(compressed, {
      status: 200,
      headers: {
        "content-type": "application/gzip",
        "content-disposition": 'attachment; filename="nimhub-backend-backup.json.gz"',
        "cache-control": "no-store, no-cache, must-revalidate, private",
        pragma: "no-cache",
        "x-content-type-options": "nosniff",
        "x-backup-sha256": digest,
      },
    });
  } catch (error) {
    await client.query("ROLLBACK").catch(() => undefined);
    console.error("backup export failed", error instanceof Error ? error.message : "unknown error");
    return new Response("backup export failed", { status: 500 });
  } finally {
    client.release();
    await pool.end();
  }
}
