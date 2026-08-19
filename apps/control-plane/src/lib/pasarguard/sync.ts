import { createHash, randomBytes } from "node:crypto";
import { encryptConfig } from "@/lib/config-encryption";
import { db } from "@/lib/db";
import {
  PasarGuardError,
  type PasarGuardClient,
  type PasarGuardUser,
} from "@/lib/pasarguard/client";
import {
  createPasarGuardClient,
  createPasarGuardClientForProvider,
  isPasarGuardConfigured,
} from "@/lib/pasarguard/provider";
import {
  normalizePasarGuardConfig,
  type NormalizedPasarGuardConfig,
} from "@/lib/pasarguard/config";
import { pasarGuardLogicalNodeKey } from "@/lib/pasarguard/logical-node";

const UNLIMITED_QUOTA_BYTES = 100n * 1024n ** 4n;
const UNLIMITED_EXPIRY = new Date("2100-01-01T00:00:00.000Z");
const STALE_AFTER_MS = 5 * 60 * 1000;

export type BindPasarGuardOptions = {
  isFree?: boolean;
  planId?: string;
  planName?: string;
  serviceName?: string;
  license?: string;
  allowAdditionalBinding?: boolean;
  expectedQuotaBytes?: bigint;
};

function safeSyncError(error: unknown): string {
  if (error instanceof PasarGuardError) return error.message;
  return "همگام‌سازی پاسارگارد با خطای پیش‌بینی‌نشده متوقف شد";
}

function serviceState(user: PasarGuardUser, usageOffsetBytes = 0n, fallbackQuotaBytes?: bigint) {
  const now = Date.now();
  const expired = user.expiresAt ? user.expiresAt.getTime() <= now : false;
  const active = user.status.toLowerCase() === "active" && !expired;
  const remoteQuotaBytes = user.dataLimit ?? (user.usedTraffic > UNLIMITED_QUOTA_BYTES
    ? user.usedTraffic + 1024n ** 4n
    : UNLIMITED_QUOTA_BYTES);
  const quotaBytes = user.dataLimit === null && usageOffsetBytes > 0n && fallbackQuotaBytes !== undefined
    ? fallbackQuotaBytes
    : usageOffsetBytes + remoteQuotaBytes;
  return {
    status: expired ? "EXPIRED" as const : active ? "ACTIVE" as const : "SUSPENDED" as const,
    quotaBytes,
    usedBytes: usageOffsetBytes + user.usedTraffic,
    expiresAt: user.expiresAt ?? UNLIMITED_EXPIRY,
    maxDevices: user.maxDevices ?? 1,
  };
}

function nodeProviderKey(bindingId: string, tag: string): string {
  const tagHash = createHash("sha256").update(tag).digest("hex").slice(0, 24);
  return `pasarguard:${bindingId}:${tagHash}`;
}

