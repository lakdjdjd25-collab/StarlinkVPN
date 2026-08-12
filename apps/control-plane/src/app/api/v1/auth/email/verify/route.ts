import type { NextRequest } from "next/server";
import { z } from "zod";
import { createOpaqueToken, issueToken, verifyAuthCode } from "@/lib/auth";
import { fail, ok } from "@/lib/api";
import { db } from "@/lib/db";

const schema = z.object({
  challengeId: z.string().min(1).max(160),
  code: z.string().regex(/^\d{6}$/),
  installationId: z.string().min(8).max(160),
  deviceName: z.string().min(1).max(120),
  appVersion: z.string().max(32).optional(),
});

export async function POST(request: NextRequest) {
  const input = schema.safeParse(await request.json().catch(() => null));
  if (!input.success) return fail(400, "invalid_input", "کد ورود معتبر نیست");
  const challenge = await db.authCode.findUnique({
    where: { id: input.data.challengeId },
    include: { user: true },
  });
  if (
    !challenge ||
    challenge.purpose !== "email_login" ||
    challenge.usedAt ||
    challenge.expiresAt <= new Date() ||
    challenge.attempts >= 5 ||
    challenge.installationId !== input.data.installationId ||
    challenge.user.status !== "ACTIVE"
  ) {
    return fail(401, "invalid_code", "کد ورود نامعتبر یا منقضی شده است");
  }
  if (!verifyAuthCode(challenge.id, input.data.code, challenge.codeHash)) {
    await db.authCode.update({
      where: { id: challenge.id },
      data: { attempts: { increment: 1 } },
    });
    return fail(401, "invalid_code", "کد ورود نامعتبر یا منقضی شده است");
  }

  const opaque = createOpaqueToken();
  const refreshExpiresAt = new Date(Date.now() + 30 * 86_400_000);
  const accessToken = await issueToken(challenge.user.id, challenge.user.role);
  const consumed = await db.$transaction(async (transaction) => {
    const result = await transaction.authCode.updateMany({
      where: {
        id: challenge.id,
        usedAt: null,
        attempts: { lt: 5 },
        expiresAt: { gt: new Date() },
      },
      data: { usedAt: new Date() },
    });
    if (result.count !== 1) return false;
    await transaction.user.update({
      where: { id: challenge.user.id },
      data: { emailVerifiedAt: challenge.user.emailVerifiedAt ?? new Date() },
    });
    const device = await transaction.device.upsert({
      where: { installationId: input.data.installationId },
      update: {
        userId: challenge.user.id,
        name: input.data.deviceName,
        appVersion: input.data.appVersion,
        lastSeenAt: new Date(),
        revokedAt: null,
      },
      create: {
        userId: challenge.user.id,
        installationId: input.data.installationId,
        name: input.data.deviceName,
        appVersion: input.data.appVersion,
      },
    });
    await transaction.refreshToken.create({
      data: {
        userId: challenge.user.id,
        deviceId: device.id,
        tokenHash: opaque.hash,
        expiresAt: refreshExpiresAt,
      },
    });
    return true;
  });
  if (!consumed) return fail(401, "invalid_code", "کد ورود نامعتبر یا منقضی شده است");

  return ok({
    accessToken,
    refreshToken: opaque.raw,
    expiresInSeconds: 900,
    user: {
      id: challenge.user.id,
      email: challenge.user.email,
      emailVerified: true,
      telegramBound: Boolean(challenge.user.telegramId),
      balance: challenge.user.balance,
      language: challenge.user.language,
    },
  });
}
