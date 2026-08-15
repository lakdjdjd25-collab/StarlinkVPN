import { createHash, createHmac, randomBytes, randomInt, timingSafeEqual } from "node:crypto";
import { compare, hash } from "bcryptjs";
import { jwtVerify, SignJWT, type JWTPayload } from "jose";
import type { UserRole } from "@/generated/prisma/enums";

const encoder = new TextEncoder();
const issuer = "quickping-control-plane";
const audience = "quickping-client";

export type SessionClaims = JWTPayload & {
  sub: string;
  role: UserRole;
  kind: "access" | "admin";
  serviceId?: string;
};

function secret(): Uint8Array {
  const value = process.env.JWT_SECRET;
  if (!value || value.length < 32) {
    if (process.env.NODE_ENV === "production") {
      throw new Error("JWT_SECRET must contain at least 32 characters");
    }
    return encoder.encode("quickping-local-development-secret-only");
  }
  return encoder.encode(value);
}

export async function issueToken(
  userId: string,
  role: UserRole,
  kind: "access" | "admin" = "access",
  serviceId?: string,
): Promise<string> {
  return new SignJWT({ role, kind, ...(kind === "access" && serviceId ? { serviceId } : {}) })
    .setProtectedHeader({ alg: "HS256", typ: "JWT" })
    .setSubject(userId)
    .setIssuer(issuer)
    .setAudience(audience)
    .setIssuedAt()
    .setExpirationTime(kind === "admin" ? "8h" : "15m")
    .sign(secret());
}

export async function verifyToken(
  token: string,
  expectedKind?: "access" | "admin",
): Promise<SessionClaims> {
  const result = await jwtVerify(token, secret(), { issuer, audience });
  const payload = result.payload as SessionClaims;
  if (!payload.sub || !payload.role || !payload.kind) {
    throw new Error("Incomplete token claims");
  }
  if (expectedKind && payload.kind !== expectedKind) {
    throw new Error("Unexpected token kind");
  }
  if (payload.serviceId !== undefined && (typeof payload.serviceId !== "string" || !payload.serviceId)) {
    throw new Error("Invalid service scope");
  }
  return payload;
}

export async function hashPassword(password: string): Promise<string> {
  return hash(password, 12);
}

export async function verifyPassword(
  password: string,
  passwordHash: string,
): Promise<boolean> {
  return compare(password, passwordHash);
}

export function createOpaqueToken(serviceId?: string): { raw: string; hash: string } {
  const entropy = randomBytes(48).toString("base64url");
  const raw = serviceId ? `${serviceId}.${entropy}` : entropy;
  return { raw, hash: hashOpaqueToken(raw) };
}

export function opaqueTokenServiceId(raw: string): string | null {
  const separator = raw.indexOf(".");
  if (separator <= 0) return null;
  const serviceId = raw.slice(0, separator);
  return /^[A-Za-z0-9_-]{1,128}$/.test(serviceId) ? serviceId : null;
}

export function hashOpaqueToken(raw: string): string {
  return createHash("sha256").update(raw).digest("hex");
}

function authCodeSecret(): string {
  const value = process.env.OTP_HASH_SECRET ?? process.env.JWT_SECRET;
  if (!value || value.length < 32) {
    if (process.env.NODE_ENV === "production") {
      throw new Error("OTP_HASH_SECRET or JWT_SECRET must contain at least 32 characters");
    }
    return "quickping-local-otp-secret-only-32";
  }
  return value;
}

export function createAuthCode(): string {
  return randomInt(0, 1_000_000).toString().padStart(6, "0");
}

export function hashAuthCode(challengeId: string, code: string): string {
  return createHmac("sha256", authCodeSecret())
    .update(`${challengeId}:${code}`)
    .digest("hex");
}

export function verifyAuthCode(challengeId: string, code: string, expectedHash: string): boolean {
  const actual = Buffer.from(hashAuthCode(challengeId, code), "hex");
  const expected = Buffer.from(expectedHash, "hex");
  return actual.length === expected.length && timingSafeEqual(actual, expected);
}

export function bearerToken(request: Request): string | null {
  const authorization = request.headers.get("authorization");
  if (!authorization?.startsWith("Bearer ")) return null;
  return authorization.slice(7).trim() || null;
}

export function adminCookieName(): string {
  return process.env.NODE_ENV === "production"
    ? "__Host-quickping-admin"
    : "quickping_admin";
}