async function applyPasarGuardSync(
  bindingId: string,
  user: PasarGuardUser,
  normalized: NormalizedPasarGuardConfig,
) {
  return db.$transaction(async (tx) => {
    const binding = await tx.pasarGuardBinding.findUnique({
      where: { id: bindingId },
      select: {
        id: true,
        serviceId: true,
        usageOffsetBytes: true,
        service: { select: { status: true, quotaBytes: true } },
      },
    });
    if (!binding) throw new PasarGuardError("invalid_response", "اتصال پاسارگارد در QuickPing پیدا نشد");
    const state = serviceState(user, binding.usageOffsetBytes, binding.service.quotaBytes);

    await tx.service.update({
      where: { id: binding.serviceId },
      data: {
        status: binding.service.status === "CANCELLED" && state.status === "SUSPENDED"
          ? "CANCELLED"
          : state.status,
        quotaBytes: state.quotaBytes,
        usedBytes: state.usedBytes,
        expiresAt: state.expiresAt,
        maxDevices: state.maxDevices,
      },
    });

    // PasarGuard returns a user-specific copy of the same physical/logical servers. Read the
    // existing global access policy once so new users inherit the same VIP classification instead
    // of silently creating a new STANDARD copy.
    const existingLogicalNodes = await tx.vpnNode.findMany({
      where: {
        provider: "PASARGUARD",
        providerTag: { in: normalized.nodes.map((node) => node.tag) },
      },
      select: {
        name: true,
        providerTag: true,
        host: true,
        port: true,
        protocol: true,
        accessTier: true,
      },
    });
    const logicalAccessTier = new Map<string, "STANDARD" | "VIP">();
    for (const node of existingLogicalNodes) {
      const key = pasarGuardLogicalNodeKey(node);
      const current = logicalAccessTier.get(key);
      if (!current || node.accessTier === "VIP") logicalAccessTier.set(key, node.accessTier);
    }

    const providerKeys: string[] = [];
    for (const [index, remoteNode] of normalized.nodes.entries()) {
      const providerKey = nodeProviderKey(binding.id, remoteNode.tag);
      providerKeys.push(providerKey);
      const region = await tx.serverRegion.upsert({
        where: { code: `pasarguard-${remoteNode.countryCode}` },
        update: {
          name: remoteNode.countryName,
          countryCode: remoteNode.countryCode,
          enabled: true,
        },
        create: {
          code: `pasarguard-${remoteNode.countryCode}`,
          name: remoteNode.countryName,
          countryCode: remoteNode.countryCode,
          priority: remoteNode.countryCode === "global" ? 0 : 10,
        },
      });
      const node = await tx.vpnNode.upsert({
        where: { providerKey },
        update: {
          regionId: region.id,
          name: remoteNode.name,
          host: remoteNode.host,
          port: remoteNode.port,
          protocol: remoteNode.protocol,
          configCiphertext: encryptConfig(remoteNode.runtimeConfig),
          configVersion: { increment: 1 },
          status: "ONLINE",
          lastSeenAt: new Date(),
          providerTag: remoteNode.tag,
          pasarGuardBindingId: binding.id,
        },
        create: {
          regionId: region.id,
          name: remoteNode.name,
          host: remoteNode.host,
          port: remoteNode.port,
          protocol: remoteNode.protocol,
          coreType: "sing-box",
          configCiphertext: encryptConfig(remoteNode.runtimeConfig),
          capacity: 1000,
          accessTier: logicalAccessTier.get(pasarGuardLogicalNodeKey(remoteNode)) ?? "STANDARD",
          status: "ONLINE",
          lastSeenAt: new Date(),
          provider: "PASARGUARD",
          providerKey,
          providerTag: remoteNode.tag,
          pasarGuardBindingId: binding.id,
        },
      });
      await tx.serviceNode.upsert({
        where: { serviceId_nodeId: { serviceId: binding.serviceId, nodeId: node.id } },
        update: { enabled: true, priority: normalized.nodes.length - index },
        create: {
          serviceId: binding.serviceId,
          nodeId: node.id,
          enabled: true,
          priority: normalized.nodes.length - index,
        },
      });
    }

    const obsoleteNodes = await tx.vpnNode.findMany({
      where: {
        pasarGuardBindingId: binding.id,
        providerKey: { notIn: providerKeys },
      },
      select: { id: true },
    });
    if (obsoleteNodes.length) {
      await tx.vpnNode.deleteMany({ where: { id: { in: obsoleteNodes.map((node) => node.id) } } });
    }

    return tx.pasarGuardBinding.update({
      where: { id: binding.id },
      data: {
        externalUsername: user.username,
        configFingerprint: normalized.fingerprint,
        lastSyncAt: new Date(),
        lastError: null,
      },
      include: {
        service: { include: { user: { select: { id: true, email: true } } } },
        nodes: {
          select: {
            id: true,
            name: true,
            host: true,
            port: true,
            protocol: true,
            coreType: true,
            status: true,
            lastSeenAt: true,
            region: { select: { id: true, code: true, name: true, countryCode: true } },
          },
          orderBy: { name: "asc" },
        },
      },
    });
  });
}

