import { createHash, randomUUID } from "node:crypto";
import type { NextRequest } from "next/server";
import { z } from "zod";
import { adminFromRequest, isSameOrigin } from "@/lib/admin-session";
import { fail, ok } from "@/lib/api";
import { hashPassword } from "@/lib/auth";
import { db } from "@/lib/db";
import { generateLicense, licenseQrPayload } from "@/lib/license";
import {
  generateManagedPassword,
  isPublicManagedEmail,
  managedIdentity,
} from "@/lib/managed-account";
import {
  PasarGuardError,
  type PasarGuardClient,
  type PasarGuardUser,
} from "@/lib/pasarguard/client";
import {
  createPasarGuardClient,
  discoverPasarGuardProfiles,
  isPasarGuardConfigured,
  savePasarGuardPlanMapping,
} from "@/lib/pasarguard/provider";
import { migratePasarGuardBindingToActiveProvider } from "@/lib/pasarguard/migration";
import { bindPasarGuardUser, syncPasarGuardBinding } from "@/lib/pasarguard/sync";

const createSchema = z.object({
  idempotencyKey: z.string().uuid(),
  customerName: z.string().trim().min(2).max(120),
  quotaGb: z.number().positive().max(100_000),
  days: z.number().int().min(1).max(3650),
  maxDevices: z.number().int().min(1).max(1000),
  profileKey: z.string().regex(/^(template|group):[1-9]\d*$/, "قالب یا گروه پاسارگارد معتبر نیست"),
  note: z.string().trim().max(500).default(""),
});

const patchSchema = z.discriminatedUnion("action", [
  z.object({
    action: z.literal("update"),
    serviceId: z.string().min(1),
    status: z.enum(["ACTIVE", "SUSPENDED"]),
    quotaGb: z.number().positive().max(100_000),
    daysFromNow: z.number().int().min(1).max(3650),
    maxDevices: z.number().int().min(1).max(1000),
    profileKey: z.string().regex(/^(template|group):[1-9]\d*$/, "قالب یا گروه پاسارگارد معتبر نیست"),
  }),
  z.object({
    action: z.literal("reset_credentials"),
    serviceId: z.string().min(1),
  }),
  z.object({
    action: z.literal("migrate_provider"),
    serviceId: z.string().min(1),
    profileKey: z.string().regex(/^(template|group):[1-9]\d*$/, "قالب یا گروه پاسارگارد معتبر نیست"),
  }),
]);

type ProviderProfile = {
  key: string;
  kind: "template" | "group";
  id: number;
  name: string;
  groupIds: number[];
  dataLimit: bigint | null;
  expireDurationSeconds: number | null;
};

function planName(profileKey: string, quotaBytes: bigint, days: number, maxDevices: number): string {
  const id = createHash("sha256")
    .update(`${profileKey}:${quotaBytes}:${days}:${maxDevices}`)
    .digest("hex")
    .slice(0, 18);
  return `NimHUB Managed ${id}`;
}

function sameGroups(left: number[], right: number[]): boolean {
  const a = [...new Set(left)].sort((first, second) => first - second);
  const b = [...new Set(right)].sort((first, second) => first - second);
  return a.length === b.length && a.every((value, index) => value === b[index]);
}

async function availableProfiles(client: PasarGuardClient): Promise<ProviderProfile[]> {
  return discoverPasarGuardProfiles(client);
}

async function nextUniqueLicense(): Promise<string> {
  for (let attempt = 0; attempt < 12; attempt += 1) {
    const license = generateLicense();
    const duplicate = await db.service.findUnique({ where: { license }, select: { id: true } });
    if (!duplicate) return license;
  }
  throw new Error("license_generation_failed");
}

async function nextManagedEmail(): Promise<string> {
  for (let attempt = 0; attempt < 12; attempt += 1) {
    const email = managedIdentity(randomUUID()).email;
    const duplicate = await db.user.findUnique({ where: { email }, select: { id: true } });
    if (!duplicate) return email;
  }
  throw new Error("credential_generation_failed");
}

