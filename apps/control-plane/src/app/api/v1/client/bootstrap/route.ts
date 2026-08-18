import type { NextRequest } from "next/server";
import { fail, ok, requireBearer } from "@/lib/api";
import { db } from "@/lib/db";
import { refreshPasarGuardBindingsForUser } from "@/lib/pasarguard/sync";
import { effectiveUsedBytes, remainingServiceBytes, serverAccessState } from "@/lib/server-access";

export async function GET(request: NextRequest) {
  const auth = await requireBearer(request);
  if (!auth.ok) return auth.response;
  await refreshPasarGuardBindingsForUser(auth.userId);
  const user = await db.user.findUnique({
    where: { id: auth.userId },
    include: {
      services: {
        where: {
          ...(auth.serviceId ? { id: auth.serviceId } : {}),
          status: { in: ["ACTIVE", "SUSPENDED"] },
          expiresAt: { gt: new Date() },
        },
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
          pasarGuardBinding: { select: { providerId: true, lastSyncAt: true } },
        },
      },
    },
  });
  if (!user || user.status === "DELETED") {
    return fail(403, "account_unavailable", "The account is unavailable");
  }
  const visibleServices = user.services.filter((service) =>
    user.status === "SUSPENDED" ? service.status === "SUSPENDED" : service.status === "ACTIVE",
  );
  if (auth.serviceId && visibleServices.length !== 1) {
    return fail(403, "license_unavailable", "The licensed service is no longer available");
  }
  const [settings, management, activeProvider, release, notifications, manualServers] = await Promise.all([
    db.globalSetting.findUnique({ where: { key: "client.bootstrap" } }),
    db.globalSetting.findUnique({ where: { key: "client.management" } }),
    db.pasarGuardProvider.findFirst({ where: { active: true }, select: { id: true } }),
    db.appRelease.findFirst({
      where: { platform: "ANDROID", publishedAt: { not: null } },
      orderBy: { versionCode: "desc" },
    }),
    db.notification.findMany({
      where: {
        publishedAt: { lte: new Date() },
        AND: [
          { OR: [{ expiresAt: null }, { expiresAt: { gt: new Date() } }] },
          { deliveries: { some: { userId: user.id } } },
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
    db.manualServer.findMany({
      where: { enabled: true, deletedAt: null },
      select: {
        id: true,
        displayName: true,
        host: true,
        port: true,
        country: true,
        countryCode: true,
        countryOverride: true,
        category: true,
        accessTier: true,
        enabled: true,
        sortOrder: true,
        countTraffic: true,
      },
      orderBy: [{ sortOrder: "asc" }, { createdAt: "asc" }],
    }),
  ]);

  const undeliveredIds = notifications
    .filter((notification) => !notification.deliveries[0]?.deliveredAt)
    .map((notification) => notification.id);
  if (undeliveredIds.length) {
    await db.notificationDelivery.updateMany({
      where: { userId: user.id, notificationId: { in: undeliveredIds }, deliveredAt: null },
      data: { deliveredAt: new Date() },
    });
  }

  return ok({
    user: {
      id: user.id,
      email: user.email,
      emailVerified: Boolean(user.emailVerifiedAt),
      telegramBound: Boolean(user.telegramId),
      balance: user.balance,
      language: user.language,
      status: user.status,
    },
    services: visibleServices.map((service) => {
      const providerReview = Boolean(
        service.pasarGuardBinding
        && (!activeProvider
          || service.pasarGuardBinding.providerId !== activeProvider.id
          || !service.pasarGuardBinding.lastSyncAt),
      );
      // Preserve the original VIP privacy contract for managed nodes: a STANDARD service never
      // receives managed VIP node metadata. Manual VIP entries are the only visible-but-locked
      // entries because that behavior is required by the Manual Server list UX.
      const managedServers = service.nodes
        .filter(({ node }) => service.vipAccess || node.accessTier !== "VIP")
        .map(({ node }) => {
          const access = serverAccessState(service.vipAccess, node.accessTier);
          return {
            id: node.id,
            location: node.region.name,
            countryCode: node.region.countryCode,
            remarks: node.name,
            host: node.host,
            port: node.port,
            coreType: node.coreType,
            freeAllowed: node.freeAllowed,
            unmetered: node.unmetered,
            accessTier: node.accessTier,
            status: node.status,
            category: null,
            serverType: "MANAGED",
            countTraffic: false,
            ...access,
          };
        });
      const dynamicManualServers = manualServers.map((node) => {
        const access = serverAccessState(service.vipAccess, node.accessTier);
        const country = node.countryOverride || node.country || "Unknown";
        return {
          id: node.id,
          location: country,
          countryCode: node.countryCode || "global",
          remarks: node.displayName,
          host: access.canConnect ? node.host : "",
          port: access.canConnect ? node.port : 0,
          coreType: "sing-box",
          freeAllowed: false,
          unmetered: false,
          accessTier: node.accessTier,
          status: "ONLINE",
          category: node.category,
          serverType: "MANUAL",
          countTraffic: node.countTraffic,
          sortOrder: node.sortOrder,
          ...access,
        };
      });
      const servers = user.status === "SUSPENDED" || providerReview
        ? []
        : [...managedServers, ...dynamicManualServers];
      return {
        id: service.id,
        name: service.name,
        plan: service.plan.name,
        license: service.license,
        size: service.quotaBytes,
        usedSize: effectiveUsedBytes(service),
        remainSize: remainingServiceBytes(service),
        expiryTime: service.expiresAt,
        usersCount: service.maxDevices,
        isFree: service.isFree,
        autoPay: service.autoPay,
        vipAccess: service.vipAccess,
        providerState: providerReview ? "REVIEW" : "READY",
        servers,
        guardian: service.guardianProfile,
      };
    }),
    global: settings?.value ?? {},
    management: management?.value ?? { telegramUsername: "Folwn" },
    release,
    notifications: notifications.map(({ deliveries, ...notification }) => ({
      ...notification,
      read: Boolean(deliveries[0]?.readAt),
      delivered: Boolean(deliveries[0]?.deliveredAt),
    })),
  });
}
