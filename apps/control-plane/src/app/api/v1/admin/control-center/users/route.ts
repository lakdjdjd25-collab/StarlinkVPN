import { randomUUID } from "node:crypto";
import type { NextRequest } from "next/server";
import { z } from "zod";
import type { Prisma } from "@/generated/prisma/client";
import { adminFromRequest, isSameOrigin } from "@/lib/admin-session";
import { fail, ok } from "@/lib/api";
import { hashPassword } from "@/lib/auth";
import { db } from "@/lib/db";
import { generateManagedPassword, isPublicManagedEmail, managedIdentity } from "@/lib/managed-account";
import { PasarGuardError } from "@/lib/pasarguard/client";
import { createPasarGuardClientForProvider } from "@/lib/pasarguard/provider";
import { syncPasarGuardBinding } from "@/lib/pasarguard/sync";
import { effectiveUsedBytes, remainingServiceBytes } from "@/lib/server-access";

const actionSchema = z.discriminatedUnion("action", [
  z.object({ action: z.literal("add_traffic"), serviceId: z.string().min(1), gb: z.number().positive().max(100_000) }),
  z.object({ action: z.literal("extend"), serviceId: z.string().min(1), days: z.number().int().positive().max(3650) }),
  z.object({ action: z.literal("set_vip"), serviceId: z.string().min(1), enabled: z.boolean() }),
  z.object({ action: z.literal("set_service_status"), serviceId: z.string().min(1), status: z.enum(["ACTIVE", "SUSPENDED"]) }),
  z.object({ action: z.literal("set_device_limit"), serviceId: z.string().min(1), maxDevices: z.number().int().min(1).max(1000) }),
  z.object({ action: z.literal("reset_credentials"), userId: z.string().min(1) }),
  z.object({ action: z.literal("revoke_device"), userId: z.string().min(1), deviceId: z.string().min(1) }),
  z.object({ action: z.literal("revoke_all_devices"), userId: z.string().min(1) }),
]);

type ProviderState = "LOCAL" | "MIGRATION_REQUIRED" | "OFFLINE" | "STALE" | "SYNCED";

type SummaryService = {
  status: string;
  expiresAt: Date;
  quotaBytes: bigint;
  usedBytes: bigint;
  providerState: ProviderState;
};

function providerFailure(error: unknown) {
  if (error instanceof PasarGuardError) {
    return fail(error.code === "not_configured" ? 503 : 502, `pasarguard_${error.code}`, "اتصال به پنل VPN برقرار نشد", {
      technical: error.message,
      retryable: true,
    });
  }
  return fail(500, "admin_user_action_failed", "عملیات مدیریت کاربر انجام نشد", {
    technical: error instanceof Error ? error.message : "unknown_error",
    retryable: true,
  });
}

async function nextManagedEmail(): Promise<string> {
  for (let attempt = 0; attempt < 12; attempt += 1) {
    const email = managedIdentity(randomUUID()).email;
    const duplicate = await db.user.findUnique({ where: { email }, select: { id: true } });
    if (!duplicate) return email;
  }
  throw new Error("credential_generation_failed");
}

async function loadService(serviceId: string) {
  return db.service.findUnique({
    where: { id: serviceId },
    include: {
      user: { select: { id: true, email: true, status: true, passwordHash: true, managedAccount: true } },
      pasarGuardBinding: {
        select: {
          id: true,
          providerId: true,
          externalUserId: true,
          externalUsername: true,
          usageOffsetBytes: true,
        },
      },
    },
  });
}

async function providerContext(service: NonNullable<Awaited<ReturnType<typeof loadService>>>) {
  if (!service.pasarGuardBinding) return null;
  const client = await createPasarGuardClientForProvider(service.pasarGuardBinding.providerId);
  const remoteId = Number(service.pasarGuardBinding.externalUserId);
  if (!Number.isSafeInteger(remoteId)) {
    throw new PasarGuardError("invalid_response", "شناسه کاربر Provider معتبر نیست");
  }
  return { client, remote: await client.getUser(remoteId) };
}

