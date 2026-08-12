import type { NextRequest } from "next/server";
import { z } from "zod";
import { adminFromRequest, isSameOrigin } from "@/lib/admin-session";
import { fail, ok } from "@/lib/api";
import { db } from "@/lib/db";

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
  const plan = await db.plan.findUnique({ where: { id: input.data.planId } });
  if (!plan?.isActive) return fail(404, "plan_not_found", "پلن فعال پیدا نشد");
  const service = await db.service.create({
    data: {
      userId: input.data.userId,
      planId: plan.id,
      name: input.data.name,
      license: input.data.license,
      quotaBytes: plan.dataLimitBytes,
      expiresAt: new Date(Date.now() + input.data.days * 86_400_000),
      maxDevices: plan.maxDevices,
      isFree: plan.interval === "FREE",
      guardianProfile: { create: { rules: { create: [
        { category: "malware", enabled: true },
        { category: "ads", enabled: true },
        { category: "phishing", enabled: true },
      ] } } },
    },
  });
  await db.auditLog.create({
    data: { actorId: admin.sub, action: "service.create", entityType: "Service", entityId: service.id, after: { license: service.license, userId: service.userId } },
  });
  return ok(service, { status: 201 });
}

export async function PATCH(request: NextRequest) {
  if (!isSameOrigin(request)) return fail(403, "invalid_origin", "مبدأ درخواست معتبر نیست");
  const admin = await adminFromRequest(request);
  if (!admin || admin.role !== "ADMIN") return fail(403, "forbidden", "فقط مدیر اصلی مجاز است");
  const input = updateSchema.safeParse(await request.json().catch(() => null));
  if (!input.success) return fail(400, "invalid_input", "تغییرات سرویس معتبر نیست");
  const before = await db.service.findUnique({
    where: { id: input.data.id },
    select: { id: true, status: true, quotaBytes: true, expiresAt: true, maxDevices: true },
  });
  if (!before) return fail(404, "service_not_found", "سرویس پیدا نشد");
  const service = await db.service.update({
    where: { id: before.id },
    data: {
      status: input.data.status,
      quotaBytes: BigInt(Math.round(input.data.quotaGb * 1024 ** 3)),
      expiresAt: new Date(Date.now() + input.data.daysFromNow * 86_400_000),
      maxDevices: input.data.maxDevices,
    },
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
