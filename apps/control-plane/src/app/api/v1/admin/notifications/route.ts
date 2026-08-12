import type { NextRequest } from "next/server";
import { z } from "zod";
import { adminFromRequest, isSameOrigin } from "@/lib/admin-session";
import { fail, ok } from "@/lib/api";
import { db } from "@/lib/db";

const schema = z.object({
  title: z.string().min(2).max(160),
  body: z.string().min(2).max(4000),
  audience: z.enum(["ALL", "FREE", "PAID", "SELECTED"]),
  actionUrl: z.url().optional(),
  publishNow: z.boolean().default(true),
  expiresAt: z.iso.datetime().optional(),
  selectedUserIds: z.array(z.string().min(1)).max(1000).default([]),
});

export async function POST(request: NextRequest) {
  if (!isSameOrigin(request)) return fail(403, "invalid_origin", "مبدأ درخواست معتبر نیست");
  const admin = await adminFromRequest(request);
  if (!admin || admin.role !== "ADMIN") return fail(403, "forbidden", "فقط مدیر اصلی مجاز است");
  const input = schema.safeParse(await request.json().catch(() => null));
  if (!input.success) return fail(400, "invalid_input", "اطلاعات اعلان معتبر نیست");
  if (input.data.audience === "SELECTED" && input.data.selectedUserIds.length === 0) {
    return fail(400, "recipients_required", "برای اعلان انتخابی حداقل یک کاربر لازم است");
  }
  const selectedUserIds = Array.from(new Set(input.data.selectedUserIds));
  const notification = await db.notification.create({
    data: {
      title: input.data.title,
      body: input.data.body,
      audience: input.data.audience,
      actionUrl: input.data.actionUrl,
      publishedAt: input.data.publishNow ? new Date() : null,
      expiresAt: input.data.expiresAt ? new Date(input.data.expiresAt) : null,
      deliveries: input.data.audience === "SELECTED"
        ? { create: selectedUserIds.map((userId) => ({ userId })) }
        : undefined,
    },
  });
  await db.auditLog.create({
    data: {
      actorId: admin.sub,
      action: "notification.create",
      entityType: "Notification",
      entityId: notification.id,
      after: { title: notification.title, audience: notification.audience },
    },
  });
  return ok(notification, { status: 201 });
}