function providerState(
  binding: { providerId: string | null; lastSyncAt: Date | null; lastError: string | null } | null,
  activeProviderId: string | null,
  now: Date,
): ProviderState {
  if (!binding) return "LOCAL";
  if (!binding.providerId || !activeProviderId || binding.providerId !== activeProviderId) return "MIGRATION_REQUIRED";
  if (binding.lastError) return "OFFLINE";
  if (!binding.lastSyncAt || now.getTime() - binding.lastSyncAt.getTime() > 15 * 60_000) return "STALE";
  return "SYNCED";
}

function warningFor(service: SummaryService | null, accountStatus: string, now: Date) {
  if (accountStatus !== "ACTIVE") return { code: "ACCOUNT_SUSPENDED", label: "حساب معلق", tone: "danger" as const };
  if (!service) return { code: "NO_SUBSCRIPTION", label: "بدون اشتراک", tone: "warning" as const };
  if (service.status !== "ACTIVE") return { code: "SERVICE_SUSPENDED", label: "اشتراک متوقف", tone: "danger" as const };
  if (service.expiresAt.getTime() <= now.getTime()) return { code: "EXPIRED", label: "منقضی", tone: "danger" as const };
  if (service.usedBytes >= service.quotaBytes) return { code: "QUOTA_EXHAUSTED", label: "حجم تمام شده", tone: "danger" as const };
  if (service.providerState === "MIGRATION_REQUIRED") return { code: "MIGRATION_REQUIRED", label: "نیازمند انتقال", tone: "warning" as const };
  if (service.providerState === "OFFLINE") return { code: "PROVIDER_ERROR", label: "خطای Provider", tone: "warning" as const };
  if (service.providerState === "STALE") return { code: "PROVIDER_STALE", label: "Sync قدیمی", tone: "warning" as const };
  return null;
}

function buildUserWhere(request: NextRequest, now: Date): Prisma.UserWhereInput {
  const params = request.nextUrl.searchParams;
  const query = params.get("q")?.trim() ?? "";
  const accountStatus = params.get("status") ?? "ALL";
  const vip = params.get("vip") ?? "ALL";
  const expiring = params.get("expiring") === "1";
  const attention = params.get("attention") === "1";
  const sevenDaysAhead = new Date(now.getTime() + 7 * 86_400_000);
  const clauses: Prisma.UserWhereInput[] = [];

  if (query) {
    clauses.push({
      OR: [
        { email: { contains: query, mode: "insensitive" } },
        { services: { some: { name: { contains: query, mode: "insensitive" } } } },
        { services: { some: { license: { contains: query, mode: "insensitive" } } } },
        { services: { some: { pasarGuardBinding: { externalUsername: { contains: query, mode: "insensitive" } } } } },
      ],
    });
  }
  if (vip === "VIP") clauses.push({ services: { some: { vipAccess: true } } });
  if (vip === "STANDARD") clauses.push({ services: { some: {} } }, { services: { none: { vipAccess: true } } });
  if (expiring) clauses.push({ services: { some: { status: "ACTIVE", expiresAt: { gt: now, lte: sevenDaysAhead } } } });
  if (attention) {
    clauses.push({
      OR: [
        { status: "SUSPENDED" },
        { services: { some: { status: { not: "ACTIVE" } } } },
        { services: { some: { expiresAt: { lte: now } } } },
        { services: { some: { pasarGuardBinding: { lastError: { not: null } } } } },
      ],
    });
  }

  return {
    role: "CUSTOMER",
    ...(accountStatus === "ACTIVE" ? { status: "ACTIVE" as const } : {}),
    ...(accountStatus === "SUSPENDED" ? { status: "SUSPENDED" as const } : {}),
    ...(clauses.length ? { AND: clauses } : {}),
  };
}

