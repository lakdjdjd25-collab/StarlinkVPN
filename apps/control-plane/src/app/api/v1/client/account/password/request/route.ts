import { randomUUID } from "node:crypto";
import type { NextRequest } from "next/server";
import { createAuthCode, hashAuthCode } from "@/lib/auth";
import { fail, ok, requireBearer } from "@/lib/api";
import { db } from "@/lib/db";
import { sendPasswordChangeCode } from "@/lib/email";

const PURPOSE = "password_change";

export async function POST(request: NextRequest) {
  const auth = await requireBearer(request, ["CUSTOMER"]);
  if (!auth.ok) return auth.response;

  const user = await db.user.findFirst({
    where: { id: auth.userId, status: "ACTIVE" },
    select: { id: true, email: true },
  });
  if (!user) return fail(403, "account_unavailable", "حساب کاربری در دسترس نیست");

  const recent = await db.authCode.findFirst({
    where: {
      userId: user.id,
      purpose: PURPOSE,
      createdAt: { gt: new Date(Date.now() - 60_000) },
    },
    orderBy: { createdAt: "desc" },
  });
  if (recent) return fail(429, "try_later", "برای دریافت کد جدید کمی صبر کنید");

  const challengeId = randomUUID();
  const code = createAuthCode();
  const challenge = await db.authCode.create({
    data: {
      id: challengeId,
      userId: user.id,
      codeHash: hashAuthCode(challengeId, code),
      purpose: PURPOSE,
      expiresAt: new Date(Date.now() + 10 * 60_000),
    },
  });

  try {
    const delivered = await sendPasswordChangeCode({ to: user.email, code });
    return ok({
      challengeId: challenge.id,
      expiresInSeconds: 600,
      ...(process.env.NODE_ENV !== "production" && !delivered ? { debugCode: code } : {}),
    }, { status: 202 });
  } catch {
    await db.authCode.delete({ where: { id: challenge.id } }).catch(() => undefined);
    return fail(503, "email_unavailable", "ارسال کد تغییر گذرواژه فعلاً امکان‌پذیر نیست");
  }
}
