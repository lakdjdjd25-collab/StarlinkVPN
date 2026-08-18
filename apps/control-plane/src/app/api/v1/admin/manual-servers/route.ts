import type { NextRequest } from "next/server";
import { z } from "zod";
import { adminFromRequest, isSameOrigin } from "@/lib/admin-session";
import { fail, ok } from "@/lib/api";
import { encryptConfig } from "@/lib/config-encryption";
import { db } from "@/lib/db";
import { countryFlag, detectGeoCountry, parseVlessUri } from "@/lib/manual-vless";

const createSchema = z.object({
  config: z.string().trim().min(10).max(16_384),
  displayName: z.string().trim().min(2).max(120),
  category: z.enum(["UNLIMITED", "GAMING"]),
  accessTier: z.enum(["STANDARD", "VIP"]),
  enabled: z.boolean().default(true),
  sortOrder: z.number().int().min(-100_000).max(100_000).default(0),
  countryOverride: z.string().trim().max(80).optional().nullable(),
  countryCode: z.string().trim().regex(/^[A-Za-z]{2}$/).optional().nullable(),
});

const updateSchema = z.object({
  id: z.string().min(1),
  config: z.string().trim().min(10).max(16_384).optional(),
  displayName: z.string().trim().min(2).max(120).optional(),
  category: z.enum(["UNLIMITED", "GAMING"]).optional(),
  accessTier: z.enum(["STANDARD", "VIP"]).optional(),
  enabled: z.boolean().optional(),
  sortOrder: z.number().int().min(-100_000).max(100_000).optional(),
  countryOverride: z.string().trim().max(80).optional().nullable(),
  countryCode: z.string().trim().regex(/^[A-Za-z]{2}$/).optional().nullable(),
});

const deleteSchema = z.object({ id: z.string().min(1) });

type StatsRow = {
  manualServerId: string;
  totalTraffic: bigint;
  sessions: bigint;
  uniqueUsers: bigint;
  lastUsed: Date | null;
};

function parserMessage(error: unknown): string {
  const code = error instanceof Error ? error.message : "VLESS_CONFIG_INVALID";
  const messages: Record<string, string> = {
    VLESS_CONFIG_REQUIRED: "لینک باید با vless:// شروع شود",
    VLESS_CONFIG_INVALID: "ساختار لینک VLESS معتبر نیست",
    VLESS_UUID_INVALID: "UUID لینک VLESS معتبر نیست",
    VLESS_ENDPOINT_INVALID: "آدرس یا پورت سرور معتبر نیست",
    VLESS_TRANSPORT_UNSUPPORTED: "Transport این لینک پشتیبانی نمی‌شود",
    VLESS_SECURITY_UNSUPPORTED: "Security این لینک پشتیبانی نمی‌شود",
    VLESS_REALITY_KEY_REQUIRED: "کلید عمومی Reality در لینک وجود ندارد",
    VLESS_RUNTIME_INVALID: "این لینک به پیکربندی معتبر sing-box تبدیل نشد",
  };
  return messages[code] ?? "لینک VLESS قابل استفاده نیست";
}

export async function GET(request: NextRequest) {
  const admin = await adminFromRequest(request);
  if (!admin) return fail(401, "unauthorized", "ورود مدیر لازم است");
  const [servers, stats] = await Promise.all([
    db.manualServer.findMany({
      where: { deletedAt: null },
      select: {
        id: true,
        displayName: true,
        protocol: true,
        host: true,
        port: true,
        country: true,
        countryCode: true,
        countryOverride: true,
        category: true,
        accessTier: true,
        enabled: true,
        sortOrder: true,
        countTraffic: true,
        lastUsedAt: true,
        createdAt: true,
        updatedAt: true,
      },
      orderBy: [{ sortOrder: "asc" }, { createdAt: "asc" }],
    }),
    db.$queryRaw<StatsRow[]>`
      SELECT
        "manualServerId",
        COALESCE(SUM("totalBytes"), 0)::bigint AS "totalTraffic",
        COUNT(*)::bigint AS "sessions",
        COUNT(DISTINCT "serviceId")::bigint AS "uniqueUsers",
        MAX(COALESCE("lastReportAt", "startedAt")) AS "lastUsed"
      FROM "TrafficSession"
      GROUP BY "manualServerId"
    `,
  ]);
  const statsById = new Map(stats.map((item) => [item.manualServerId, item]));
  return ok(servers.map((server) => {
    const item = statsById.get(server.id);
    const countryCode = server.countryCode ?? "global";
    return {
      ...server,
      displayCountry: server.countryOverride || server.country || "Unknown",
      flag: countryFlag(countryCode),
      stats: {
        totalTraffic: item?.totalTraffic ?? 0n,
        sessions: item?.sessions ?? 0n,
        uniqueUsers: item?.uniqueUsers ?? 0n,
        lastUsed: item?.lastUsed ?? server.lastUsedAt,
      },
    };
  }));
}