function receipt(
  service: {
    id: string;
    name: string;
    license: string;
    quotaBytes: bigint;
    expiresAt: Date;
    maxDevices: number;
  },
  remote: { id: number; username: string },
  credentials: { email: string; initialPassword: string | null },
  reused = false,
) {
  return {
    reused,
    license: service.license,
    qrPayload: licenseQrPayload(service.license),
    credentials,
    service: {
      id: service.id,
      name: service.name,
      quotaBytes: service.quotaBytes,
      expiresAt: service.expiresAt,
      maxDevices: service.maxDevices,
    },
    remoteUser: { id: remote.id, username: remote.username },
  };
}

function providerFailure(error: unknown) {
  if (error instanceof PasarGuardError) {
    const status = error.code === "not_configured" ? 503 : error.code === "invalid_response" ? 422 : 502;
    return fail(status, `pasarguard_${error.code}`, error.message);
  }
  if (error instanceof Error && error.message === "license_generation_failed") {
    return fail(503, "license_generation_failed", "تولید کلید یکتای مجوز انجام نشد؛ دوباره تلاش کنید");
  }
  if (error instanceof Error && error.message === "credential_generation_failed") {
    return fail(503, "credential_generation_failed", "ساخت ایمیل یکتای کاربر انجام نشد؛ دوباره تلاش کنید");
  }
  return fail(500, "managed_license_failed", "عملیات کاربر و مجوز با خطای پیش‌بینی‌نشده متوقف شد");
}

export async function GET(request: NextRequest) {
  const admin = await adminFromRequest(request);
  if (!admin) return fail(401, "unauthorized", "ورود مدیر لازم است");
  if (!await isPasarGuardConfigured()) {
    return fail(503, "pasarguard_not_configured", "اتصال پاسارگارد در Secretهای سرور کامل نشده است");
  }
  try {
    const client = await createPasarGuardClient();
    const [profiles, remoteUsers, bindings] = await Promise.all([
      availableProfiles(client),
      client.listUsers(),
      db.pasarGuardBinding.findMany({
        orderBy: { createdAt: "desc" },
        include: {
          service: {
            include: {
              user: { select: { id: true, email: true, status: true, passwordHash: true, managedAccount: true } },
              plan: { select: { id: true, name: true } },
            },
          },
          nodes: { select: { id: true } },
          provider: { select: { id: true, name: true, active: true } },
        },
      }),
    ]);
    const remoteById = new Map(remoteUsers.map((user) => [user.id, user]));
    const licenses = bindings.map((binding) => {
      const onActiveProvider = binding.providerId === client.providerId;
      const remote = onActiveProvider ? remoteById.get(Number(binding.externalUserId)) : undefined;
      const profile = remote
        ? profiles.find((candidate) => sameGroups(candidate.groupIds, remote.groupIds))
        : undefined;
      return {
        id: binding.service.id,
        name: binding.service.name,
        email: binding.service.user.email,
        credentialsReady: binding.service.user.managedAccount && Boolean(binding.service.user.passwordHash),
        license: binding.service.license,
        status: binding.service.status,
        quotaBytes: remote?.dataLimit ?? binding.service.quotaBytes,
        usedBytes: remote?.usedTraffic ?? binding.service.usedBytes,
        expiresAt: remote?.expiresAt ?? binding.service.expiresAt,
        maxDevices: remote?.maxDevices ?? binding.service.maxDevices,
        profileKey: profile?.key ?? "",
        profileName: profile?.name ?? (remote?.groupIds.length ? `گروه ${remote.groupIds.join("، ")}` : "نامشخص"),
        remoteUsername: binding.externalUsername,
        serverCount: binding.nodes.length,
        lastSyncAt: binding.lastSyncAt,
        lastError: binding.lastError,
        providerId: binding.providerId,
        providerName: binding.provider?.name ?? "PasarGuard قبلی",
        needsMigration: !onActiveProvider,
      };
    });
    return ok({ profiles, licenses });
  } catch (error) {
    return providerFailure(error);
  }
}

