import { createHash } from "node:crypto";
import type { NextRequest } from "next/server";
import { z } from "zod";
import { adminFromRequest, isSameOrigin } from "@/lib/admin-session";
import { fail, ok } from "@/lib/api";
import { db } from "@/lib/db";
import {
  createPasarGuardClient,
  PasarGuardError,
  type PasarGuardUser,
} from "@/lib/pasarguard/client";
import { bindPasarGuardUser, syncPasarGuardBinding } from "@/lib/pasarguard/sync";

const schema = z.object({
  userId: z.string().min(1),
  planId: z.string().min(1),
  name: z.string().min(2).max(120),
  license: z.string().min(6).max(64).transform((value) => value.toUpperCase()),
  days: z.number().int().min(1).max(3650),
});

const updateSchema = z.object({
  id: z.string().min(1),
  status: z.enum(["ACTIVE", "EXPIRED", "SUSPENDED", "CANCELLED"]),
  quotaGb: z.number().nonnegative().max(1_000_000),
  daysFromNow: z.number().int().min(0).max(3650),
  maxDevices: z.number().int().min(1).max(1000),
});

function remoteUsername(userId: string, license: string): string {
  return `nh_${createHash("sha256").update(`${userId}:${license}`).digest("hex").slice(0, 24)}`;
}

function remoteGroupIds(users: PasarGuardUser[]): number[] {
  const active = users.filter((user) => user.status.toLowerCase() === "active" && user.groupIds.length > 0);
  const source = active.length ? active : users.filter((user) => user.groupIds.length > 0);
  const ids = [...new Set(source.flatMap((user) => user.groupIds))].sort((a, b) => a - b);
  if (!ids.length) {
    throw new PasarGuardError("not_configured", "هیچ گروه سرور فعالی در پاسارگارد برای ساخت سرویس پیدا نشد");
  }
  return ids;
}

function providerFailure(error: unknown) {
  if (error instanceof PasarGuardError) {
    return fail(
      error.code === "not_configured" ? 503 : 502,
      `pasarguard_${error.code}`,
      error.message,
    );
  }
  return fail(500, "service_provision_failed", "ساخت یا همگام‌سازی سرویس با خطای پیش‌بینی‌نشده متوقف شد");
}

export async function GET(request: NextRequest) {
  const admin = await adminFromRequest(request);
  if (!admin) return fail(401, "unauthorized", "ورود مدیر لازم است");
  return ok(await db.service.findMany({
    orderBy: { createdAt: "desc" },
    take: 100,
    include: {
      user: { select: { id: true, email: true, role: true, status: true } },
      plan: true,
    },
  }));
}

export async function POST(request: NextRequest) {
  if (!isSameOrigin(request)) return fail(403, "invalid_origin", "مبدأ درخواست معتبر نیست");
  const admin = await adminFromRequest(request);
  if (!admin || admin.role !== "ADMIN") return fail(403, "forbidden", "فقط مدیر اصلی مجاز است");
  const input = schema.safeParse(await request.json().catch(() => null));
  if (!input.success) return fail(400, "invalid_input", "اطلاعات سرویس معتبر نیست");

  const [plan, user, duplicateLicense] = await Promise.all([
    db.plan.findUnique({ where: { id: input.data.planId } }),
    db.user.findFirst({ where: { id: input.data.userId, status: "ACTIVE" }, select: { id: true, email: true } }),
    db.service.findUnique({ where: { license: input.data.license }, select: { id: true } }),
  ]);
  if (!plan?.isActive) return fail(404, "plan_not_found", "پلن فعال پیدا نشد");
  if (!user) return fail(404, "user_not_found", "کاربر فعال پیدا نشد");
  if (duplicateLicense) return fail(409, "license_exists", "این کلید مجوز قبلاً استفاده شده است");
  if (plan.dataLimitBytes <= 0n) {
    return fail(400, "finite_quota_required", "برای سرویس مدیریت‌شده پاسارگارد باید حجم پلن بیشتر از صفر باشد");
  }

  const client = createPasarGuardClient();
  const username = remoteUsername(user.id, input.data.license);
  const expiresAt = new Date(Date.now() + input.data.days * 86_400_000);
  let remote: PasarGuardUser | null = null;
  let createdRemote = false;

  try {
    const visibleUsers = await client.listUsers();
    const groups = remoteGroupIds(visibleUsers);
    remote = visibleUsers.find((item) => item.username === username) ?? null;

    if (remote) {
      const alreadyBound = await db.pasarGuardBinding.findUnique({
        where: { externalUserId: BigInt(remote.id) },
        select: { id: true },
      });
      if (alreadyBound) return fail(409, "service_already_bound", "سرویس متناظر پاسارگارد قبلاً به QuickPing متصل شده است");
      remote = await client.updateUser(remote.username, {
        dataLimit: plan.dataLimitBytes,
        expiresAt,
        maxDevices: plan.maxDevices,
        status: "active",
        groupIds: remote.groupIds.length ? remote.groupIds : groups,
        note: "NimHUB managed service",
      });
    } else {
      remote = await client.createUser(
        username,
        plan.dataLimitBytes,
        groups,
        "NimHUB managed service",
        plan.maxDevices,
        expiresAt,
      );
      createdRemote = true;
    }

    const binding = await bindPasarGuardUser(user.id, remote.id, client, {
      isFree: plan.interval === "FREE",
      planId: plan.id,
      serviceName: input.data.name,
      license: input.data.license,
      allowAdditionalBinding: true,
      expectedQuotaBytes: plan.dataLimitBytes,
    });

    await db.auditLog.create({
      data: {
        actorId: admin.sub,
        action: "service.create",
        entityType: "Service",
        entityId: binding.service.id,
        after: {
          license: binding.service.license,
          userId: binding.service.userId,
          pasarGuardUserId: remote.id,
          pasarGuardUsername: remote.username,
        },
      },
    });
    return ok(binding.service, { status: 201 });
  } catch (error) {
    if (remote) {
      const partial = await db.pasarGuardBinding.findUnique({
        where: { externalUserId: BigInt(remote.id) },
        include: { service: { select: { id: true, userId: true, license: true } } },
      }).catch(() => null);
      if (partial?.service.userId === user.id && partial.service.license === input.data.license) {
        await db.service.delete({ where: { id: partial.service.id } }).catch(() => undefined);
      }
      if (createdRemote) await client.deleteUser(remote.username).catch(() => undefined);
    }
    return providerFailure(error);
  }
}

