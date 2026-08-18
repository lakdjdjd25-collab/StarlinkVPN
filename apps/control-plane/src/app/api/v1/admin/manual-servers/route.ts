import type { NextRequest } from "next/server";
import { z } from "zod";
import { adminFromRequest, isSameOrigin } from "@/lib/admin-session";
import { fail, ok } from "@/lib/api";
import { decryptConfig, encryptConfig } from "@/lib/config-encryption";
import { db } from "@/lib/db";
import { countryFlag, detectGeoCountry, overrideVlessEndpoint, parseVlessUri } from "@/lib/manual-vless";

const categorySchema = z.enum(["UNLIMITED", "LIMITED"]);
const hostSchema = z.string().trim().min(1).max(253).refine((value) => !/\s/.test(value), "آدرس سرور معتبر نیست");
const subcategorySchema = z.string().trim().min(1).max(80);
const volumeGbSchema = z.number().positive().max(100_000).nullable().optional();

const createSchema = z.object({
  config: z.string().trim().min(10).max(16_384),
  displayName: z.string().trim().min(2).max(120),
  host: hostSchema,
  port: z.number().int().min(1).max(65_535),
  category: categorySchema,
  subcategory: subcategorySchema,
  volumeGb: volumeGbSchema,
  accessTier: z.enum(["STANDARD", "VIP"]),
  enabled: z.boolean().default(true),
  countTraffic: z.boolean().default(true),
  sortOrder: z.number().int().min(-100_000).max(100_000).default(0),
  countryOverride: z.string().trim().min(2).max(80),
  countryCode: z.string().trim().regex(/^[A-Za-z]{2}$/).optional().nullable(),
}).superRefine((value, context) => {
  if (value.category === "LIMITED" && !value.volumeGb) {
    context.addIssue({
      code: "custom",
      path: ["volumeGb"],
      message: "برای دسته Limited حجم سرور لازم است",
    });
  }
});

