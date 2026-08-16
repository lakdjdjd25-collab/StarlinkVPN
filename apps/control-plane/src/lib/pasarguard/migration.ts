import { db } from "@/lib/db";
import { PasarGuardError, type PasarGuardClient, type PasarGuardUser } from "@/lib/pasarguard/client";
import {
  createPasarGuardClient,
  discoverPasarGuardProfiles,
} from "@/lib/pasarguard/provider";
import { syncPasarGuardBinding } from "@/lib/pasarguard/sync";

export type MigrationTransferShape = {
  usageOffsetBytes: bigint;
  remoteDataLimitBytes: bigint;
  remainingBytes: bigint;
};

export function migrationTransferShape(
  totalBytes: bigint,
  usedBytes: bigint,
  remoteUsedBytes: bigint,
): MigrationTransferShape {
  if (totalBytes <= 0n || usedBytes < 0n || remoteUsedBytes < 0n) {
    throw new PasarGuardError("invalid_response", "وضعیت حجم سرویس برای انتقال معتبر نیست");
  }
  if (usedBytes >= totalBytes) {
    throw new PasarGuardError("invalid_response", "حجم این سرویس تمام شده است؛ پس از افزایش حجم می‌توان آن را منتقل کرد");
  }
  if (remoteUsedBytes > usedBytes) {
    throw new PasarGuardError(
      "invalid_response",
      "کاربر هم‌نام در پنل جدید مصرف بیشتری از سابقه NimHUB دارد و نیازمند بررسی دستی است",
    );
  }
  const remainingBytes = totalBytes - usedBytes;
  const usageOffsetBytes = usedBytes - remoteUsedBytes;
  const remoteDataLimitBytes = totalBytes - usageOffsetBytes;
  if (remoteDataLimitBytes <= remoteUsedBytes) {
    throw new PasarGuardError("invalid_response", "حجم باقی‌مانده برای انتقال معتبر نیست");
  }
  return { usageOffsetBytes, remoteDataLimitBytes, remainingBytes };
}

function mappingGroups(value: unknown): number[] {
  if (!Array.isArray(value)) return [];
  return [...new Set(value
    .map((item) => Number(item))
    .filter((item) => Number.isSafeInteger(item) && item > 0))]
    .sort((a, b) => a - b);
}

type MigrationOptions = {
  client?: PasarGuardClient;
  profileKey?: string;
  visibleUsers?: PasarGuardUser[];
};

