import type { NextRequest } from "next/server";
import { z } from "zod";
import { createOpaqueToken, issueToken, verifyPassword } from "@/lib/auth";
import { fail, ok } from "@/lib/api";
import { db } from "@/lib/db";
import { normalizeLicense } from "@/lib/license";
import { syncPasarGuardBinding } from "@/lib/pasarguard/sync";

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

  const rawCredential = input.data.email.trim();
  const credential = rawCredential.includes("@")
    ? rawCredential
    : normalizeLicense(rawCredential.replace(/^NIMHUB\s*:/i, ""));
  const emailLogin = credential.includes("@");
  let service = emailLogin
    ? null
    : await db.service.findFirst({
        where: { license: { equals: credential, mode: "insensitive" } },
        include: { user: true, pasarGuardBinding: { select: { id: true } } },
      });
  if (service?.pasarGuardBinding) {
    // License login is the point where current quota/expiry/device limits matter
    // most. Refresh this exact binding first, while retaining the last known-good
    // state if the provider is temporarily unreachable.
    await syncPasarGuardBinding(service.pasarGuardBinding.id).catch(() => undefined);
    service = await db.service.findUnique({
      where: { id: service.id },
      include: { user: true, pasarGuardBinding: { select: { id: true } } },
    });
  }
  let user = emailLogin
    ? await db.user.findUnique({ where: { email: credential.toLowerCase() } })
    : service?.user ?? null;

  if (!user || user.status !== "ACTIVE") {
    return fail(
      401,
      emailLogin ? "invalid_credentials" : "invalid_license",
      emailLogin ? "Email or password is incorrect" : "License is invalid or inactive",
    );
  }

  if (emailLogin) {
    if (!user.passwordHash || !(await verifyPassword(input.data.password, user.passwordHash))) {
      return fail(401, "invalid_credentials", "Email or password is incorrect");
    }
    if (user.managedAccount) {
      service = await db.service.findFirst({
        where: {
          userId: user.id,
          pasarGuardBinding: { isNot: null },
        },
        orderBy: { createdAt: "desc" },
        include: { user: true, pasarGuardBinding: { select: { id: true } } },
      });
      if (service?.pasarGuardBinding) {
        await syncPasarGuardBinding(service.pasarGuardBinding.id).catch(() => undefined);
        service = await db.service.findUnique({
          where: { id: service.id },
          include: { user: true, pasarGuardBinding: { select: { id: true } } },
        });
        user = service?.user ?? user;
      }
    }
  }
  if ((!emailLogin || user.managedAccount) && (
    !service ||
    service.status !== "ACTIVE" ||
    service.expiresAt.getTime() <= Date.now() ||
    service.usedBytes >= service.quotaBytes
  )) {
    return fail(
      401,
      emailLogin ? "service_unavailable" : "invalid_license",
      emailLogin
        ? "The managed service is inactive, expired, or has no remaining quota"
        : "License is invalid, expired, or has no remaining quota",
    );
  }

  if (service) {
    const now = new Date();
    const [existingInstallation, activeDeviceCount] = await Promise.all([
      db.device.findUnique({
        where: { installationId: input.data.installationId },
        select: { userId: true, revokedAt: true },
      }),
      db.device.count({
        where: {
          userId: user.id,
          revokedAt: null,
          refreshTokens: {
            some: {
              revokedAt: null,
              expiresAt: { gt: now },
            },
          },
        },
      }),
    ]);
    const sameActiveInstallation = existingInstallation?.userId === user.id && !existingInstallation.revokedAt;
    if (!sameActiveInstallation && activeDeviceCount >= service.maxDevices) {
      return fail(
        409,
        "device_limit_reached",
        `This license allows up to ${service.maxDevices} active device${service.maxDevices === 1 ? "" : "s"}`,
      );
    }
  }

  const opaque = createOpaqueToken(service?.id);
  const expiresAt = new Date(Date.now() + 30 * 86_400_000);
  const accessToken = await issueToken(user.id, user.role, "access", service?.id);
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
