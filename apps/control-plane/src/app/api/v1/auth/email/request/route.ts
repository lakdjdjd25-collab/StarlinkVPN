import type { NextRequest } from "next/server";
import { randomUUID } from "node:crypto";
import { z } from "zod";
import { createAuthCode, hashAuthCode } from "@/lib/auth";
import { fail, ok } from "@/lib/api";
import { db } from "@/lib/db";
import { sendLoginCode } from "@/lib/email";

const schema = z.object({
  email: z.email().transform((value) => value.trim().toLowerCase()),
  installationId: z.string().min(8).max(160),
  language: z.string().min(2).max(12).default("fa"),
});

export async function POST(request: NextRequest) {
  const input = schema.safeParse(await request.json().catch(() => null));
  if (!input.success) return fail(400, "invalid_input", "نشانی ایمیل معتبر نیست");

  let user = await db.user.findUnique({ where: { email: input.data.email } });
  if (!user) {
    if (process.env.SIGNUP_ENABLED === "false") {
      return ok({ accepted: true, expiresInSeconds: 600 }, { status: 202 });
    }
    user = await db.user.create({
      data: { email: input.data.email, language: input.data.language },
    });
  }
  if (user.status !== "ACTIVE") {
    return ok({ accepted: true, expiresInSeconds: 600 }, { status: 202 });
  }

  const recent = await db.authCode.findFirst({
    where: {
      userId: user.id,
      purpose: "email_login",
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
      purpose: "email_login",
      installationId: input.data.installationId,
      expiresAt: new Date(Date.now() + 10 * 60_000),
    },
  });

  try {
    const delivered = await sendLoginCode({ to: user.email, code });
    return ok({
      accepted: true,
      challengeId: challenge.id,
      expiresInSeconds: 600,
      ...(process.env.NODE_ENV !== "production" && !delivered ? { debugCode: code } : {}),
    }, { status: 202 });
  } catch {
    await db.authCode.delete({ where: { id: challenge.id } }).catch(() => undefined);
    return fail(503, "email_unavailable", "ارسال کد ورود فعلاً امکان‌پذیر نیست");
  }
}