export async function PATCH(request: NextRequest) {
  if (!isSameOrigin(request)) return fail(403, "invalid_origin", "مبدأ درخواست معتبر نیست");
  const admin = await adminFromRequest(request);
  if (!admin || admin.role !== "ADMIN") return fail(403, "forbidden", "فقط مدیر اصلی مجاز است");
  const input = updateSchema.safeParse(await request.json().catch(() => null));
  if (!input.success) return fail(400, "invalid_input", "تغییرات سرویس معتبر نیست");

  const before = await db.service.findUnique({
    where: { id: input.data.id },
    select: {
      id: true,
      status: true,
      quotaBytes: true,
      expiresAt: true,
      maxDevices: true,
      pasarGuardBinding: { select: { id: true, externalUsername: true } },
    },
  });
  if (!before) return fail(404, "service_not_found", "سرویس پیدا نشد");

  const quotaBytes = BigInt(Math.round(input.data.quotaGb * 1024 ** 3));
  const requestedExpiry = input.data.status === "EXPIRED"
    ? new Date(Date.now() - 1_000)
    : new Date(Date.now() + input.data.daysFromNow * 86_400_000);

  try {
    if (before.pasarGuardBinding) {
      const client = createPasarGuardClient();
      await client.updateUser(before.pasarGuardBinding.externalUsername, {
        dataLimit: quotaBytes,
        expiresAt: requestedExpiry,
        maxDevices: input.data.maxDevices,
        status: input.data.status === "ACTIVE" ? "active" : "disabled",
      });
      await syncPasarGuardBinding(before.pasarGuardBinding.id, client);
      if (input.data.status === "CANCELLED") {
        await db.service.update({ where: { id: before.id }, data: { status: "CANCELLED" } });
      }
    } else {
      await db.service.update({
        where: { id: before.id },
        data: {
          status: input.data.status,
          quotaBytes,
          expiresAt: requestedExpiry,
          maxDevices: input.data.maxDevices,
        },
      });
    }
  } catch (error) {
    return providerFailure(error);
  }

  const service = await db.service.findUniqueOrThrow({
    where: { id: before.id },
    select: {
      id: true,
      name: true,
      status: true,
      quotaBytes: true,
      expiresAt: true,
      maxDevices: true,
    },
  });
  await db.auditLog.create({
    data: {
      actorId: admin.sub,
      action: "service.update",
      entityType: "Service",
      entityId: service.id,
      before: {
        status: before.status,
        quotaBytes: String(before.quotaBytes),
        expiresAt: before.expiresAt.toISOString(),
        maxDevices: before.maxDevices,
      },
      after: {
        status: service.status,
        quotaBytes: String(service.quotaBytes),
        expiresAt: service.expiresAt.toISOString(),
        maxDevices: service.maxDevices,
      },
    },
  });
  return ok(service);
}
