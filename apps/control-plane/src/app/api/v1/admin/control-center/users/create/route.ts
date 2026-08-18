import type { NextRequest } from "next/server";
import { NextRequest as ForwardRequest } from "next/server";
import { POST as createManagedLicense } from "@/app/api/v1/admin/licenses/route";
import { DELETE as deleteManagedLicense } from "@/app/api/v1/admin/licenses/delete/route";
import { adminFromRequest, isSameOrigin } from "@/lib/admin-session";
import { fail, ok } from "@/lib/api";
import { db } from "@/lib/db";

export async function POST(request: NextRequest) {
  if (!isSameOrigin(request)) return fail(403, "invalid_origin", "مبدأ درخواست معتبر نیست");
  const admin = await adminFromRequest(request);
  if (!admin || admin.role !== "ADMIN") return fail(403, "forbidden", "فقط مدیر اصلی مجاز است");

  const input = await request.json().catch(() => null) as Record<string, unknown> | null;
  if (!input || typeof input !== "object") return fail(400, "invalid_input", "اطلاعات کاربر معتبر نیست");
  const vipAccess = input.vipAccess === true;
  const legacyPayload = { ...input };
  delete legacyPayload.vipAccess;

  const forwarded = new ForwardRequest(request.url, {
    method: "POST",
    headers: request.headers,
    body: JSON.stringify(legacyPayload),
  });
  const created = await createManagedLicense(forwarded);
  const payload = await created.json().catch(() => null) as {
    data?: {
      reused?: boolean;
      service?: { id?: string };
      [key: string]: unknown;
    };
    error?: unknown;
  } | null;

  if (!created.ok || !payload?.data?.service?.id) {
    return new Response(JSON.stringify(payload ?? { error: { code: "create_failed", message: "ساخت کاربر انجام نشد" } }), {
      status: created.status,
      headers: { "content-type": "application/json" },
    });
  }

  const serviceId = payload.data.service.id;
  const reused = payload.data.reused === true;
  const before = await db.service.findUnique({ where: { id: serviceId }, select: { vipAccess: true } });
  if (!before) return fail(500, "created_service_missing", "کاربر ساخته شد اما اشتراک ایجادشده پیدا نشد");

  try {
    if (before.vipAccess !== vipAccess) {
      await db.$transaction(async (tx) => {
        await tx.service.update({ where: { id: serviceId }, data: { vipAccess } });
        await tx.auditLog.create({
          data: {
            actorId: admin.sub,
            action: "service.vipAccess",
            entityType: "Service",
            entityId: serviceId,
            before: { vipAccess: before.vipAccess },
            after: { vipAccess },
          },
        });
      });
    }
    return ok({ ...payload.data, vipAccess });
  } catch (error) {
    if (reused) {
      return fail(500, "vip_finalize_failed", "وضعیت VIP ذخیره نشد؛ اشتراک موجود بدون تغییر باقی ماند", {
        serviceId,
        retryable: true,
        technical: error instanceof Error ? error.message : "unknown_error",
      });
    }

    const rollbackRequest = new ForwardRequest(new URL("/api/v1/admin/licenses/delete", request.url), {
      method: "DELETE",
      headers: request.headers,
      body: JSON.stringify({ serviceId }),
    });
    const rollback = await deleteManagedLicense(rollbackRequest);
    if (rollback.ok) {
      return fail(500, "vip_finalize_rolled_back", "ساخت کاربر کامل نشد و تغییرات ایجادشده با موفقیت بازگردانده شد؛ دوباره تلاش کنید", {
        serviceId,
        retryable: true,
        technical: error instanceof Error ? error.message : "unknown_error",
      });
    }

    const rollbackBody = await rollback.json().catch(() => null) as { error?: { message?: string } } | null;
    return fail(500, "vip_finalize_rollback_failed", "تکمیل VIP و بازگردانی خودکار هر دو ناموفق شدند؛ این مورد نیازمند بررسی مدیر است", {
      serviceId,
      retryable: false,
      technical: `${error instanceof Error ? error.message : "unknown_error"}; rollback=${rollbackBody?.error?.message ?? rollback.status}`,
    });
  }
}
