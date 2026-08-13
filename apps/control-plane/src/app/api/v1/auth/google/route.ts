import { createHash } from "node:crypto";
import type { NextRequest } from "next/server";
import { z } from "zod";
import { createOpaqueToken, issueToken } from "@/lib/auth";
import { fail, ok } from "@/lib/api";
import { db } from "@/lib/db";
import { GoogleAuthError, verifyGoogleIdToken, type GoogleIdentity } from "@/lib/google-auth";
import { PasarGuardError } from "@/lib/pasarguard/client";
import { ensureGoogleFreeService } from "@/lib/pasarguard/google-free";

const schema = z.object({
  challengeId: z.string().uuid(),
  nonce: z.string().min(32).max(256),
  idToken: z.string().min(100).max(16_384),
  installationId: z.string().min(8).max(160),
  deviceName: z.string().min(1).max(120),
  appVersion: z.string().max(32).optional(),
  language: z.string().min(2).max(12).default("fa"),
});

class GoogleAccountConflict extends Error {}
class SignupDisabled extends Error {}

function nonceHash(value: string): string {
  return createHash("sha256").update(value).digest("hex");
}

async function resolveGoogleUser(identity: GoogleIdentity, language: string) {
  const bySubject = await db.user.findUnique({ where: { googleSubject: identity.subject } });
  if (bySubject) return bySubject;

  const byEmail = await db.user.findUnique({ where: { email: identity.email } });
  if (byEmail) {
    if (byEmail.googleSubject && byEmail.googleSubject !== identity.subject) {
      throw new GoogleAccountConflict("این ایمیل قبلاً به حساب گوگل دیگری متصل شده است");
    }
    try {
      return await db.user.update({
        where: { id: byEmail.id },
        data: {
          googleSubject: identity.subject,
          emailVerifiedAt: byEmail.emailVerifiedAt ?? new Date(),
        },
      });
    } catch {
      const raced = await db.user.findUnique({ where: { googleSubject: identity.subject } });
      if (raced) return raced;
      throw new GoogleAccountConflict("اتصال حساب گوگل به این ایمیل انجام نشد");
    }
  }

  if (process.env.SIGNUP_ENABLED === "false") {
    throw new SignupDisabled("ثبت‌نام حساب جدید غیرفعال است");
  }
  try {
    return await db.user.create({
      data: {
        email: identity.email,
        googleSubject: identity.subject,
        emailVerifiedAt: new Date(),
        language,
      },
    });
  } catch {
    const raced = await db.user.findUnique({ where: { googleSubject: identity.subject } });
    if (raced) return raced;
    throw new GoogleAccountConflict("حساب گوگل با یک حساب موجود تداخل دارد");
  }
}

export async function POST(request: NextRequest) {
  const input = schema.safeParse(await request.json().catch(() => null));
  if (!input.success) return fail(400, "invalid_input", "اطلاعات ورود گوگل معتبر نیست");

  const consumed = await db.federatedAuthNonce.updateMany({
    where: {
      id: input.data.challengeId,
      provider: "google",
      installationId: input.data.installationId,
      nonceHash: nonceHash(input.data.nonce),
      usedAt: null,
      expiresAt: { gt: new Date() },
    },
    data: { usedAt: new Date() },
  });
  if (consumed.count !== 1) {
    return fail(401, "invalid_google_challenge", "درخواست ورود گوگل نامعتبر یا منقضی شده است");
  }

  try {
    const identity = await verifyGoogleIdToken(input.data.idToken, input.data.nonce);
    const user = await resolveGoogleUser(identity, input.data.language);
    if (user.status !== "ACTIVE") {
      return fail(403, "account_unavailable", "حساب کاربری فعال نیست");
    }

    await ensureGoogleFreeService(user.id, identity.subject);

    const opaque = createOpaqueToken();
    const refreshExpiresAt = new Date(Date.now() + 30 * 86_400_000);
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
          expiresAt: refreshExpiresAt,
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
        emailVerified: true,
        telegramBound: Boolean(user.telegramId),
        balance: user.balance,
        language: user.language,
      },
    });
  } catch (error) {
    if (error instanceof GoogleAuthError) {
      return fail(401, "invalid_google_token", error.message);
    }
    if (error instanceof GoogleAccountConflict) {
      return fail(409, "google_account_conflict", error.message);
    }
    if (error instanceof SignupDisabled) {
      return fail(403, "signup_disabled", error.message);
    }
    if (error instanceof PasarGuardError) {
      return fail(503, "free_service_provisioning_failed", error.message);
    }
    return fail(500, "google_login_failed", "ورود گوگل کامل نشد");
  }
}
