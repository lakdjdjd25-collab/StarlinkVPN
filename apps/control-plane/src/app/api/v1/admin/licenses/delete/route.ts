import type { NextRequest } from "next/server";
import { z } from "zod";
import { adminFromRequest, isSameOrigin } from "@/lib/admin-session";
import { fail, ok } from "@/lib/api";
import { db } from "@/lib/db";
import {
  createPasarGuardClient,
  isPasarGuardConfigured,
  PasarGuardError,
} from "@/lib/pasarguard/client";

const schema = z.object({
  serviceId: z.string().min(1),
});

function providerFailure(error: unknown) {
  if (error instanceof PasarGuardError) {
    const status = error.code === "not_configured" ? 503 : error.code === "invalid_response" ? 422 : 502;
    return fail(status, `pasarguard_${error.code}`, error.message);
  }
  return fail(500, "managed_license_delete_failed", "حذف کاربر و مجوز با خطای پیش‌بینی‌نشده متوقف شد");
}

export async function DELETE(request: NextRequest) {
  if (!isSameOrigin(request)) return fail(403, "invalid_origin", "مبدأ درخواست معتبر نیست");
  const admin = await adminFromRequest(request);
  if (!admin || admin.role !== "ADMIN") return fail(403, "forbidden", "فقط مدیر اصلی مجاز است");
  const input = schema.safeParse(await request.json().catch(() => null));
  if (!input.success) return fail(400, "invalid_input", "شناسه مجوز معتبر نیست");
  if (!isPasarGuardConfigured()) {
    return fail(503, "pasarguard_not_configured", "اتصال پاسارگارد در Secretهای سرور کامل نشده است");
  }

  const target = await db.service.findUnique({
    where: { id: input.data.serviceId },
    include: {
      user: { select: { id: true, managedAccount: true } },
      plan: { select: { id: true, name: true } },
      pasarGuardBinding: { select: { externalUsername: true } },
      _count: { select: { payments: true } },
    },
  });
  if (!target?.pasarGuardBinding || !target.user.managedAccount) {
    return fail(404, "managed_license_not_found", "مجوز مدیریت‌شده پیدا نشد");
  }
  if (target._count.payments > 0) {
    return fail(409, "managed_license_has_payments", "مجوز دارای سابقه پرداخت است و برای حفظ سوابق مالی قابل حذف مستقیم نیست");
  }

  try {
    const client = createPasarGuardClient();
    try {
      await client.deleteUser(target.pasarGuardBinding.externalUsername);
    } catch (error) {
      // A retry after a partial cleanup is safe: if the remote user is already
      // gone, continue with local cleanup instead of leaving an orphan record.
      if (!(error instanceof PasarGuardError && error.status === 404)) throw error;
    }

    const deleted = await db.$transaction(async (transaction) => {
      await transaction.service.delete({ where: { id: target.id } });

      const remainingUserServices = await transaction.service.count({
        where: { userId: target.user.id },
      });
      let userDeleted = false;
      if (remainingUserServices === 0) {
        await transaction.user.delete({ where: { id: target.user.id } });
        userDeleted = true;
      }

      const remainingPlanServices = await transaction.service.count({
        where: { planId: target.plan.id },
      });
      let planDeleted = false;
      if (remainingPlanServices === 0 && target.plan.name.startsWith("NimHUB Managed ")) {
        await transaction.plan.delete({ where: { id: target.plan.id } });
        planDeleted = true;
      }

      return { userDeleted, planDeleted };
    });

    await db.auditLog.create({
      data: {
        actorId: admin.sub,
        action: "managed_license.delete",
        entityType: "Service",
        entityId: target.id,
        after: {
          userDeleted: deleted.userDeleted,
          planDeleted: deleted.planDeleted,
          providerUserDeleted: true,
        },
      },
    });

    return ok({ deleted: true, ...deleted });
  } catch (error) {
    return providerFailure(error);
  }
}
