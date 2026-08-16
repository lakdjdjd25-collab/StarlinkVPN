import type { NextRequest } from "next/server";
import { z } from "zod";
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
