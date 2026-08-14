import type { NextRequest } from "next/server";
import { z } from "zod";
import { hashOpaqueToken } from "@/lib/auth";
import { ok } from "@/lib/api";
import { db } from "@/lib/db";

const schema = z.object({
  refreshToken: z.string().min(32).max(512),
  installationId: z.string().min(8).max(160),
});

/**
 * Sign-out is deliberately idempotent and does not reveal whether a supplied
 * refresh token existed. When the token belongs to the current installation,
 * revoke every still-active refresh token for that device and mark the device
 * revoked. Existing short-lived access tokens expire naturally, while the
 * session can no longer be refreshed.
 */
export async function POST(request: NextRequest) {
  const input = schema.safeParse(await request.json().catch(() => null));
  if (!input.success) return ok({ signedOut: true });

  const existing = await db.refreshToken.findUnique({
    where: { tokenHash: hashOpaqueToken(input.data.refreshToken) },
    include: { device: true },
  });
  if (!existing || existing.device.installationId !== input.data.installationId) {
    return ok({ signedOut: true });
  }

  const now = new Date();
  await db.$transaction(async (transaction) => {
    await transaction.refreshToken.updateMany({
      where: { deviceId: existing.deviceId, revokedAt: null },
      data: { revokedAt: now },
    });
    await transaction.device.updateMany({
      where: { id: existing.deviceId, revokedAt: null },
      data: { revokedAt: now },
    });
  });

  return ok({ signedOut: true });
}
