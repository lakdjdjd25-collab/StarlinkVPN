import { createHash } from "node:crypto";
import type { NextRequest } from "next/server";
import { z } from "zod";
import { adminFromRequest, isSameOrigin } from "@/lib/admin-session";
import { fail, ok } from "@/lib/api";
import { db } from "@/lib/db";
import { generateLicense, licenseQrPayload } from "@/lib/license";
import {
  createPasarGuardClient,
  isPasarGuardConfigured,
  PasarGuardError,
  type PasarGuardClient,
  type PasarGuardUser,
} from "@/lib/pasarguard/client";
import { bindPasarGuardUser } from "@/lib/pasarguard/sync";

const createSchema = z.object({
  remoteUsername: z.string().trim().toLowerCase().min(3).max(64)
    .regex(/^[a-z0-9][a-z0-9_.-]*$/, "نام کاربری فقط می‌تواند شامل حروف انگلیسی، عدد، نقطه، خط تیره و زیرخط باشد"),
  customerName: z.string().trim().min(2).max(120),
  quotaGb: z.number().positive().max(100_000),
  days: z.number().int().min(1).max(3650),
  maxDevices: z.number().int().min(1).max(1000),
  profileKey: z.string().regex(/^(template|group):[1-9]\d*$/, "قالب یا گروه پاسارگارد معتبر نیست"),
  note: z.string().trim().max(500).default(""),
});

type ProviderProfile = {
  key: string;
  kind: "template" | "group";
  id: number;
  name: string;
  groupIds: number[];
  dataLimit: bigint | null;
  expireDurationSeconds: number | null;
};

function managedEmail(username: string): string {
  const id = createHash("sha256").update(username).digest("hex").slice(0, 32);
  return `pg-${id}@license.nimhub.local`;
}

function planName(profileKey: string, quotaBytes: bigint, days: number, maxDevices: number): string {
  const id = createHash("sha256")
    .update(`${profileKey}:${quotaBytes}:${days}:${maxDevices}`)
    .digest("hex")
    .slice(0, 18);
  return `NimHUB Managed ${id}`;
}

async function availableProfiles(client: PasarGuardClient): Promise<ProviderProfile[]> {
  const [templatesResult, groupsResult] = await Promise.allSettled([
    client.listUserTemplates(),
    client.listGroups(),
  ]);
  const profiles: ProviderProfile[] = [];

  if (templatesResult.status === "fulfilled") {
    profiles.push(...templatesResult.value
      .filter((template) => !template.isDisabled && template.status === "active" && template.groupIds.length > 0)
      .map((template) => ({
        key: `template:${template.id}`,
        kind: "template" as const,
        id: template.id,
        name: template.name,
        groupIds: template.groupIds,
        dataLimit: template.dataLimit,
        expireDurationSeconds: template.expireDurationSeconds,
      })));
  }
  if (groupsResult.status === "fulfilled") {
    profiles.push(...groupsResult.value.map((group) => ({
      key: `group:${group.id}`,
      kind: "group" as const,
      id: group.id,
      name: group.name,
      groupIds: [group.id],
      dataLimit: null,
      expireDurationSeconds: null,
    })));
  }
  if (templatesResult.status === "rejected" && groupsResult.status === "rejected") {
    throw groupsResult.reason ?? templatesResult.reason;
  }

  return profiles;
}

async function nextUniqueLicense(): Promise<string> {
  for (let attempt = 0; attempt < 12; attempt += 1) {
    const license = generateLicense();
    const duplicate = await db.service.findUnique({ where: { license }, select: { id: true } });
    if (!duplicate) return license;
  }
  throw new Error("license_generation_failed");
}

function receipt(service: {
  id: string;
  name: string;
  license: string;
  quotaBytes: bigint;
  expiresAt: Date;
  maxDevices: number;
}, remote: { id: number; username: string }, reused = false) {
  return {
    reused,
    license: service.license,
    qrPayload: licenseQrPayload(service.license),
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
  return fail(500, "managed_license_failed", "ساخت کاربر و مجوز با خطای پیش‌بینی‌نشده متوقف شد");
}

export async function GET(request: NextRequest) {
  const admin = await adminFromRequest(request);
  if (!admin) return fail(401, "unauthorized", "ورود مدیر لازم است");
  if (!isPasarGuardConfigured()) {
    return fail(503, "pasarguard_not_configured", "اتصال پاسارگارد در Secretهای سرور کامل نشده است");
  }
  try {
    const profiles = await availableProfiles(createPasarGuardClient());
    return ok({ profiles });
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
  if (!isPasarGuardConfigured()) {
    return fail(503, "pasarguard_not_configured", "اتصال پاسارگارد در Secretهای سرور کامل نشده است");
  }

  const quotaBytes = BigInt(Math.round(input.data.quotaGb * 1024 ** 3));
  const expiresAt = new Date(Date.now() + input.data.days * 86_400_000);
  const internalEmail = managedEmail(input.data.remoteUsername);
  const managedPlanName = planName(input.data.profileKey, quotaBytes, input.data.days, input.data.maxDevices);
  let client: PasarGuardClient | null = null;
  let remote: PasarGuardUser | null = null;
  let quickPingUserId: string | null = null;
  let createdRemote = false;
  let createdUser = false;
  let createdPlan = false;

  try {
    client = createPasarGuardClient();
    const [profiles, users] = await Promise.all([availableProfiles(client), client.listUsers()]);
    const profile = profiles.find((item) => item.key === input.data.profileKey);
    if (!profile || !profile.groupIds.length) {
      return fail(400, "template_unavailable", "قالب یا گروه انتخاب‌شده در پاسارگارد فعال نیست");
    }

    remote = users.find((user) => user.username.toLowerCase() === input.data.remoteUsername) ?? null;
    if (remote) {
      const existingBinding = await db.pasarGuardBinding.findUnique({
        where: { externalUserId: BigInt(remote.id) },
        include: { service: true },
      });
      if (existingBinding) return ok(receipt(existingBinding.service, remote, true));
    }

    const existingUser = await db.user.findUnique({ where: { email: internalEmail }, select: { id: true } });
    const user = await db.user.upsert({
      where: { email: internalEmail },
      update: { status: "ACTIVE" },
      create: {
        email: internalEmail,
        emailVerifiedAt: new Date(),
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

    const providerNote = [
      `NimHUB: ${input.data.customerName}`,
      input.data.note,
    ].filter(Boolean).join(" — ");
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
        input.data.remoteUsername,
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
          providerProfile: input.data.profileKey,
          quotaBytes: quotaBytes.toString(),
          durationDays: input.data.days,
          maxDevices: input.data.maxDevices,
        },
      },
    });
    return ok(receipt(binding.service, remote), { status: 201 });
  } catch (error) {
    if (remote) {
      const partial = await db.pasarGuardBinding.findUnique({
        where: { externalUserId: BigInt(remote.id) },
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
