import type { NextRequest } from "next/server";
import { z } from "zod";
import { Prisma } from "@/generated/prisma/client";
import { adminFromRequest, isSameOrigin } from "@/lib/admin-session";
import { fail, ok } from "@/lib/api";
import { db } from "@/lib/db";
import { PasarGuardError } from "@/lib/pasarguard/client";
import {
  activatePasarGuardProvider,
  activePasarGuardProviderSummary,
  createPasarGuardClient,
  isPasarGuardConfigured,
  savePasarGuardPlanMapping,
  syncActivePasarGuardProfiles,
  testPasarGuardProvider,
} from "@/lib/pasarguard/provider";
import { migratePasarGuardBindingToActiveProvider } from "@/lib/pasarguard/migration";
import { bindPasarGuardUser, syncPasarGuardBinding } from "@/lib/pasarguard/sync";

const credentials = z.object({
  baseUrl: z.string().url().max(500),
  username: z.string().trim().min(1).max(200),
  password: z.string().min(1).max(1000),
});

const actionSchema = z.discriminatedUnion("action", [
  z.object({
    action: z.literal("bind"),
    quickPingUserId: z.string().min(1),
    externalUserId: z.number().int().positive(),
  }),
  z.object({ action: z.literal("sync"), bindingId: z.string().min(1) }),
  z.object({ action: z.literal("test_connection"), ...credentials.shape }),
  z.object({ action: z.literal("activate_provider"), ...credentials.shape }),
  z.object({ action: z.literal("sync_profiles") }),
  z.object({
    action: z.literal("map_plan"),
    planId: z.string().min(1),
    profileKey: z.string().regex(/^(template|group):[1-9]\d*$/),
  }),
  z.object({
    action: z.literal("migrate_batch"),
    limit: z.number().int().min(1).max(50).default(20),
    excludeBindingIds: z.array(z.string().min(1)).max(1000).default([]),
  }),
]);

function integrationFailure(error: unknown) {
  if (error instanceof PasarGuardError) {
    const status = error.code === "not_configured" ? 503
      : error.code === "invalid_response" ? 422
        : error.code === "unauthorized" ? 401
          : 502;
    return fail(status, `pasarguard_${error.code}`, error.message);
  }
  return fail(500, "pasarguard_sync_failed", "اتصال پاسارگارد با خطای پیش‌بینی‌نشده متوقف شد");
}

export async function GET(request: NextRequest) {
  const admin = await adminFromRequest(request);
  if (!admin) return fail(401, "unauthorized", "ورود مدیر لازم است");
  try {
    const configured = await isPasarGuardConfigured();
    const [activeProvider, bindings, providers, mappings] = await Promise.all([
      activePasarGuardProviderSummary(),
      db.pasarGuardBinding.findMany({
        orderBy: { createdAt: "desc" },
        include: {
          provider: { select: { id: true, name: true, baseUrl: true, active: true, lastSyncAt: true, lastError: true } },
          service: { include: { user: { select: { id: true, email: true } }, plan: { select: { id: true, name: true } } } },
          nodes: { select: { id: true, name: true, status: true } },
        },
      }),
      db.pasarGuardProvider.findMany({
        orderBy: { updatedAt: "desc" },
        select: { id: true, name: true, baseUrl: true, username: true, active: true, lastTestAt: true, lastSyncAt: true, lastError: true },
      }),
      db.pasarGuardPlanMapping.findMany({
        include: { plan: { select: { id: true, name: true } }, provider: { select: { id: true, name: true, active: true } } },
        orderBy: { updatedAt: "desc" },
      }),
    ]);

    let profiles: Awaited<ReturnType<typeof syncActivePasarGuardProfiles>>["profiles"] = [];
    if (configured) {
      try { profiles = (await syncActivePasarGuardProfiles()).profiles; } catch { /* status is returned through provider */ }
    }

    if (request.nextUrl.searchParams.get("remote") === "1") {
      if (!configured) return fail(503, "pasarguard_not_configured", "اطلاعات پاسارگارد تنظیم نشده است");
      const client = await createPasarGuardClient();
      const users = await client.listUsers();
      return ok({
        configured,
        activeProvider,
        profiles,
        users: users.map((user) => ({
          id: user.id,
          username: user.username,
          status: user.status,
          usedTraffic: user.usedTraffic,
          dataLimit: user.dataLimit,
          expiresAt: user.expiresAt,
          groupIds: user.groupIds,
        })),
      });
    }

    return ok({
      configured,
      activeProvider,
      profiles,
      providers,
      mappings: mappings.map((mapping) => ({
        id: mapping.id,
        providerId: mapping.providerId,
        providerName: mapping.provider.name,
        providerActive: mapping.provider.active,
        planId: mapping.planId,
        planName: mapping.plan.name,
        profileKey: mapping.profileKey,
        profileName: mapping.profileName,
        groupIds: mapping.groupIds,
        valid: mapping.valid,
        lastValidatedAt: mapping.lastValidatedAt,
      })),
      bindings: bindings.map((binding) => ({
        ...binding,
        needsMigration: Boolean(activeProvider && binding.providerId !== activeProvider.id),
      })),
    });
  } catch (error) {
    return integrationFailure(error);
  }
}