export async function POST(request: NextRequest) {
  if (!isSameOrigin(request)) return fail(403, "invalid_origin", "مبدأ درخواست معتبر نیست");
  const admin = await adminFromRequest(request);
  if (!admin || admin.role !== "ADMIN") return fail(403, "forbidden", "فقط مدیر اصلی مجاز است");
  const input = createSchema.safeParse(await request.json().catch(() => null));
  if (!input.success) {
    return fail(400, "invalid_input", input.error.issues[0]?.message ?? "اطلاعات مجوز معتبر نیست");
  }
  if (!await isPasarGuardConfigured()) {
    return fail(503, "pasarguard_not_configured", "اتصال پاسارگارد در Secretهای سرور کامل نشده است");
  }

  const quotaBytes = BigInt(Math.round(input.data.quotaGb * 1024 ** 3));
  const expiresAt = new Date(Date.now() + input.data.days * 86_400_000);
  const identity = managedIdentity(input.data.idempotencyKey);
  const initialPassword = generateManagedPassword();
  const managedPlanName = planName(input.data.profileKey, quotaBytes, input.data.days, input.data.maxDevices);
  let client: PasarGuardClient | null = null;
  let remote: PasarGuardUser | null = null;
  let quickPingUserId: string | null = null;
  let createdRemote = false;
  let createdUser = false;
  let createdPlan = false;
  let previousUser: {
    passwordHash: string | null;
    emailVerifiedAt: Date | null;
    managedAccount: boolean;
    status: "ACTIVE" | "SUSPENDED" | "DELETED";
  } | null = null;

  try {
    client = await createPasarGuardClient();
    const [profiles, users] = await Promise.all([availableProfiles(client), client.listUsers()]);
    const profile = profiles.find((item) => item.key === input.data.profileKey);
    if (!profile || !profile.groupIds.length) {
      return fail(400, "template_unavailable", "قالب یا گروه انتخاب‌شده در پاسارگارد فعال نیست");
    }

    remote = users.find((user) => user.username.toLowerCase() === identity.remoteUsername) ?? null;
    if (remote) {
      const existingBinding = await db.pasarGuardBinding.findFirst({
        where: { providerId: client.providerId, externalUserId: BigInt(remote.id) },
        include: { service: { include: { user: { select: { email: true } } } } },
      });
      if (existingBinding) {
        return ok(receipt(
          existingBinding.service,
          remote,
          { email: existingBinding.service.user.email, initialPassword: null },
          true,
        ));
      }
    }

    const existingUser = await db.user.findUnique({
      where: { email: identity.email },
      select: {
        id: true,
        passwordHash: true,
        emailVerifiedAt: true,
        managedAccount: true,
        status: true,
      },
    });
    previousUser = existingUser;
    const passwordHash = await hashPassword(initialPassword);
    const user = await db.user.upsert({
      where: { email: identity.email },
      update: {
        passwordHash,
        emailVerifiedAt: new Date(),
        managedAccount: true,
        status: "ACTIVE",
      },
      create: {
        email: identity.email,
        passwordHash,
        emailVerifiedAt: new Date(),
        managedAccount: true,
        role: "CUSTOMER",
        status: "ACTIVE",
      },
      select: { id: true },
    });
    quickPingUserId = user.id;
    createdUser = !existingUser;

    const existingPlan = await db.plan.findUnique({ where: { name: managedPlanName }, select: { id: true } });
    const plan = await db.plan.upsert({
      where: { name: managedPlanName },
      update: {
        durationDays: input.data.days,
        dataLimitBytes: quotaBytes,
        maxDevices: input.data.maxDevices,
        isActive: true,
        isPublic: false,
      },
      create: {
        name: managedPlanName,
        interval: "CUSTOM",
        price: 0,
        durationDays: input.data.days,
        dataLimitBytes: quotaBytes,
        maxDevices: input.data.maxDevices,
        isActive: true,
        isPublic: false,
      },
      select: { id: true },
    });
    createdPlan = !existingPlan;
    await savePasarGuardPlanMapping(plan.id, profile.key);

    const providerNote = [`NimHUB: ${input.data.customerName}`, input.data.note]
      .filter(Boolean)
      .join(" — ");
    if (remote) {
      remote = await client.updateUser(remote.username, {
        dataLimit: quotaBytes,
        expiresAt,
        maxDevices: input.data.maxDevices,
        status: "active",
        groupIds: profile.groupIds,
        note: providerNote,
      });
    } else {
      remote = await client.createUser(
        identity.remoteUsername,
        quotaBytes,
        profile.groupIds,
        providerNote,
        input.data.maxDevices,
        expiresAt,
      );
      createdRemote = true;
    }

    const license = await nextUniqueLicense();
    const binding = await bindPasarGuardUser(user.id, remote.id, client, {
      planId: plan.id,
      serviceName: input.data.customerName,
      license,
      allowAdditionalBinding: true,
      expectedQuotaBytes: quotaBytes,
    });
    await db.auditLog.create({
      data: {
        actorId: admin.sub,
        action: "managed_license.create",
        entityType: "Service",
        entityId: binding.service.id,
        after: {
          pasarGuardUserId: remote.id,
          pasarGuardUsername: remote.username,
          accountEmail: identity.email,
          providerProfile: input.data.profileKey,
          quotaBytes: quotaBytes.toString(),
          durationDays: input.data.days,
          maxDevices: input.data.maxDevices,
        },
      },
    });
    return ok(receipt(
      binding.service,
      remote,
      { email: identity.email, initialPassword },
    ), { status: 201 });
  } catch (error) {
    if (remote) {
      const partial = await db.pasarGuardBinding.findFirst({
        where: { providerId: client?.providerId ?? null, externalUserId: BigInt(remote.id) },
        include: { service: { select: { id: true, userId: true } } },
      }).catch(() => null);
      if (partial?.service.userId === quickPingUserId) {
        await db.service.delete({ where: { id: partial.service.id } }).catch(() => undefined);
      }
      if (createdRemote && client) await client.deleteUser(remote.username).catch(() => undefined);
    }
    if (createdUser && quickPingUserId) {
      const serviceCount = await db.service.count({ where: { userId: quickPingUserId } }).catch(() => 1);
      if (serviceCount === 0) await db.user.delete({ where: { id: quickPingUserId } }).catch(() => undefined);
    } else if (previousUser && quickPingUserId) {
      await db.user.update({
        where: { id: quickPingUserId },
        data: previousUser,
      }).catch(() => undefined);
    }
    if (createdPlan) {
      const plan = await db.plan.findUnique({ where: { name: managedPlanName }, select: { id: true } }).catch(() => null);
      if (plan) {
        const serviceCount = await db.service.count({ where: { planId: plan.id } }).catch(() => 1);
        if (serviceCount === 0) await db.plan.delete({ where: { id: plan.id } }).catch(() => undefined);
      }
    }
    return providerFailure(error);
  }
}

