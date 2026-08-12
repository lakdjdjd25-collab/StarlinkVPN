import type { NextRequest } from "next/server";
import { z } from "zod";
import { createOpaqueToken, hashOpaqueToken, issueToken } from "@/lib/auth";
import { fail, ok } from "@/lib/api";
import { db } from "@/lib/db";

const schema = z.object({
  refreshToken: z.string().min(32).max(512),
  installationId: z.string().min(8).max(160),
});

export async function POST(request: NextRequest) {
  const input = schema.safeParse(await request.json().catch(() => null));
  if (!input.success) return fail(400, "invalid_input", "Refresh token is missing");
  const existing = await db.refreshToken.findUnique({
    where: { tokenHash: hashOpaqueToken(input.data.refreshToken) },
    include: { user: true, device: true },
  });
  if (
    !existing ||
    existing.revokedAt ||
    existing.expiresAt <= new Date() ||
    existing.user.status !== "ACTIVE" ||
    existing.device.revokedAt ||
    existing.device.installationId !== input.data.installationId ||
    existing.device.userId !== existing.userId
  ) {
    return fail(401, "invalid_refresh_token", "The refresh token is invalid or expired");
  }
  const next = createOpaqueToken();
  const nextExpiresAt = new Date(Date.now() + 30 * 86_400_000);
  const rotated = await db.$transaction(async (transaction) => {
    const revoked = await transaction.refreshToken.updateMany({
      where: { id: existing.id, revokedAt: null },
      data: { revokedAt: new Date() },
    });
    if (revoked.count !== 1) return false;
    await transaction.refreshToken.create({
      data: {
        userId: existing.userId,
        deviceId: existing.deviceId,
        tokenHash: next.hash,
        expiresAt: nextExpiresAt,
        rotatedFromId: existing.id,
      },
    });
    return true;
  });
  if (!rotated) return fail(401, "token_reused", "The refresh token was already used");
  return ok({
    accessToken: await issueToken(existing.user.id, existing.user.role),
    refreshToken: next.raw,
    expiresInSeconds: 900,
  });
}