export async function GET(request: NextRequest) {
  const admin = await adminFromRequest(request);
  if (!admin) return fail(401, "unauthorized", "ورود مدیر لازم است");

  const params = request.nextUrl.searchParams;
  const page = Math.max(1, Number(params.get("page") || "1") || 1);
  const pageSize = Math.min(100, Math.max(10, Number(params.get("pageSize") || "25") || 25));
  const now = new Date();
  const where = buildUserWhere(request, now);

  const [total, users, activeProvider] = await Promise.all([
    db.user.count({ where }),
    db.user.findMany({
      where,
      orderBy: { createdAt: "desc" },
      skip: (page - 1) * pageSize,
      take: pageSize,
      include: {
        devices: { orderBy: { lastSeenAt: "desc" } },
        services: {
          orderBy: { createdAt: "desc" },
          include: {
            plan: {
              select: {
                id: true,
                name: true,
                pasarGuardMappings: {
                  where: { valid: true },
                  select: { providerId: true, profileKey: true, profileName: true, updatedAt: true },
                },
              },
            },
            pasarGuardBinding: {
              select: {
                id: true,
                providerId: true,
                externalUsername: true,
                lastSyncAt: true,
                lastError: true,
                provider: { select: { id: true, name: true, active: true } },
              },
            },
            _count: { select: { nodes: true } },
          },
        },
      },
    }),
    db.pasarGuardProvider.findFirst({
      where: { active: true },
      orderBy: { updatedAt: "desc" },
      select: { id: true, name: true },
    }),
  ]);

  const entityIds = users.flatMap((user) => [user.id, ...user.services.map((service) => service.id)]);
  const recentActivity = entityIds.length
    ? await db.auditLog.findMany({
        where: { entityId: { in: entityIds } },
        orderBy: { createdAt: "desc" },
        take: Math.min(250, Math.max(50, users.length * 8)),
        include: { actor: { select: { email: true } } },
      })
    : [];

  const items = users.map((user) => {
    const services = user.services.map((service) => {
      const state = providerState(service.pasarGuardBinding, activeProvider?.id ?? null, now);
      const mapping = service.plan.pasarGuardMappings.find((item) => item.providerId === service.pasarGuardBinding?.providerId)
        ?? service.plan.pasarGuardMappings[0];
      return {
        id: service.id,
        name: service.name,
        license: service.license,
        status: service.status,
        quotaBytes: service.quotaBytes,
        usedBytes: effectiveUsedBytes(service),
        remainingBytes: remainingServiceBytes(service),
        expiresAt: service.expiresAt,
        maxDevices: service.maxDevices,
        vipAccess: service.vipAccess,
        serverGroup: mapping?.profileName ?? service.plan.name,
        serverGroupKey: mapping?.profileKey ?? null,
        serverCount: service._count.nodes,
        providerState: state,
        providerName: service.pasarGuardBinding?.provider?.name ?? null,
        remoteUsername: service.pasarGuardBinding?.externalUsername ?? null,
        lastSyncAt: service.pasarGuardBinding?.lastSyncAt ?? null,
        providerError: service.pasarGuardBinding?.lastError ?? null,
      };
    });
    const primary = services.find((service) => service.status === "ACTIVE" && service.expiresAt.getTime() > now.getTime())
      ?? services[0]
      ?? null;
    const activeDevices = user.devices.filter((device) => !device.revokedAt).length;
    const related = new Set([user.id, ...services.map((service) => service.id)]);
    return {
      id: user.id,
      name: primary?.name ?? user.email.split("@")[0],
      email: user.email,
      accountStatus: user.status,
      managedAccount: user.managedAccount,
      createdAt: user.createdAt,
      serviceCount: services.length,
      activeDevices,
      primaryServiceId: primary?.id ?? null,
      primaryService: primary,
      services,
      devices: user.devices.map((device) => ({
        id: device.id,
        name: device.name,
        platform: device.platform,
        appVersion: device.appVersion,
        lastSeenAt: device.lastSeenAt,
        revokedAt: device.revokedAt,
        status: device.revokedAt ? "REVOKED" : "ACTIVE",
      })),
      warning: warningFor(primary, user.status, now),
      activity: recentActivity
        .filter((item) => item.entityId && related.has(item.entityId))
        .slice(0, 12)
        .map((item) => ({
          id: item.id,
          action: item.action,
          entityType: item.entityType,
          createdAt: item.createdAt,
          actor: item.actor?.email ?? "System",
        })),
    };
  });

  return ok({
    items,
    pagination: { page, pageSize, total, pages: Math.max(1, Math.ceil(total / pageSize)) },
    provider: activeProvider,
  });
}

