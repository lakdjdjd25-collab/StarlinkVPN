import type { NextRequest } from "next/server";
import { z } from "zod";
import { createOpaqueToken, issueToken, verifyPassword } from "@/lib/auth";
import { fail, ok } from "@/lib/api";
import { db } from "@/lib/db";

const schema = z.object({
  email: z.string().trim().min(1).max(320),
  password: z.string().max(256).default(""),
  installationId: z.string().min(8).max(160),
  deviceName: z.string().min(1).max(120),
  appVersion: z.string().max(32).optional(),
});

export async function POST(request: NextRequest) {
  const input = schema.safeParse(await request.json().catch(() => null));
  if (!input.success) return fail(400, "invalid_input", "Login data is invalid");

  const credential = input.data.email.trim();
  const emailLogin = credential.includes("@");
  const service = emailLogin
    ? null
    : await db.service.findFirst({
        where: { license: { equals: credential, mode: "insensitive" } },
        include: { user: true },
      });
  const user = emailLogin
    ? await db.user.findUnique({ where: { email: credential.toLowerCase() } })
    : service?.user ?? null;

  if (!user || user.status !== "ACTIVE") {
    return fail(401, emailLogin ? "invalid_credentials" : "invalid_license", emailLogin ? "Email or password is incorrect" : "License is invalid or inactive");
  }

  if (emailLogin) {
    if (!user.passwordHash || !(await verifyPassword(input.data.password, user.passwordHash))) {
      return fail(401, "invalid_credentials", "Email or password is incorrect");
    }
  } else if (!service || service.status !== "ACTIVE" || service.expiresAt.getTime() <= Date.now()) {
    return fail(401, "invalid_license", "License is invalid or inactive");
  }

  const opaque = createOpaqueToken();
  const expiresAt = new Date(Date.now() + 30 * 86_400_000);
  const accessToken = await issueToken(user.id, user.role);
  await db.$transaction(async (transaction) => {
    const device = await transaction.device.upsert({
      where: { installationId: input.data.installationId },
      update: {
        userId: user.id,
        name: input.data.deviceName,
        appVersion: input.data.appVersion,
        lastSeenAt: new Date(),
        revokedAt: null,
      },
      create: {
        userId: user.id,
        installationId: input.data.installationId,
        name: input.data.deviceName,
        appVersion: input.data.appVersion,
      },
    });
    await transaction.refreshToken.create({
      data: {
        userId: user.id,
        deviceId: device.id,
        tokenHash: opaque.hash,
        expiresAt,
      },
    });
  });

  return ok({
    accessToken,
    refreshToken: opaque.raw,
    expiresInSeconds: 900,
    user: {
      id: user.id,
      email: user.email,
      emailVerified: Boolean(user.emailVerifiedAt),
      telegramBound: Boolean(user.telegramId),
      balance: user.balance,
      language: user.language,
    },
  });
}
