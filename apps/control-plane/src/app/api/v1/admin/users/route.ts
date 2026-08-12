import type { NextRequest } from "next/server";
import { z } from "zod";
import { adminFromRequest, isSameOrigin } from "@/lib/admin-session";
import { fail, ok } from "@/lib/api";
import { hashPassword } from "@/lib/auth";
import { db } from "@/lib/db";

const schema = z.object({
  email: z.email().transform((value) => value.toLowerCase()),
  password: z.string().min(12).max(256).optional(),
  role: z.enum(["ADMIN", "SUPPORT", "CUSTOMER"]).default("CUSTOMER"),
});

const updateSchema = z.object({
  id: z.string().min(1),
  status: z.enum(["ACTIVE", "SUSPENDED"]),
  role: z.enum(["ADMIN", "SUPPORT", "CUSTOMER"]),
});

export async function GET(request: NextRequest) {
  const admin = await adminFromRequest(request);
  if (!admin) return fail(401, "unauthorized", "ورود مدیر لازم است");
  return ok(await db.user.findMany({
    orderBy: { createdAt: "desc" },
    take: 100,
    select: {
      id: true,
      email: true,
      role: true,
      status: true,
      emailVerifiedAt: true,
      telegramId: true,
      balance: true,
      language: true,
      createdAt: true,
      updatedAt: true,
    },
  }));
}

export async function POST(request: NextRequest) {
  if (!isSameOrigin(request)) return fail(403, "invalid_origin", "مبدأ درخواست معتبر نیست");
  const admin = await adminFromRequest(request);
  if (!admin || admin.role !== "ADMIN") return fail(403, "forbidden", "فقط مدیر اصلی مجاز است");
  const input = schema.safeParse(await request.json().catch(() => null));
  if (!input.success) return fail(400, "invalid_input", "اطلاعات کاربر معتبر نیست");
  const user = await db.user.create({
    data: {
      email: input.data.email,
      role: input.data.role,
      passwordHash: input.data.password ? await hashPassword(input.data.password) : null,
      emailVerifiedAt: input.data.password ? new Date() : null,
    },
  });
  await db.auditLog.create({
    data: { actorId: admin.sub, action: "user.create", entityType: "User", entityId: user.id, after: { email: user.email, role: user.role } },
  });
  return ok({ id: user.id, email: user.email, role: user.role }, { status: 201 });
}

export async function PATCH(request: NextRequest) {
  if (!isSameOrigin(request)) return fail(403, "invalid_origin", "مبدأ درخواست معتبر نیست");
  const admin = await adminFromRequest(request);
  if (!admin || admin.role !== "ADMIN") return fail(403, "forbidden", "فقط مدیر اصلی مجاز است");
  const input = updateSchema.safeParse(await request.json().catch(() => null));
  if (!input.success) return fail(400, "invalid_input", "تغییرات کاربر معتبر نیست");
  if (input.data.id === admin.sub && (input.data.status !== "ACTIVE" || input.data.role !== "ADMIN")) {
    return fail(400, "self_lockout", "مدیر نمی‌تواند دسترسی حساب جاری خود را حذف کند");
  }
  const before = await db.user.findUnique({
    where: { id: input.data.id },
    select: { id: true, status: true, role: true },
  });
  if (!before) return fail(404, "user_not_found", "کاربر پیدا نشد");
  const user = await db.$transaction(async (transaction) => {
    const updated = await transaction.user.update({
      where: { id: before.id },
      data: { status: input.data.status, role: input.data.role },
      select: { id: true, email: true, status: true, role: true },
    });
    if (updated.status !== "ACTIVE") {
      await Promise.all([
        transaction.refreshToken.updateMany({
          where: { userId: updated.id, revokedAt: null },
          data: { revokedAt: new Date() },
        }),
        transaction.device.updateMany({
          where: { userId: updated.id, revokedAt: null },
          data: { revokedAt: new Date() },
        }),
      ]);
    }
    return updated;
  });
  await db.auditLog.create({
    data: {
      actorId: admin.sub,
      action: "user.updateAccess",
      entityType: "User",
      entityId: user.id,
      before: { status: before.status, role: before.role },
      after: { status: user.status, role: user.role },
    },
  });
  return ok(user);
}
