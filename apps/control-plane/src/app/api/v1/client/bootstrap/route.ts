import type { NextRequest } from "next/server";
import { fail, ok, requireBearer } from "@/lib/api";
import { db } from "@/lib/db";
import { refreshPasarGuardBindingsForUser } from "@/lib/pasarguard/sync";

export async function GET(request: NextRequest) {
  const auth = await requireBearer(request);
  if (!auth.ok) return auth.response;
  await refreshPasarGuardBindingsForUser(auth.userId);
  const user = await db.user.findUnique({
    where: { id: auth.userId },
    include: {
      services: {
        where: { status: "ACTIVE", expiresAt: { gt: new Date() } },
        orderBy: { expiresAt: "desc" },
        include: {
          plan: true,
          nodes: {
            where: {
              enabled: true,
              node: {
                status: { in: ["ONLINE", "DEGRADED"] },
                region: { enabled: true },
              },
            },
            include: { node: { include: { region: true } } },
            orderBy: { priority: "desc" },
          },
          guardianProfile: { include: { rules: true } },
        },
      },
    },
  });
  if (!user || user.status !== "ACTIVE") {
    return fail(403, "account_unavailable", "The account is unavailable");
  }
  const hasPaidService = user.services.some((service) => !service.isFree);
  const [settings, release, notifications] = await Promise.all([
    db.globalSetting.findUnique({ where: { key: "client.bootstrap" } }),
    db.appRelease.findFirst({
      where: { platform: "ANDROID", publishedAt: { not: null } },
      orderBy: { versionCode: "desc" },
    }),
    db.notification.findMany({
      where: {
        publishedAt: { lte: new Date() },
        AND: [
          { OR: [{ expiresAt: null }, { expiresAt: { gt: new Date() } }] },
          {
            OR: [
              { audience: { in: ["ALL", hasPaidService ? "PAID" : "FREE"] } },
              { audience: "SELECTED", deliveries: { some: { userId: user.id } } },
            ],
          },
        ],
      },
      include: {
        deliveries: {
          where: { userId: user.id },
          select: { readAt: true, deliveredAt: true },
        },
      },
      orderBy: { publishedAt: "desc" },
      take: 30,
    }),
  ]);

  return ok({
    user: {
      id: user.id,
      email: user.email,
      emailVerified: Boolean(user.emailVerifiedAt),
      telegramBound: Boolean(user.telegramId),
      balance: user.balance,
      language: user.language,
    },
    services: user.services.map((service) => ({
      id: service.id,
      name: service.name,
      plan: service.plan.name,
      license: service.license,
      size: service.quotaBytes,
      usedSize: service.usedBytes,
      remainSize: service.quotaBytes > service.usedBytes
        ? service.quotaBytes - service.usedBytes
        : 0n,
      expiryTime: service.expiresAt,
      usersCount: service.maxDevices,
      isFree: service.isFree,
      autoPay: service.autoPay,
      servers: service.nodes.map(({ node }) => ({
        id: node.id,
        location: node.region.name,
        countryCode: node.region.countryCode,
        remarks: node.name,
        host: node.host,
        port: node.port,
        coreType: node.coreType,
        freeAllowed: node.freeAllowed,
        unmetered: node.unmetered,
        status: node.status,
      })),
      guardian: service.guardianProfile,
    })),
    global: settings?.value ?? {},
    release,
    notifications: notifications.map(({ deliveries, ...notification }) => ({
      ...notification,
      read: Boolean(deliveries[0]?.readAt),
      delivered: Boolean(deliveries[0]?.deliveredAt),
    })),
  });
}
