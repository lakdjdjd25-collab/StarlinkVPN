import { createHash, randomBytes, randomUUID } from "node:crypto";
import type { NextRequest } from "next/server";
import { z } from "zod";
import { fail, ok } from "@/lib/api";
import { db } from "@/lib/db";
import { GoogleAuthError, googleWebClientId } from "@/lib/google-auth";
import { isPasarGuardConfigured, PasarGuardError } from "@/lib/pasarguard/client";
import { pasarGuardFreeTemplateId } from "@/lib/pasarguard/google-free";

const schema = z.object({
  installationId: z.string().min(8).max(160),
});

function nonceHash(value: string): string {
  return createHash("sha256").update(value).digest("hex");
}

export async function POST(request: NextRequest) {
  const input = schema.safeParse(await request.json().catch(() => null));
  if (!input.success) return fail(400, "invalid_input", "درخواست ورود گوگل معتبر نیست");

  try {
    const serverClientId = googleWebClientId();
    pasarGuardFreeTemplateId();
    if (!isPasarGuardConfigured()) {
      throw new PasarGuardError("not_configured", "اتصال پاسارگارد برای سرویس رایگان تنظیم نشده است");
    }

    const challengeId = randomUUID();
    const nonce = randomBytes(32).toString("base64url");
    const expiresAt = new Date(Date.now() + 5 * 60_000);
    await db.$transaction([
      db.federatedAuthNonce.deleteMany({
        where: {
          OR: [
            { expiresAt: { lt: new Date() } },
            { usedAt: { not: null }, createdAt: { lt: new Date(Date.now() - 60 * 60_000) } },
          ],
        },
      }),
      db.federatedAuthNonce.create({
        data: {
          id: challengeId,
          provider: "google",
          nonceHash: nonceHash(nonce),
          installationId: input.data.installationId,
          expiresAt,
        },
      }),
    ]);

    return ok({
      challengeId,
      nonce,
      serverClientId,
      expiresInSeconds: 300,
    });
  } catch (error) {
    if (error instanceof GoogleAuthError || error instanceof PasarGuardError) {
      return fail(503, "google_login_unavailable", error.message);
    }
    return fail(503, "google_login_unavailable", "ورود گوگل فعلاً در دسترس نیست");
  }
}