export async function bindPasarGuardUser(
  quickPingUserId: string,
  externalUserId: number,
  client: PasarGuardClient | undefined = undefined,
  options: BindPasarGuardOptions = {},
) {
  const resolvedClient = client ?? await createPasarGuardClient();
  const [user, rawConfig] = await Promise.all([
    resolvedClient.getUser(externalUserId),
    resolvedClient.getSingBoxConfig(externalUserId),
  ]);
  const state = serviceState(user);
  if (options.expectedQuotaBytes !== undefined && state.quotaBytes !== options.expectedQuotaBytes) {
    throw new PasarGuardError(
      "invalid_response",
      `حجم سرویس پاسارگارد باید دقیقاً ${options.expectedQuotaBytes.toString()} بایت باشد`,
    );
  }
  const normalized = normalizePasarGuardConfig(rawConfig);
  const quickPingUser = await db.user.findFirst({
    where: { id: quickPingUserId, status: "ACTIVE" },
    select: { id: true },
  });
  if (!quickPingUser) throw new PasarGuardError("invalid_response", "کاربر فعال QuickPing پیدا نشد");

  const existing = await db.pasarGuardBinding.findFirst({
    where: { providerId: resolvedClient.providerId, externalUserId: BigInt(user.id) },
    include: { service: { select: { userId: true } } },
  });
  if (existing && existing.service.userId !== quickPingUser.id) {
    throw new PasarGuardError("invalid_response", "این کاربر پاسارگارد قبلاً به حساب دیگری متصل شده است");
  }
  if (!options.allowAdditionalBinding) {
    const existingForQuickPingUser = await db.pasarGuardBinding.findFirst({
      where: { service: { userId: quickPingUser.id } },
      select: { id: true, externalUserId: true },
    });
    if (existingForQuickPingUser && existingForQuickPingUser.id !== existing?.id) {
      throw new PasarGuardError("invalid_response", "این حساب QuickPing قبلاً به یک اشتراک پاسارگارد متصل شده است");
    }
  }

  let bindingId = existing?.id;
  if (!bindingId) {
    const isFree = options.isFree ?? false;
    const binding = await db.$transaction(async (tx) => {
      const plan = options.planId
        ? await tx.plan.findFirst({ where: { id: options.planId, isActive: true } })
        : await tx.plan.upsert({
            where: { name: options.planName ?? "PasarGuard" },
            update: {
              interval: isFree ? "FREE" : "CUSTOM",
              price: 0,
              dataLimitBytes: state.quotaBytes,
              durationDays: 3650,
              maxDevices: state.maxDevices,
              isActive: true,
              isPublic: false,
            },
            create: {
              name: options.planName ?? "PasarGuard",
              interval: isFree ? "FREE" : "CUSTOM",
              price: 0,
              durationDays: 3650,
              dataLimitBytes: state.quotaBytes,
              maxDevices: state.maxDevices,
              isActive: true,
              isPublic: false,
            },
          });
      if (!plan) throw new PasarGuardError("invalid_response", "پلن انتخاب‌شده برای سرویس پاسارگارد فعال نیست");

      const service = await tx.service.create({
        data: {
          userId: quickPingUser.id,
          planId: plan.id,
          name: options.serviceName ?? user.username,
          license: options.license ?? `${isFree ? "FREE" : "PG"}-${randomBytes(12).toString("hex").toUpperCase()}`,
          status: state.status,
          quotaBytes: state.quotaBytes,
          usedBytes: state.usedBytes,
          expiresAt: state.expiresAt,
          maxDevices: state.maxDevices,
          isFree,
          guardianProfile: {
            create: {
              rules: {
                create: [
                  { category: "malware", enabled: true },
                  { category: "ads", enabled: true },
                  { category: "phishing", enabled: true },
                ],
              },
            },
          },
        },
      });
      return tx.pasarGuardBinding.create({
        data: {
          serviceId: service.id,
          providerId: resolvedClient.providerId,
          externalUserId: BigInt(user.id),
          externalUsername: user.username,
        },
      });
    });
    bindingId = binding.id;
  }
  return applyPasarGuardSync(bindingId, user, normalized);
}

export async function syncPasarGuardBinding(
  bindingId: string,
  client?: PasarGuardClient,
) {
  const binding = await db.pasarGuardBinding.findUnique({
    where: { id: bindingId },
    select: { id: true, externalUserId: true, providerId: true },
  });
  if (!binding) throw new PasarGuardError("invalid_response", "اتصال پاسارگارد پیدا نشد");
  const externalUserId = Number(binding.externalUserId);
  if (!Number.isSafeInteger(externalUserId)) {
    throw new PasarGuardError("invalid_response", "شناسهٔ کاربر پاسارگارد قابل استفاده نیست");
  }
  try {
    const resolvedClient = client ?? await createPasarGuardClientForProvider(binding.providerId);
    const [user, rawConfig] = await Promise.all([
      resolvedClient.getUser(externalUserId),
      resolvedClient.getSingBoxConfig(externalUserId),
    ]);
    return await applyPasarGuardSync(binding.id, user, normalizePasarGuardConfig(rawConfig));
  } catch (error) {
    await db.pasarGuardBinding.update({
      where: { id: binding.id },
      data: { lastError: safeSyncError(error) },
    }).catch(() => undefined);
    throw error;
  }
}

export async function refreshPasarGuardBindingsForUser(userId: string): Promise<void> {
  if (!await isPasarGuardConfigured()) return;
  try {
    // Creating the active client also imports a legacy ENV provider once when needed.
    const client = await createPasarGuardClient();
    if (!client.providerId) return;
    const staleBefore = new Date(Date.now() - STALE_AFTER_MS);
    const bindings = await db.pasarGuardBinding.findMany({
      where: {
        service: { userId },
        providerId: client.providerId,
        OR: [{ lastSyncAt: null }, { lastSyncAt: { lt: staleBefore } }],
      },
      select: { id: true },
    });
    if (!bindings.length) return;
    await Promise.allSettled(bindings.map((binding) => syncPasarGuardBinding(binding.id, client)));
  } catch {
    // A provider outage must not prevent the client from receiving its last known-good bootstrap.
  }
}