export async function PATCH(request: NextRequest) {
  if (!isSameOrigin(request)) return fail(403, "invalid_origin", "مبدأ درخواست معتبر نیست");
  const admin = await adminFromRequest(request);
  if (!admin || admin.role !== "ADMIN") return fail(403, "forbidden", "فقط مدیر اصلی مجاز است");
  const input = patchSchema.safeParse(await request.json().catch(() => null));
  if (!input.success) {
    return fail(400, "invalid_input", input.error.issues[0]?.message ?? "تغییرات مجوز معتبر نیست");
  }

  const before = await db.service.findUnique({
    where: { id: input.data.serviceId },
    include: {
      user: {
        select: {
          id: true,
          email: true,
          status: true,
          passwordHash: true,
          managedAccount: true,
        },
      },
      pasarGuardBinding: true,
    },
  });
  if (!before?.pasarGuardBinding) {
    return fail(404, "managed_license_not_found", "مجوز مدیریت‌شده پیدا نشد");
  }

  if (input.data.action === "reset_credentials") {
    try {
      const email = isPublicManagedEmail(before.user.email) ? before.user.email : await nextManagedEmail();
      const initialPassword = generateManagedPassword();
      const passwordHash = await hashPassword(initialPassword);
      const now = new Date();
      await db.$transaction(async (transaction) => {
        await transaction.user.update({
          where: { id: before.user.id },
          data: {
            email,
            passwordHash,
            managedAccount: true,
            emailVerifiedAt: now,
          },
        });
        await transaction.refreshToken.updateMany({
          where: { userId: before.user.id, revokedAt: null },
          data: { revokedAt: now },
        });
      });
      await db.auditLog.create({
        data: {
          actorId: admin.sub,
          action: "managed_license.resetCredentials",
          entityType: "User",
          entityId: before.user.id,
          before: { email: before.user.email, hadPassword: Boolean(before.user.passwordHash) },
          after: { email, hadPassword: true },
        },
      });
      return ok({ credentials: { email, initialPassword } });
    } catch (error) {
      return providerFailure(error);
    }
  }

  if (!await isPasarGuardConfigured()) {
    return fail(503, "pasarguard_not_configured", "اتصال پاسارگارد در Secretهای سرور کامل نشده است");
  }

  if (input.data.action === "migrate_provider") {
    try {
      const result = await migratePasarGuardBindingToActiveProvider(before.pasarGuardBinding.id, {
        profileKey: input.data.profileKey,
      });
      await db.auditLog.create({
        data: {
          actorId: admin.sub,
          action: "managed_license.migrateProvider",
          entityType: "Service",
          entityId: before.id,
          before: {
            providerId: result.previousProviderId,
            usedBytes: before.usedBytes.toString(),
            quotaBytes: before.quotaBytes.toString(),
          },
          after: {
            providerId: result.providerId,
            externalUserId: String(result.externalUserId),
            providerProfile: result.profileKey,
            usageOffsetBytes: result.usageOffsetBytes.toString(),
          },
        },
      });
      return ok({
        migrated: result.migrated,
        alreadyActiveProvider: result.alreadyActiveProvider,
        remainingBytes: "remainingBytes" in result && result.remainingBytes !== undefined
          ? result.remainingBytes.toString()
          : undefined,
      });
    } catch (error) {
      return providerFailure(error);
    }
  }

  const update = input.data;
  const quotaBytes = BigInt(Math.round(update.quotaGb * 1024 ** 3));
  const expiresAt = new Date(Date.now() + update.daysFromNow * 86_400_000);
  const managedPlanName = planName(
    update.profileKey,
    quotaBytes,
    update.daysFromNow,
    update.maxDevices,
  );
  const client = await createPasarGuardClient();
  let remoteBefore: PasarGuardUser | null = null;
  try {
    const currentBinding = await db.pasarGuardBinding.findUnique({
      where: { id: before.pasarGuardBinding.id },
      select: { providerId: true, usageOffsetBytes: true },
    });
    if (!currentBinding || currentBinding.providerId !== client.providerId) {
      return fail(409, "provider_migration_required", "این سرویس به پنل قبلی متصل است؛ ابتدا آن را به پنل فعال منتقل کنید");
    }
    const profiles = await availableProfiles(client);
    const profile = profiles.find((item) => item.key === update.profileKey);
    if (!profile || !profile.groupIds.length) {
      return fail(400, "template_unavailable", "قالب یا گروه انتخاب‌شده در پاسارگارد فعال نیست");
    }
    remoteBefore = await client.getUser(Number(before.pasarGuardBinding.externalUserId));
    const effectiveUsedBytes = currentBinding.usageOffsetBytes + remoteBefore.usedTraffic;
    if (quotaBytes < effectiveUsedBytes) {
      return fail(400, "quota_below_usage", "حجم کل نمی‌تواند کمتر از مصرف فعلی کاربر باشد");
    }
    const remoteQuotaBytes = quotaBytes - currentBinding.usageOffsetBytes;
    if (remoteQuotaBytes <= 0n) {
      return fail(400, "quota_below_usage", "حجم جدید برای سابقه مصرف این کاربر کافی نیست");
    }
    await client.updateUser(before.pasarGuardBinding.externalUsername, {
      dataLimit: remoteQuotaBytes,
      expiresAt,
      maxDevices: update.maxDevices,
      status: update.status === "ACTIVE" ? "active" : "disabled",
      groupIds: profile.groupIds,
    });
    await syncPasarGuardBinding(before.pasarGuardBinding.id, client);

    const now = new Date();
    const updatedPlanId = await db.$transaction(async (transaction) => {
      const plan = await transaction.plan.upsert({
        where: { name: managedPlanName },
        update: {
          durationDays: update.daysFromNow,
          dataLimitBytes: quotaBytes,
          maxDevices: update.maxDevices,
          isActive: true,
          isPublic: false,
        },
        create: {
          name: managedPlanName,
          interval: "CUSTOM",
          price: 0,
          durationDays: update.daysFromNow,
          dataLimitBytes: quotaBytes,
          maxDevices: update.maxDevices,
          isActive: true,
          isPublic: false,
        },
      });
      await transaction.service.update({
        where: { id: before.id },
        data: { planId: plan.id, status: update.status },
      });
      await transaction.user.update({
        where: { id: before.user.id },
        data: { status: update.status === "ACTIVE" ? "ACTIVE" : "SUSPENDED" },
      });
      if (update.status !== "ACTIVE" || update.maxDevices !== before.maxDevices) {
        await Promise.all([
          transaction.refreshToken.updateMany({
            where: { userId: before.user.id, revokedAt: null },
            data: { revokedAt: now },
          }),
          transaction.device.updateMany({
            where: { userId: before.user.id, revokedAt: null },
            data: { revokedAt: now },
          }),
        ]);
      }
      return plan.id;
    });
    await savePasarGuardPlanMapping(updatedPlanId, profile.key);
    await db.auditLog.create({
      data: {
        actorId: admin.sub,
        action: "managed_license.update",
        entityType: "Service",
        entityId: before.id,
        before: {
          status: before.status,
          quotaBytes: before.quotaBytes.toString(),
          expiresAt: before.expiresAt.toISOString(),
          maxDevices: before.maxDevices,
        },
        after: {
          status: update.status,
          quotaBytes: quotaBytes.toString(),
          expiresAt: expiresAt.toISOString(),
          maxDevices: update.maxDevices,
          providerProfile: update.profileKey,
        },
      },
    });
    return ok({ updated: true });
  } catch (error) {
    if (remoteBefore) {
      await client.updateUser(before.pasarGuardBinding.externalUsername, {
        dataLimit: remoteBefore.dataLimit ?? before.quotaBytes,
        expiresAt: remoteBefore.expiresAt,
        maxDevices: remoteBefore.maxDevices ?? before.maxDevices,
        status: remoteBefore.status,
        ...(remoteBefore.groupIds.length ? { groupIds: remoteBefore.groupIds } : {}),
      }).then(() => syncPasarGuardBinding(before.pasarGuardBinding!.id, client)).catch(() => undefined);
    }
    return providerFailure(error);
  }
}