export async function PATCH(request: NextRequest) {
  if (!isSameOrigin(request)) return fail(403, "invalid_origin", "مبدأ درخواست معتبر نیست");
  const admin = await adminFromRequest(request);
  if (!admin || admin.role !== "ADMIN") return fail(403, "forbidden", "فقط مدیر اصلی مجاز است");
  const input = actionSchema.safeParse(await request.json().catch(() => null));
  if (!input.success) return fail(400, "invalid_input", input.error.issues[0]?.message ?? "درخواست مدیریت کاربر معتبر نیست");
  const action = input.data;
  const now = new Date();

  if (action.action === "reset_credentials") {
    const user = await db.user.findUnique({
      where: { id: action.userId },
      select: { id: true, email: true, passwordHash: true },
    });
    if (!user) return fail(404, "user_not_found", "کاربر پیدا نشد");
    try {
      const email = isPublicManagedEmail(user.email) ? user.email : await nextManagedEmail();
      const initialPassword = generateManagedPassword();
      const passwordHash = await hashPassword(initialPassword);
      await db.$transaction(async (tx) => {
        await tx.user.update({
          where: { id: user.id },
          data: { email, passwordHash, managedAccount: true, emailVerifiedAt: now },
        });
        await tx.refreshToken.updateMany({ where: { userId: user.id, revokedAt: null }, data: { revokedAt: now } });
        await tx.auditLog.create({
          data: {
            actorId: admin.sub,
            action: "managed_license.resetCredentials",
            entityType: "User",
            entityId: user.id,
            before: { email: user.email, hadPassword: Boolean(user.passwordHash) },
            after: { email, hadPassword: true },
          },
        });
      });
      return ok({ credentials: { email, initialPassword } });
    } catch (error) {
      return providerFailure(error);
    }
  }

  if (action.action === "revoke_device" || action.action === "revoke_all_devices") {
    const user = await db.user.findUnique({ where: { id: action.userId }, select: { id: true } });
    if (!user) return fail(404, "user_not_found", "کاربر پیدا نشد");
    if (action.action === "revoke_device") {
      const device = await db.device.findFirst({
        where: { id: action.deviceId, userId: user.id },
        select: { id: true, revokedAt: true },
      });
      if (!device) return fail(404, "device_not_found", "دستگاه پیدا نشد");
      await db.$transaction(async (tx) => {
        await tx.device.update({ where: { id: device.id }, data: { revokedAt: now } });
        await tx.refreshToken.updateMany({ where: { deviceId: device.id, revokedAt: null }, data: { revokedAt: now } });
        await tx.auditLog.create({
          data: { actorId: admin.sub, action: "device.revoke", entityType: "Device", entityId: device.id, before: { revokedAt: device.revokedAt }, after: { revokedAt: now, userId: user.id } },
        });
      });
      return ok({ revoked: 1 });
    }
    const activeDevices = await db.device.count({ where: { userId: user.id, revokedAt: null } });
    await db.$transaction(async (tx) => {
      await tx.device.updateMany({ where: { userId: user.id, revokedAt: null }, data: { revokedAt: now } });
      await tx.refreshToken.updateMany({ where: { userId: user.id, revokedAt: null }, data: { revokedAt: now } });
      await tx.auditLog.create({
        data: { actorId: admin.sub, action: "device.revokeAll", entityType: "User", entityId: user.id, before: { activeDevices }, after: { activeDevices: 0 } },
      });
    });
    return ok({ revoked: activeDevices });
  }

  const service = await loadService(action.serviceId);
  if (!service) return fail(404, "service_not_found", "اشتراک پیدا نشد");

  if (action.action === "set_vip") {
    if (service.vipAccess === action.enabled) return ok({ id: service.id, vipAccess: service.vipAccess, changed: false });
    const value = await db.$transaction(async (tx) => {
      const updated = await tx.service.update({
        where: { id: service.id },
        data: { vipAccess: action.enabled },
        select: { id: true, vipAccess: true },
      });
      await tx.auditLog.create({
        data: { actorId: admin.sub, action: "service.vipAccess", entityType: "Service", entityId: service.id, before: { vipAccess: service.vipAccess }, after: { vipAccess: action.enabled } },
      });
      return updated;
    });
    return ok({ ...value, changed: true });
  }

  try {
    const provider = await providerContext(service);

    if (action.action === "add_traffic") {
      const addedBytes = BigInt(Math.round(action.gb * 1024 ** 3));
      const quotaBytes = service.quotaBytes + addedBytes;
      if (provider && service.pasarGuardBinding) {
        const remoteQuota = quotaBytes - service.pasarGuardBinding.usageOffsetBytes;
        if (remoteQuota <= 0n) return fail(400, "invalid_quota", "حجم جدید برای سابقه مصرف این کاربر کافی نیست");
        await provider.client.updateUser(service.pasarGuardBinding.externalUsername, { dataLimit: remoteQuota });
        await syncPasarGuardBinding(service.pasarGuardBinding.id, provider.client);
      } else {
        await db.service.update({ where: { id: service.id }, data: { quotaBytes } });
      }
      await db.auditLog.create({
        data: { actorId: admin.sub, action: "service.addTraffic", entityType: "Service", entityId: service.id, before: { quotaBytes: service.quotaBytes.toString() }, after: { quotaBytes: quotaBytes.toString(), addedBytes: addedBytes.toString() } },
      });
      return ok({ updated: true, quotaBytes });
    }

    if (action.action === "extend") {
      const base = service.expiresAt.getTime() > now.getTime() ? service.expiresAt : now;
      const expiresAt = new Date(base.getTime() + action.days * 86_400_000);
      if (provider && service.pasarGuardBinding) {
        await provider.client.updateUser(service.pasarGuardBinding.externalUsername, { expiresAt });
        await syncPasarGuardBinding(service.pasarGuardBinding.id, provider.client);
      } else {
        await db.service.update({ where: { id: service.id }, data: { expiresAt } });
      }
      await db.auditLog.create({
        data: { actorId: admin.sub, action: "service.extend", entityType: "Service", entityId: service.id, before: { expiresAt: service.expiresAt.toISOString() }, after: { expiresAt: expiresAt.toISOString(), addedDays: action.days } },
      });
      return ok({ updated: true, expiresAt });
    }

    if (action.action === "set_service_status") {
      if (action.status === "ACTIVE" && service.expiresAt.getTime() <= now.getTime()) {
        return fail(409, "service_expired", "برای فعال‌سازی ابتدا اشتراک را تمدید کنید");
      }
      if (provider && service.pasarGuardBinding) {
        await provider.client.updateUser(service.pasarGuardBinding.externalUsername, { status: action.status === "ACTIVE" ? "active" : "disabled" });
        await syncPasarGuardBinding(service.pasarGuardBinding.id, provider.client);
      } else {
        await db.service.update({ where: { id: service.id }, data: { status: action.status } });
      }
      await db.auditLog.create({
        data: { actorId: admin.sub, action: action.status === "ACTIVE" ? "service.reactivate" : "service.suspend", entityType: "Service", entityId: service.id, before: { status: service.status, accountStatus: service.user.status }, after: { status: action.status, accountStatus: service.user.status } },
      });
      return ok({ updated: true, status: action.status, accountStatus: service.user.status });
    }

    if (action.action === "set_device_limit") {
      if (provider && service.pasarGuardBinding) {
        await provider.client.updateUser(service.pasarGuardBinding.externalUsername, { maxDevices: action.maxDevices });
        await syncPasarGuardBinding(service.pasarGuardBinding.id, provider.client);
      } else {
        await db.service.update({ where: { id: service.id }, data: { maxDevices: action.maxDevices } });
      }
      await db.auditLog.create({
        data: { actorId: admin.sub, action: "service.deviceLimit", entityType: "Service", entityId: service.id, before: { maxDevices: service.maxDevices }, after: { maxDevices: action.maxDevices, sessionsRevoked: false } },
      });
      return ok({ updated: true, maxDevices: action.maxDevices, sessionsRevoked: false });
    }

    return fail(400, "unsupported_action", "عملیات پشتیبانی نمی‌شود");
  } catch (error) {
    return providerFailure(error);
  }
}
