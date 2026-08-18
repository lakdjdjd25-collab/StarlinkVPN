import { fail, ok } from "@/lib/api";
import { db } from "@/lib/db";

type SchemaState = {
  serviceVipAccess: boolean;
  nodeAccessTier: boolean;
  vipMigration: boolean;
  serviceManualUsedBytes: boolean;
  manualServerTable: boolean;
  trafficSessionTable: boolean;
  manualServerMigration: boolean;
};

export async function GET() {
  try {
    const [schema] = await db.$queryRaw<SchemaState[]>`
      SELECT
        EXISTS (
          SELECT 1 FROM information_schema.columns
          WHERE table_schema = 'public' AND table_name = 'Service' AND column_name = 'vipAccess'
        ) AS "serviceVipAccess",
        EXISTS (
          SELECT 1 FROM information_schema.columns
          WHERE table_schema = 'public' AND table_name = 'VpnNode' AND column_name = 'accessTier'
        ) AS "nodeAccessTier",
        EXISTS (
          SELECT 1 FROM "_prisma_migrations"
          WHERE migration_name = '20260818000500_vip_server_access'
            AND finished_at IS NOT NULL AND rolled_back_at IS NULL
        ) AS "vipMigration",
        EXISTS (
          SELECT 1 FROM information_schema.columns
          WHERE table_schema = 'public' AND table_name = 'Service' AND column_name = 'manualUsedBytes'
        ) AS "serviceManualUsedBytes",
        to_regclass('public."ManualServer"') IS NOT NULL AS "manualServerTable",
        to_regclass('public."TrafficSession"') IS NOT NULL AS "trafficSessionTable",
        EXISTS (
          SELECT 1 FROM "_prisma_migrations"
          WHERE migration_name = '20260818102000_manual_vless_servers'
            AND finished_at IS NOT NULL AND rolled_back_at IS NULL
        ) AS "manualServerMigration"
    `;

    if (!schema?.serviceVipAccess || !schema.nodeAccessTier || !schema.vipMigration ||
        !schema.serviceManualUsedBytes || !schema.manualServerTable || !schema.trafficSessionTable ||
        !schema.manualServerMigration) {
      return fail(503, "schema_not_ready", "Control-plane database schema is not ready");
    }

    return ok({
      status: "ok",
      database: "ready",
      schema: "manual-vless-v1",
      service: "quickping-control-plane",
      version: "2.6.0",
    });
  } catch {
    return fail(503, "database_unavailable", "Control-plane database is unavailable");
  }
}