export async function POST(request: NextRequest) {
  if (!isSameOrigin(request)) return fail(403, "invalid_origin", "مبدأ درخواست معتبر نیست");
  const admin = await adminFromRequest(request);
  if (!admin || admin.role !== "ADMIN") return fail(403, "forbidden", "فقط مدیر اصلی مجاز است");
  const input = createSchema.safeParse(await request.json().catch(() => null));
  if (!input.success) return fail(400, "invalid_input", "اطلاعات سرور دستی معتبر نیست");
  try {
    const parsed = parseVlessUri(input.data.config);
    const geo = await detectGeoCountry(parsed.host);
    const node = await db.manualServer.create({
      data: {
        displayName: input.data.displayName,
        sourceCiphertext: encryptConfig({ uri: input.data.config }),
        configCiphertext: encryptConfig(parsed.runtimeConfig),
        protocol: parsed.protocol,
        host: parsed.host,
        port: parsed.port,
        country: geo?.country ?? null,
        countryCode: (input.data.countryCode || geo?.countryCode || null)?.toUpperCase() ?? null,
        countryOverride: input.data.countryOverride || null,
        category: input.data.category,
        accessTier: input.data.accessTier,
        enabled: input.data.enabled,
        sortOrder: input.data.sortOrder,
        countTraffic: true,
      },
      select: {
        id: true, displayName: true, host: true, port: true, country: true, countryCode: true,
        countryOverride: true, category: true, accessTier: true, enabled: true, sortOrder: true,
        countTraffic: true, createdAt: true, updatedAt: true,
      },
    });
    await db.auditLog.create({
      data: {
        actorId: admin.sub,
        action: "manual_server.create",
        entityType: "ManualServer",
        entityId: node.id,
        after: {
          displayName: node.displayName,
          host: node.host,
          port: node.port,
          category: node.category,
          accessTier: node.accessTier,
          enabled: node.enabled,
          sortOrder: node.sortOrder,
        },
      },
    });
    return ok(node, { status: 201 });
  } catch (error) {
    return fail(400, "invalid_vless_config", parserMessage(error));
  }
}

