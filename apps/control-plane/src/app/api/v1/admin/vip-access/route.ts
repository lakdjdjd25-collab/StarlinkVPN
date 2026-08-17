import type { NextRequest } from "next/server";
import { z } from "zod";
import { adminFromRequest, isSameOrigin } from "@/lib/admin-session";
import { fail, ok } from "@/lib/api";
import { db } from "@/lib/db";

const updateSchema = z.object({
  serviceId: z.string().min(1),
  vipAccess: z.boolean(),
});

export async function GET(request: NextRequest) {
  const admin = await adminFromRequest(request);
  if (!admin) return fail(401, "unauthorized", "ورود مدیر لازم است");
  const services = await db.service.findMany({
    select: { id: true, vipAccess: true },
    orderBy: { createdAt: "desc" },
  });
  return ok({ services });
}

export async function PATCH(request: NextRequest) {
  if (!isSameOrigin(request)) return fail(403, "invalid_origin", "مبدأ درخواست معتبر نیست");
  const admin = await adminFromRequest(request);
  if (!admin || admin.role !== "ADMIN") return fail(403, "forbidden", "فقط مدیر اصلی مجاز است");
  const input = updateSchema.safeParse(await request.json().catch(() => null));
  if (!input.success) return fail(400, "invalid_input", "تنظیم VIP معتبر نیست");

  const before = await db.service.findUnique({
    where: { id: input.data.serviceId },
    select: { id: true, vipAccess: true },
  });
  if (!before) return fail(404, "service_not_found", "مجوز پیدا نشد");
  if (before.vipAccess === input.data.vipAccess) {
    return ok({ id: before.id, vipAccess: before.vipAccess, changed: false });
  }

  const service = await db.service.update({
    where: { id: before.id },
    data: { vipAccess: input.data.vipAccess },
    select: { id: true, vipAccess: true },
  });
  await db.auditLog.create({
    data: {
      actorId: admin.sub,
      action: "service.vipAccess",
      entityType: "Service",
      entityId: service.id,
      before: { vipAccess: before.vipAccess },
      after: { vipAccess: service.vipAccess },
    },
  });
  return ok({ ...service, changed: true });
}
