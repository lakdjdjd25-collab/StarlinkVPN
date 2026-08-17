import { fail, ok } from "@/lib/api";
import { db } from "@/lib/db";

type VipSchemaState = {
  serviceVipAccess: boolean;
  nodeAccessTier: boolean;
  vipMigration: boolean;
};

export async function GET() {
  try {
    const [schema] = await db.$queryRaw<VipSchemaState[]>`
      SELECT
        EXISTS (
          SELECT 1
          FROM information_schema.columns
          WHERE table_schema = 'public'
            AND table_name = 'Service'
            AND column_name = 'vipAccess'
        ) AS "serviceVipAccess",
        EXISTS (
          SELECT 1
          FROM information_schema.columns
          WHERE table_schema = 'public'
            AND table_name = 'VpnNode'
            AND column_name = 'accessTier'
        ) AS "nodeAccessTier",
        EXISTS (
          SELECT 1
          FROM "_prisma_migrations"
          WHERE migration_name = '20260818000500_vip_server_access'
            AND finished_at IS NOT NULL
            AND rolled_back_at IS NULL
        ) AS "vipMigration"
    `;

    if (!schema?.serviceVipAccess || !schema.nodeAccessTier || !schema.vipMigration) {
      return fail(503, "schema_not_ready", "Control-plane database schema is not ready");
    }

    return ok({
      status: "ok",
      database: "ready",
      schema: "vip-access-v1",
      service: "quickping-control-plane",
      version: "2.6.0",
    });
  } catch {
    return fail(503, "database_unavailable", "Control-plane database is unavailable");
  }
}