export async function migratePasarGuardBindingToActiveProvider(
  bindingId: string,
  options: MigrationOptions = {},
) {
  const client = options.client ?? await createPasarGuardClient();
  if (!client.providerId) throw new PasarGuardError("not_configured", "Provider فعال پاسارگارد شناسه معتبر ندارد");

  const binding = await db.pasarGuardBinding.findUnique({
    where: { id: bindingId },
    include: {
      service: {
        select: {
          id: true,
          planId: true,
          status: true,
          quotaBytes: true,
          usedBytes: true,
          expiresAt: true,
          maxDevices: true,
        },
      },
    },
  });
  if (!binding) throw new PasarGuardError("invalid_response", "اتصال سرویس پاسارگارد پیدا نشد");
  if (binding.providerId === client.providerId && binding.lastSyncAt) {
    return {
      migrated: false,
      alreadyActiveProvider: true,
      bindingId: binding.id,
      serviceId: binding.service.id,
      previousProviderId: binding.providerId,
      providerId: client.providerId,
      externalUserId: binding.externalUserId,
      profileKey: options.profileKey ?? null,
      usageOffsetBytes: binding.usageOffsetBytes,
      remoteUser: null as PasarGuardUser | null,
    };
  }

  let profileKey = options.profileKey;
  let profileName = "";
  let groupIds: number[] = [];
  if (profileKey) {
    const profiles = await discoverPasarGuardProfiles(client);
    const profile = profiles.find((item) => item.key === profileKey);
    if (!profile || !profile.groupIds.length) {
      throw new PasarGuardError("invalid_response", "گروه یا قالب انتخاب‌شده در پنل فعال وجود ندارد");
    }
    profileName = profile.name;
    groupIds = profile.groupIds;
    await db.pasarGuardPlanMapping.upsert({
      where: { providerId_planId: { providerId: client.providerId, planId: binding.service.planId } },
      update: {
        profileKey: profile.key,
        profileName: profile.name,
        groupIds: profile.groupIds,
        valid: true,
        lastValidatedAt: new Date(),
      },
      create: {
        providerId: client.providerId,
        planId: binding.service.planId,
        profileKey: profile.key,
        profileName: profile.name,
        groupIds: profile.groupIds,
        valid: true,
        lastValidatedAt: new Date(),
      },
    });
  } else {
    const mapping = await db.pasarGuardPlanMapping.findUnique({
      where: { providerId_planId: { providerId: client.providerId, planId: binding.service.planId } },
    });
    if (!mapping?.valid) {
      throw new PasarGuardError("invalid_response", "برای پلن این سرویس هنوز گروه پنل فعال انتخاب نشده است");
    }
    groupIds = mappingGroups(mapping.groupIds);
    if (!groupIds.length) throw new PasarGuardError("invalid_response", "گروه ثبت‌شده برای پلن معتبر نیست");
    profileKey = mapping.profileKey;
    profileName = mapping.profileName;
  }

  const visibleUsers = options.visibleUsers ?? await client.listUsers();
  const summary = visibleUsers.find((item) => item.username.toLowerCase() === binding.externalUsername.toLowerCase());
  let remote = summary ? await client.getUserByUsername(summary.username) : null;
  const remoteSnapshot = remote;
  let createdRemote = false;

  if (remote) {
    const conflicting = await db.pasarGuardBinding.findFirst({
      where: {
        providerId: client.providerId,
        externalUserId: BigInt(remote.id),
        id: { not: binding.id },
      },
      select: { id: true },
    });
    if (conflicting) {
      throw new PasarGuardError("invalid_response", "این کاربر در پنل جدید قبلاً به سرویس دیگری متصل شده است");
    }
  }

  const transfer = migrationTransferShape(
    binding.service.quotaBytes,
    binding.service.usedBytes,
    remote?.usedTraffic ?? 0n,
  );

  if (remote) {
    remote = await client.updateUser(remote.username, {
      dataLimit: transfer.remoteDataLimitBytes,
      expiresAt: binding.service.expiresAt,
      maxDevices: binding.service.maxDevices,
      status: binding.service.status === "ACTIVE" ? "active" : "disabled",
      groupIds,
      note: `NimHUB provider migration (${profileName})`,
    });
  } else {
    remote = await client.createUser(
      binding.externalUsername,
      transfer.remoteDataLimitBytes,
      groupIds,
      `NimHUB provider migration (${profileName})`,
      binding.service.maxDevices,
      binding.service.expiresAt,
    );
    createdRemote = true;
    if (binding.service.status !== "ACTIVE") {
      remote = await client.updateUser(remote.username, { status: "disabled" });
    }
  }

  try {
    await db.pasarGuardBinding.update({
      where: { id: binding.id },
      data: {
        providerId: client.providerId,
        externalUserId: BigInt(remote.id),
        externalUsername: remote.username,
        usageOffsetBytes: transfer.usageOffsetBytes,
        lastSyncAt: null,
        lastError: null,
      },
    });
    await syncPasarGuardBinding(binding.id, client);
  } catch (error) {
    await db.pasarGuardBinding.update({
      where: { id: binding.id },
      data: {
        providerId: binding.providerId,
        externalUserId: binding.externalUserId,
        externalUsername: binding.externalUsername,
        usageOffsetBytes: binding.usageOffsetBytes,
        configFingerprint: binding.configFingerprint,
        lastSyncAt: binding.lastSyncAt,
        lastError: binding.lastError,
      },
    }).catch(() => undefined);
    if (createdRemote) {
      await client.deleteUser(remote.username).catch(() => undefined);
    } else if (remoteSnapshot) {
      await client.updateUser(remoteSnapshot.username, {
        dataLimit: remoteSnapshot.dataLimit ?? binding.service.quotaBytes,
        expiresAt: remoteSnapshot.expiresAt,
        maxDevices: remoteSnapshot.maxDevices ?? binding.service.maxDevices,
        status: remoteSnapshot.status,
        ...(remoteSnapshot.groupIds.length ? { groupIds: remoteSnapshot.groupIds } : {}),
      }).catch(() => undefined);
    }
    throw error;
  }

  return {
    migrated: true,
    alreadyActiveProvider: false,
    bindingId: binding.id,
    serviceId: binding.service.id,
    previousProviderId: binding.providerId,
    providerId: client.providerId,
    externalUserId: BigInt(remote.id),
    profileKey,
    usageOffsetBytes: transfer.usageOffsetBytes,
    remainingBytes: transfer.remainingBytes,
    remoteUser: remote,
  };
}
