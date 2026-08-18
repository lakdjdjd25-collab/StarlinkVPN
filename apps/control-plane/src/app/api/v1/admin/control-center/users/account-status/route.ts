import type { NextRequest } from "next/server";
import { z } from "zod";
import { adminFromRequest, isSameOrigin } from "@/lib/admin-session";
import { fail, ok } from "@/lib/api";
import { adminAccountTransition } from "@/lib/admin-v2-account-status";
import { db } from "@/lib/db";

const schema = z.object({
  userId: z.string().min(1),
  status: z.enum(["ACTIVE", "SUSPENDED"]),
});

export async function PATCH(request: NextRequest) {
  if (!isSameOrigin(request)) return fail(403, "invalid_origin", "مبدأ درخواست معتبر نیست");

  const admin = await adminFromRequest(request);
  if (!admin || admin.role !== "ADMIN") {
    return fail(403, "forbidden", "فقط مدیر اصلی مجاز است");
  }

  const input = schema.safeParse(await request.json().catch(() => null));
  if (!input.success) {
    return fail(400, "invalid_input", input.error.issues[0]?.message ?? "وضعیت حساب معتبر نیست");
  }

  const user = await db.user.findUnique({
    where: { id: input.data.userId },
    select: { id: true, email: true, role: true, status: true },
  });
  if (!user || user.role !== "CUSTOMER") {
    return fail(404, "customer_not_found", "حساب کاربر پیدا نشد");
  }
  if (user.status !== "ACTIVE" && user.status !== "SUSPENDED") {
    return fail(409, "account_deleted", "حساب حذف‌شده از این بخش قابل فعال‌سازی یا تعلیق نیست");
  }

  const transition = adminAccountTransition(user.status, input.data.status);
  if (!transition.changed) {
    return ok({
      id: user.id,
      status: user.status,
      changed: false,
      sessionsRevoked: false,
      serviceStatusesChanged: false,
    });
  }

  const now = new Date();
  const result = await db.$transaction(async (tx) => {
    const updated = await tx.user.update({
      where: { id: user.id },
      data: { status: input.data.status },
      select: { id: true, email: true, status: true },
    });

    let revokedDevices = 0;
    let revokedRefreshTokens = 0;
    if (transition.revokeSessions) {
      const [devices, refreshTokens] = await Promise.all([
        tx.device.updateMany({
          where: { userId: user.id, revokedAt: null },
          data: { revokedAt: now },
        }),
        tx.refreshToken.updateMany({
          where: { userId: user.id, revokedAt: null },
          data: { revokedAt: now },
        }),
      ]);
      revokedDevices = devices.count;
      revokedRefreshTokens = refreshTokens.count;
    }

    await tx.auditLog.create({
      data: {
        actorId: admin.sub,
        action: transition.revokeSessions ? "user.suspend" : "user.reactivate",
        entityType: "User",
        entityId: user.id,
        before: { status: user.status },
        after: {
          status: updated.status,
          revokedDevices,
          revokedRefreshTokens,
          serviceStatusesChanged: transition.serviceStatusesChanged,
        },
      },
    });

    return { updated, revokedDevices, revokedRefreshTokens };
  });

  return ok({
    id: result.updated.id,
    status: result.updated.status,
    changed: true,
    sessionsRevoked: transition.revokeSessions,
    revokedDevices: result.revokedDevices,
    revokedRefreshTokens: result.revokedRefreshTokens,
    serviceStatusesChanged: transition.serviceStatusesChanged,
  });
}
