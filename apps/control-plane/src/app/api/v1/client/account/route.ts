import { randomBytes } from "node:crypto";
import type { NextRequest } from "next/server";
import { z } from "zod";
import { verifyPassword } from "@/lib/auth";
import { fail, ok, requireBearer } from "@/lib/api";
import { db } from "@/lib/db";

const schema = z.object({ password: z.string().min(1).max(256) });

export async function DELETE(request: NextRequest) {
  const auth = await requireBearer(request, ["CUSTOMER"]);
  if (!auth.ok) return auth.response;
  const input = schema.safeParse(await request.json().catch(() => null));
  if (!input.success) return fail(400, "invalid_input", "گذرواژه را وارد کنید");

  const user = await db.user.findFirst({
    where: { id: auth.userId, status: "ACTIVE" },
    select: { id: true, passwordHash: true },
  });
  if (!user?.passwordHash || !(await verifyPassword(input.data.password, user.passwordHash))) {
    return fail(401, "invalid_password", "گذرواژه صحیح نیست");
  }

  const now = new Date();
  const scrubbedEmail = `deleted-${user.id}-${randomBytes(8).toString("hex")}@deleted.invalid`;
  await db.$transaction(async (transaction) => {
    await transaction.service.deleteMany({ where: { userId: user.id } });
    await transaction.notificationDelivery.deleteMany({ where: { userId: user.id } });
    await transaction.authCode.deleteMany({ where: { userId: user.id } });
    await transaction.device.deleteMany({ where: { userId: user.id } });
    await transaction.user.update({
      where: { id: user.id },
      data: {
        email: scrubbedEmail,
        passwordHash: null,
        emailVerifiedAt: null,
        telegramId: null,
        balance: 0,
        status: "DELETED",
        deletedAt: now,
      },
    });
  });
  return ok({ deleted: true });
}
