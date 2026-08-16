import type { NextRequest } from "next/server";
import { z } from "zod";
import { adminFromRequest, isSameOrigin } from "@/lib/admin-session";
import { fail, ok } from "@/lib/api";
import { db } from "@/lib/db";

const categorySchema = z.enum(["ACCOUNT", "SERVICE", "SYSTEM", "SUPPORT"]);
const schema = z.object({
  title: z.string().trim().min(2).max(160),
  body: z.string().trim().min(2).max(4000),
  category: categorySchema.default("SYSTEM"),
  audience: z.enum(["ALL", "FREE", "PAID", "SELECTED"]),
  actionUrl: z.url().optional(),
  publishNow: z.boolean().default(true),
  expiresAt: z.iso.datetime().optional(),
  selectedUserIds: z.array(z.string().min(1)).max(1000).default([]),
});

async function recipientIds(input: z.infer<typeof schema>): Promise<string[]> {
  if (input.audience === "SELECTED") {
    const unique = [...new Set(input.selectedUserIds)];
    if (!unique.length) return [];
    const users = await db.user.findMany({
      where: { id: { in: unique }, role: "CUSTOMER", status: { not: "DELETED" } },
      select: { id: true },
    });
    return users.map((user) => user.id);
  }
  const serviceFilter = input.audience === "ALL"
    ? undefined
    : input.audience === "FREE"
      ? { some: { isFree: true, status: "ACTIVE" as const } }
      : { some: { isFree: false, status: "ACTIVE" as const } };
  const users = await db.user.findMany({
    where: {
      role: "CUSTOMER",
      status: { not: "DELETED" },
      ...(serviceFilter ? { services: serviceFilter } : {}),
    },
    select: { id: true },
  });
  return users.map((user) => user.id);
}

export async function POST(request: NextRequest) {
  if (!isSameOrigin(request)) return fail(403, "invalid_origin", "مبدأ درخواست معتبر نیست");
  const admin = await adminFromRequest(request);
  if (!admin || admin.role !== "ADMIN") return fail(403, "forbidden", "فقط مدیر اصلی مجاز است");
  const input = schema.safeParse(await request.json().catch(() => null));
  if (!input.success) return fail(400, "invalid_input", "اطلاعات اعلان معتبر نیست");
  if (input.data.audience === "SELECTED" && input.data.selectedUserIds.length === 0) {
    return fail(400, "recipients_required", "برای اعلان شخصی حداقل یک کاربر لازم است");
  }

  const recipients = await recipientIds(input.data);
  if (input.data.audience === "SELECTED" && recipients.length === 0) {
    return fail(400, "recipients_unavailable", "هیچ کاربر فعالی از انتخاب انجام‌شده پیدا نشد");
  }
  const publishedAt = input.data.publishNow ? new Date() : null;
  const notification = await db.$transaction(async (transaction) => {
    const created = await transaction.notification.create({
      data: {
        title: input.data.title,
        body: input.data.body,
        category: input.data.category,
        audience: input.data.audience,
        actionUrl: input.data.actionUrl,
        publishedAt,
        expiresAt: input.data.expiresAt ? new Date(input.data.expiresAt) : null,
      },
    });
    if (recipients.length) {
      await transaction.notificationDelivery.createMany({
        data: recipients.map((userId) => ({
          notificationId: created.id,
          userId,
          deliveredAt: null,
          readAt: null,
        })),
        skipDuplicates: true,
      });
    }
    return created;
  });
  await db.auditLog.create({
    data: {
      actorId: admin.sub,
      action: "notification.create",
      entityType: "Notification",
      entityId: notification.id,
      after: {
        title: notification.title,
        category: notification.category,
        audience: notification.audience,
        recipientCount: recipients.length,
      },
    },
  });
  return ok({ ...notification, recipientCount: recipients.length }, { status: 201 });
}
