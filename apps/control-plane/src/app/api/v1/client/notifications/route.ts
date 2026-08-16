import type { NextRequest } from "next/server";
import { z } from "zod";
import { fail, ok, requireBearer } from "@/lib/api";
import { db } from "@/lib/db";

const schema = z.object({
  notificationIds: z.array(z.string().min(1)).max(100).optional(),
  all: z.boolean().optional(),
}).refine((value) => value.all === true || Boolean(value.notificationIds?.length), {
  message: "notification ids required",
});

export async function PATCH(request: NextRequest) {
  const auth = await requireBearer(request);
  if (!auth.ok) return auth.response;
  const input = schema.safeParse(await request.json().catch(() => null));
  if (!input.success) return fail(400, "invalid_input", "درخواست اعلان معتبر نیست");
  const now = new Date();
  const result = await db.notificationDelivery.updateMany({
    where: {
      userId: auth.userId,
      readAt: null,
      ...(input.data.all ? {} : { notificationId: { in: input.data.notificationIds! } }),
      notification: { publishedAt: { not: null } },
    },
    data: { readAt: now, deliveredAt: now },
  });
  return ok({ updated: result.count });
}