export async function POST(request: NextRequest) {
  if (!isSameOrigin(request)) return fail(403, "invalid_origin", "مبدأ درخواست معتبر نیست");
  const admin = await adminFromRequest(request);
  if (!admin || admin.role !== "ADMIN") return fail(403, "forbidden", "فقط مدیر اصلی مجاز است");
  const input = actionSchema.safeParse(await request.json().catch(() => null));
  if (!input.success) return fail(400, "invalid_input", "درخواست اتصال پاسارگارد معتبر نیست");

  try {
    if (input.data.action === "test_connection") {
      const tested = await testPasarGuardProvider(input.data);
      return ok({
        success: true,
        baseUrl: tested.baseUrl.toString(),
        username: tested.username,
        profileCount: tested.profiles.length,
        userCount: tested.userCount,
        profiles: tested.profiles,
      });
    }

    if (input.data.action === "activate_provider") {
      const activated = await activatePasarGuardProvider(input.data);
      await db.auditLog.create({
        data: {
          actorId: admin.sub,
          action: "pasarguard.activateProvider",
          entityType: "PasarGuardProvider",
          entityId: activated.provider.id,
          after: {
            baseUrl: activated.provider.baseUrl,
            username: activated.provider.username,
            profileCount: activated.profiles.length,
            userCount: activated.userCount,
          },
        },
      });
      return ok(activated);
    }

    if (input.data.action === "sync_profiles") {
      const result = await syncActivePasarGuardProfiles();
      await db.auditLog.create({
        data: {
          actorId: admin.sub,
          action: "pasarguard.syncProfiles",
          entityType: "PasarGuardProvider",
          entityId: result.providerId,
          after: { profileCount: result.profiles.length },
        },
      });
      return ok(result);
    }

    if (input.data.action === "map_plan") {
      const plan = await db.plan.findUnique({ where: { id: input.data.planId }, select: { id: true } });
      if (!plan) return fail(404, "plan_not_found", "پلن پیدا نشد");
      const mapping = await savePasarGuardPlanMapping(plan.id, input.data.profileKey);
      await db.auditLog.create({
        data: {
          actorId: admin.sub,
          action: "pasarguard.mapPlan",
          entityType: "Plan",
          entityId: plan.id,
          after: { providerId: mapping.providerId, profileKey: mapping.profileKey, profileName: mapping.profileName },
        },
      });
      return ok({ mapped: true, mapping });
    }

    if (input.data.action === "migrate_batch") {
      const client = await createPasarGuardClient();
      if (!client.providerId) return fail(503, "pasarguard_not_configured", "Provider فعال شناسه معتبر ندارد");
      const mappings = await db.pasarGuardPlanMapping.findMany({
        where: { providerId: client.providerId, valid: true },
        select: { planId: true },
      });
      const mappedPlanIds = [...new Set(mappings.map((mapping) => mapping.planId))];
      if (!mappedPlanIds.length) {
        return fail(409, "plan_mapping_required", "ابتدا گروه پنل فعال را برای پلن‌های NimHUB مشخص کنید");
      }
      const excluded = [...new Set(input.data.excludeBindingIds)];
      const baseWhere: Prisma.PasarGuardBindingWhereInput = {
        OR: [{ providerId: null }, { providerId: { not: client.providerId } }],
        service: {
          planId: { in: mappedPlanIds },
          status: { in: ["ACTIVE", "SUSPENDED"] },
          expiresAt: { gt: new Date() },
        },
      };
      const candidates = await db.pasarGuardBinding.findMany({
        where: { ...baseWhere, ...(excluded.length ? { id: { notIn: excluded } } : {}) },
        orderBy: { createdAt: "asc" },
        take: input.data.limit,
        select: { id: true },
      });
      const visibleUsers = await client.listUsers();
      const migratedIds: string[] = [];
      const failed: Array<{ bindingId: string; message: string }> = [];
      for (const candidate of candidates) {
        try {
          const result = await migratePasarGuardBindingToActiveProvider(candidate.id, { client, visibleUsers });
          migratedIds.push(candidate.id);
          if (result.remoteUser) {
            const index = visibleUsers.findIndex((user) => user.id === result.remoteUser!.id);
            if (index >= 0) visibleUsers[index] = result.remoteUser;
            else visibleUsers.push(result.remoteUser);
          }
        } catch (error) {
          failed.push({
            bindingId: candidate.id,
            message: error instanceof PasarGuardError ? error.message : "انتقال این سرویس انجام نشد",
          });
        }
      }
      const skippedNow = [...new Set([...excluded, ...candidates.map((candidate) => candidate.id)])];
      const remainingEligible = await db.pasarGuardBinding.count({
        where: { ...baseWhere, ...(skippedNow.length ? { id: { notIn: skippedNow } } : {}) },
      });
      await db.auditLog.create({
        data: {
          actorId: admin.sub,
          action: "pasarguard.migrateBatch",
          entityType: "PasarGuardProvider",
          entityId: client.providerId,
          after: {
            processed: candidates.length,
            migrated: migratedIds.length,
            failed: failed.length,
            remainingEligible,
          },
        },
      });
      return ok({
        processed: candidates.length,
        migratedCount: migratedIds.length,
        migratedIds,
        failed,
        remainingEligible,
        hasMoreEligible: remainingEligible > 0,
      });
    }

    if (input.data.action === "bind") {
      const client = await createPasarGuardClient();
      const binding = await bindPasarGuardUser(input.data.quickPingUserId, input.data.externalUserId, client);
      await db.auditLog.create({
        data: {
          actorId: admin.sub,
          action: "pasarguard.bind",
          entityType: "PasarGuardBinding",
          entityId: binding.id,
          after: {
            providerId: client.providerId,
            externalUserId: String(binding.externalUserId),
            externalUsername: binding.externalUsername,
            nodeCount: binding.nodes.length,
          },
        },
      });
      return ok(binding);
    }

    const binding = await syncPasarGuardBinding(input.data.bindingId);
    await db.auditLog.create({
      data: {
        actorId: admin.sub,
        action: "pasarguard.sync",
        entityType: "PasarGuardBinding",
        entityId: binding.id,
        after: {
          externalUserId: String(binding.externalUserId),
          externalUsername: binding.externalUsername,
          nodeCount: binding.nodes.length,
        },
      },
    });
    return ok(binding);
  } catch (error) {
    return integrationFailure(error);
  }
}