const updateSchema = z.object({
  id: z.string().min(1),
  config: z.string().trim().min(10).max(16_384).optional(),
  displayName: z.string().trim().min(2).max(120).optional(),
  host: hostSchema.optional(),
  port: z.number().int().min(1).max(65_535).optional(),
  category: categorySchema.optional(),
  subcategory: subcategorySchema.optional(),
  volumeGb: volumeGbSchema,
  accessTier: z.enum(["STANDARD", "VIP"]).optional(),
  enabled: z.boolean().optional(),
  countTraffic: z.boolean().optional(),
  sortOrder: z.number().int().min(-100_000).max(100_000).optional(),
  countryOverride: z.string().trim().min(2).max(80).optional().nullable(),
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

function bytesFromGb(value: number): bigint {
  return BigInt(Math.round(value * 1024 ** 3));
}

function normalizedCategory(value: "UNLIMITED" | "GAMING" | "LIMITED"): "UNLIMITED" | "LIMITED" {
  return value === "UNLIMITED" ? "UNLIMITED" : "LIMITED";
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
        subcategory: true,
        volumeBytes: true,
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
      category: normalizedCategory(server.category),
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
  if (!input.success) {
    return fail(400, "invalid_input", input.error.issues[0]?.message ?? "اطلاعات سرور دستی معتبر نیست");
  }
  try {
    const parsed = parseVlessUri(input.data.config);
    const runtimeConfig = overrideVlessEndpoint(parsed.runtimeConfig, input.data.host, input.data.port);
    const geo = await detectGeoCountry(input.data.host);
    const volumeBytes = input.data.category === "LIMITED" && input.data.volumeGb
      ? bytesFromGb(input.data.volumeGb)
      : null;
    const node = await db.manualServer.create({
      data: {
        displayName: input.data.displayName,
        sourceCiphertext: encryptConfig({ uri: input.data.config }),
        configCiphertext: encryptConfig(runtimeConfig),
        protocol: parsed.protocol,
        host: input.data.host,
        port: input.data.port,
        country: geo?.country ?? null,
        countryCode: (input.data.countryCode || geo?.countryCode || null)?.toUpperCase() ?? null,
        countryOverride: input.data.countryOverride,
        category: input.data.category,
        subcategory: input.data.subcategory,
        volumeBytes,
        accessTier: input.data.accessTier,
        enabled: input.data.enabled,
        sortOrder: input.data.sortOrder,
        countTraffic: input.data.countTraffic,
      },
      select: {
        id: true, displayName: true, host: true, port: true, country: true, countryCode: true,
        countryOverride: true, category: true, subcategory: true, volumeBytes: true,
        accessTier: true, enabled: true, sortOrder: true, countTraffic: true,
        createdAt: true, updatedAt: true,
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
          country: node.countryOverride || node.country,
          category: normalizedCategory(node.category),
          subcategory: node.subcategory,
          volumeBytes: node.volumeBytes?.toString() ?? null,
          accessTier: node.accessTier,
          enabled: node.enabled,
          countTraffic: node.countTraffic,
          sortOrder: node.sortOrder,
        },
      },
    });
    return ok({ ...node, category: normalizedCategory(node.category) }, { status: 201 });
  } catch (error) {
    return fail(400, "invalid_vless_config", parserMessage(error));
  }
}

export async function PATCH(request: NextRequest) {
  if (!isSameOrigin(request)) return fail(403, "invalid_origin", "مبدأ درخواست معتبر نیست");
  const admin = await adminFromRequest(request);
  if (!admin || admin.role !== "ADMIN") return fail(403, "forbidden", "فقط مدیر اصلی مجاز است");
  const input = updateSchema.safeParse(await request.json().catch(() => null));
  if (!input.success) {
    return fail(400, "invalid_input", input.error.issues[0]?.message ?? "تغییرات سرور دستی معتبر نیست");
  }
  const before = await db.manualServer.findFirst({
    where: { id: input.data.id, deletedAt: null },
    select: {
      id: true, displayName: true, host: true, port: true, country: true, countryCode: true,
      countryOverride: true, category: true, subcategory: true, volumeBytes: true,
      accessTier: true, enabled: true, sortOrder: true, countTraffic: true,
      configCiphertext: true,
    },
  });
  if (!before) return fail(404, "manual_server_not_found", "سرور دستی پیدا نشد");

  const nextCategory = input.data.category ?? normalizedCategory(before.category);
  const nextVolumeBytes = nextCategory === "UNLIMITED"
    ? null
    : input.data.volumeGb === undefined
      ? before.volumeBytes
      : input.data.volumeGb === null
        ? null
        : bytesFromGb(input.data.volumeGb);
  if (nextCategory === "LIMITED" && !nextVolumeBytes &&
      (input.data.category !== undefined || input.data.volumeGb !== undefined || before.category === "LIMITED")) {
    return fail(400, "invalid_input", "برای دسته Limited حجم سرور لازم است");
  }

  try {
    const parsed = input.data.config ? parseVlessUri(input.data.config) : null;
    const nextHost = input.data.host ?? parsed?.host ?? before.host;
    const nextPort = input.data.port ?? parsed?.port ?? before.port;
    const endpointChanged = Boolean(parsed) || input.data.host !== undefined || input.data.port !== undefined;
    const runtimeBase = parsed?.runtimeConfig ?? decryptConfig<Record<string, unknown>>(before.configCiphertext);
    const runtimeConfig = endpointChanged
      ? overrideVlessEndpoint(runtimeBase, nextHost, nextPort)
      : runtimeBase;
    const geo = endpointChanged ? await detectGeoCountry(nextHost) : null;

    const updated = await db.$transaction(async (tx) => {
      const node = await tx.manualServer.update({
        where: { id: before.id },
        data: {
          ...(input.data.displayName !== undefined ? { displayName: input.data.displayName } : {}),
          ...(input.data.category !== undefined ? { category: input.data.category } : {}),
          ...(input.data.subcategory !== undefined ? { subcategory: input.data.subcategory } : {}),
          ...((input.data.category !== undefined || input.data.volumeGb !== undefined) ? { volumeBytes: nextVolumeBytes } : {}),
          ...(input.data.accessTier !== undefined ? { accessTier: input.data.accessTier } : {}),
          ...(input.data.enabled !== undefined ? { enabled: input.data.enabled } : {}),
          ...(input.data.countTraffic !== undefined ? { countTraffic: input.data.countTraffic } : {}),
          ...(input.data.sortOrder !== undefined ? { sortOrder: input.data.sortOrder } : {}),
          ...(input.data.countryOverride !== undefined ? { countryOverride: input.data.countryOverride || null } : {}),
          ...(input.data.countryCode !== undefined ? { countryCode: input.data.countryCode?.toUpperCase() || null } : {}),
          ...(endpointChanged ? {
            configCiphertext: encryptConfig(runtimeConfig),
            host: nextHost,
            port: nextPort,
            country: geo?.country ?? before.country,
            ...(input.data.countryCode === undefined && geo?.countryCode ? { countryCode: geo.countryCode } : {}),
          } : {}),
          ...(parsed ? {
            sourceCiphertext: encryptConfig({ uri: input.data.config }),
            protocol: parsed.protocol,
          } : {}),
        },
        select: {
          id: true, displayName: true, host: true, port: true, country: true, countryCode: true,
          countryOverride: true, category: true, subcategory: true, volumeBytes: true,
          accessTier: true, enabled: true, sortOrder: true, countTraffic: true,
          createdAt: true, updatedAt: true,
        },
      });

      const revokesAllActiveSessions = input.data.enabled === false || endpointChanged;
      const restrictsToVip = before.accessTier === "STANDARD" && input.data.accessTier === "VIP";
      if (revokesAllActiveSessions) {
        await tx.trafficSession.updateMany({
          where: { manualServerId: before.id, status: "ACTIVE" },
          data: { status: "REVOKED", endedAt: new Date() },
        });
      } else if (restrictsToVip) {
        await tx.trafficSession.updateMany({
          where: {
            manualServerId: before.id,
            status: "ACTIVE",
            service: { vipAccess: false },
          },
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
          country: before.countryOverride || before.country,
          category: normalizedCategory(before.category),
          subcategory: before.subcategory,
          volumeBytes: before.volumeBytes?.toString() ?? null,
          accessTier: before.accessTier,
          enabled: before.enabled,
          countTraffic: before.countTraffic,
          sortOrder: before.sortOrder,
        },
        after: {
          displayName: updated.displayName,
          host: updated.host,
          port: updated.port,
          country: updated.countryOverride || updated.country,
          category: normalizedCategory(updated.category),
          subcategory: updated.subcategory,
          volumeBytes: updated.volumeBytes?.toString() ?? null,
          accessTier: updated.accessTier,
          enabled: updated.enabled,
          countTraffic: updated.countTraffic,
          sortOrder: updated.sortOrder,
          configReplaced: Boolean(parsed),
        },
      },
    });
    return ok({ ...updated, category: normalizedCategory(updated.category) });
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