export async function PATCH(request: NextRequest) {
  if (!isSameOrigin(request)) return fail(403, "invalid_origin", "مبدأ درخواست معتبر نیست");
  const admin = await adminFromRequest(request);
  if (!admin || admin.role !== "ADMIN") return fail(403, "forbidden", "فقط مدیر اصلی مجاز است");
  const input = updateSchema.safeParse(await request.json().catch(() => null));
  if (!input.success) return fail(400, "invalid_input", "تغییرات سرور دستی معتبر نیست");
  const before = await db.manualServer.findFirst({
    where: { id: input.data.id, deletedAt: null },
    select: {
      id: true, displayName: true, host: true, port: true, country: true, countryCode: true,
      countryOverride: true, category: true, accessTier: true, enabled: true, sortOrder: true,
    },
  });
  if (!before) return fail(404, "manual_server_not_found", "سرور دستی پیدا نشد");
  try {
    const parsed = input.data.config ? parseVlessUri(input.data.config) : null;
    const geo = parsed ? await detectGeoCountry(parsed.host) : null;
    const updated = await db.$transaction(async (tx) => {
      const node = await tx.manualServer.update({
        where: { id: before.id },
        data: {
          ...(input.data.displayName !== undefined ? { displayName: input.data.displayName } : {}),
          ...(input.data.category !== undefined ? { category: input.data.category } : {}),
          ...(input.data.accessTier !== undefined ? { accessTier: input.data.accessTier } : {}),
          ...(input.data.enabled !== undefined ? { enabled: input.data.enabled } : {}),
          ...(input.data.sortOrder !== undefined ? { sortOrder: input.data.sortOrder } : {}),
          ...(input.data.countryOverride !== undefined ? { countryOverride: input.data.countryOverride || null } : {}),
          ...(input.data.countryCode !== undefined ? { countryCode: input.data.countryCode?.toUpperCase() || null } : {}),
          ...(parsed ? {
            sourceCiphertext: encryptConfig({ uri: input.data.config }),
            configCiphertext: encryptConfig(parsed.runtimeConfig),
            protocol: parsed.protocol,
            host: parsed.host,
            port: parsed.port,
            country: geo?.country ?? null,
            ...(input.data.countryCode === undefined ? { countryCode: geo?.countryCode ?? null } : {}),
          } : {}),
        },
        select: {
          id: true, displayName: true, host: true, port: true, country: true, countryCode: true,
          countryOverride: true, category: true, accessTier: true, enabled: true, sortOrder: true,
          countTraffic: true, createdAt: true, updatedAt: true,
        },
      });
      if (input.data.enabled === false || input.data.accessTier === "VIP") {
        await tx.trafficSession.updateMany({
          where: { manualServerId: before.id, status: "ACTIVE" },
          data: { status: "REVOKED", endedAt: new Date() },
        });
      }
      return node;
    });
    await db.auditLog.create({
      data: {
        actorId: admin.sub,
        action: "manual_server.update",
        entityType: "ManualServer",
        entityId: updated.id,
        before: {
          displayName: before.displayName,
          host: before.host,
          port: before.port,
          category: before.category,
          accessTier: before.accessTier,
          enabled: before.enabled,
          sortOrder: before.sortOrder,
        },
        after: {
          displayName: updated.displayName,
          host: updated.host,
          port: updated.port,
          category: updated.category,
          accessTier: updated.accessTier,
          enabled: updated.enabled,
          sortOrder: updated.sortOrder,
          configReplaced: Boolean(parsed),
        },
      },
    });
    return ok(updated);
  } catch (error) {
    return fail(400, "invalid_vless_config", parserMessage(error));
  }
}

export async function DELETE(request: NextRequest) {
  if (!isSameOrigin(request)) return fail(403, "invalid_origin", "مبدأ درخواست معتبر نیست");
  const admin = await adminFromRequest(request);
  if (!admin || admin.role !== "ADMIN") return fail(403, "forbidden", "فقط مدیر اصلی مجاز است");
  const input = deleteSchema.safeParse(await request.json().catch(() => null));
  if (!input.success) return fail(400, "invalid_input", "شناسه سرور معتبر نیست");
  const before = await db.manualServer.findFirst({
    where: { id: input.data.id, deletedAt: null },
    select: { id: true, displayName: true, accessTier: true, enabled: true },
  });
  if (!before) return fail(404, "manual_server_not_found", "سرور دستی پیدا نشد");
  await db.$transaction(async (tx) => {
    await tx.manualServer.update({
      where: { id: before.id },
      data: { enabled: false, deletedAt: new Date() },
    });
    await tx.trafficSession.updateMany({
      where: { manualServerId: before.id, status: "ACTIVE" },
      data: { status: "REVOKED", endedAt: new Date() },
    });
  });
  await db.auditLog.create({
    data: {
      actorId: admin.sub,
      action: "manual_server.delete",
      entityType: "ManualServer",
      entityId: before.id,
      before: { displayName: before.displayName, accessTier: before.accessTier, enabled: before.enabled },
      after: { deleted: true },
    },
  });
  return ok({ deleted: true });
}
