import type { NextRequest } from "next/server";
import { z } from "zod";
import { hashPassword, verifyAuthCode } from "@/lib/auth";
import { fail, ok, requireBearer } from "@/lib/api";
import { db } from "@/lib/db";

const schema = z.object({
  challengeId: z.string().uuid(),
  code: z.string().regex(/^\d{6}$/),
  newPassword: z.string().min(8).max(72),
});

export async function POST(request: NextRequest) {
  const auth = await requireBearer(request, ["CUSTOMER"]);
  if (!auth.ok) return auth.response;
  if (auth.serviceId) {
    return fail(403, "license_session_not_allowed", "برای تأیید تغییر گذرواژه باید با حساب کاربری وارد شوید");
  }
  const input = schema.safeParse(await request.json().catch(() => null));
  if (!input.success) {
    return fail(400, "invalid_input", "کد و گذرواژهٔ جدید را به‌درستی وارد کنید");
  }

  const challenge = await db.authCode.findFirst({
    where: { id: input.data.challengeId, userId: auth.userId },
    include: { user: { select: { status: true } } },
  });
  if (
    !challenge ||
    challenge.purpose !== "password_change" ||
    challenge.usedAt ||
    challenge.expiresAt <= new Date() ||
    challenge.attempts >= 5 ||
    challenge.user.status !== "ACTIVE"
  ) {
    return fail(401, "invalid_code", "کد تأیید نامعتبر یا منقضی شده است");
  }
  if (!verifyAuthCode(challenge.id, input.data.code, challenge.codeHash)) {
    await db.authCode.update({
      where: { id: challenge.id },
      data: { attempts: { increment: 1 } },
    });
    return fail(401, "invalid_code", "کد تأیید نامعتبر یا منقضی شده است");
  }

  const passwordHash = await hashPassword(input.data.newPassword);
  const now = new Date();
  const consumed = await db.$transaction(async (transaction) => {
    const result = await transaction.authCode.updateMany({
      where: {
        id: challenge.id,
        userId: auth.userId,
        purpose: "password_change",
        usedAt: null,
        attempts: { lt: 5 },
        expiresAt: { gt: now },
      },
      data: { usedAt: now },
    });
    if (result.count !== 1) return false;
    await transaction.user.update({
      where: { id: auth.userId },
      data: { passwordHash },
    });
    await transaction.refreshToken.updateMany({
      where: { userId: auth.userId, revokedAt: null },
      data: { revokedAt: now },
    });
    return true;
  });
  if (!consumed) return fail(401, "invalid_code", "کد تأیید نامعتبر یا منقضی شده است");
  return ok({ changed: true, signInRequired: true });
}
