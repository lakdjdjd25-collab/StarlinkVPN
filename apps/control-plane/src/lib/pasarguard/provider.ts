import { createHash } from "node:crypto";
import { decryptConfig, encryptConfig } from "@/lib/config-encryption";
import { db } from "@/lib/db";
import {
  isPasarGuardEnvConfigured,
  normalizePasarGuardBaseUrl,
  PasarGuardClient,
  PasarGuardError,
  pasarGuardCredentialsFromEnv,
  type PasarGuardGroup,
  type PasarGuardUserTemplate,
} from "@/lib/pasarguard/client";

export type PasarGuardProviderCredentials = {
  baseUrl: string;
  username: string;
  password: string;
};

export type PasarGuardProviderProfile = {
  key: string;
  kind: "template" | "group";
  id: number;
  name: string;
  groupIds: number[];
  dataLimit: bigint | null;
  expireDurationSeconds: number | null;
};

function sourceKey(baseUrl: URL, username: string): string {
  return createHash("sha256")
    .update(`${baseUrl.toString()}\n${username.trim().toLowerCase()}`)
    .digest("hex");
}

function safeProviderName(baseUrl: URL): string {
  return `PasarGuard — ${baseUrl.host}`.slice(0, 120);
}

function mapProfiles(templates: PasarGuardUserTemplate[], groups: PasarGuardGroup[]): PasarGuardProviderProfile[] {
  const profiles: PasarGuardProviderProfile[] = [];
  profiles.push(...templates
    .filter((template) => !template.isDisabled && template.status === "active" && template.groupIds.length > 0)
    .map((template) => ({
      key: `template:${template.id}`,
      kind: "template" as const,
      id: template.id,
      name: template.name,
      groupIds: template.groupIds,
      dataLimit: template.dataLimit,
      expireDurationSeconds: template.expireDurationSeconds,
    })));
  profiles.push(...groups.map((group) => ({
    key: `group:${group.id}`,
    kind: "group" as const,
    id: group.id,
    name: group.name,
    groupIds: [group.id],
    dataLimit: null,
    expireDurationSeconds: null,
  })));
  return profiles;
}

export async function discoverPasarGuardProfiles(client: PasarGuardClient): Promise<PasarGuardProviderProfile[]> {
  const [templatesResult, groupsResult] = await Promise.allSettled([
    client.listUserTemplates(),
    client.listGroups(),
  ]);
  if (templatesResult.status === "rejected" && groupsResult.status === "rejected") {
    throw groupsResult.reason ?? templatesResult.reason;
  }
  return mapProfiles(
    templatesResult.status === "fulfilled" ? templatesResult.value : [],
    groupsResult.status === "fulfilled" ? groupsResult.value : [],
  );
}

async function importEnvironmentProvider() {
  const env = pasarGuardCredentialsFromEnv();
  const key = sourceKey(env.baseUrl, env.username);
  const encrypted = encryptConfig({ password: env.password });
  return db.$transaction(async (tx) => {
    await tx.pasarGuardProvider.updateMany({ where: { active: true }, data: { active: false } });
    const provider = await tx.pasarGuardProvider.upsert({
      where: { sourceKey: key },
      update: {
        baseUrl: env.baseUrl.toString(),
        username: env.username,
        passwordCiphertext: encrypted,
        active: true,
        lastError: null,
      },
      create: {
        sourceKey: key,
        name: safeProviderName(env.baseUrl),
        baseUrl: env.baseUrl.toString(),
        username: env.username,
        passwordCiphertext: encrypted,
        active: true,
      },
    });
    // Existing production bindings were created against the current ENV provider.
    // Attach them once so future provider switches never silently repoint them.
    await tx.pasarGuardBinding.updateMany({ where: { providerId: null }, data: { providerId: provider.id } });
    return provider;
  });
}

async function activeProviderRecord() {
  const active = await db.pasarGuardProvider.findFirst({
    where: { active: true },
    orderBy: { updatedAt: "desc" },
  });
  if (active) return active;
  if (!isPasarGuardEnvConfigured()) {
    throw new PasarGuardError("not_configured", "اطلاعات اتصال پاسارگارد تنظیم نشده است");
  }
  return importEnvironmentProvider();
}

function clientFromRecord(provider: {
  id: string;
  baseUrl: string;
  username: string;
  passwordCiphertext: string;
}): PasarGuardClient {
  let password: string;
  try {
    const value = decryptConfig<{ password?: unknown }>(provider.passwordCiphertext);
    if (typeof value.password !== "string" || !value.password) throw new Error("invalid password envelope");
    password = value.password;
  } catch {
    throw new PasarGuardError("not_configured", "رمز ذخیره‌شده پاسارگارد قابل خواندن نیست");
  }
  return new PasarGuardClient({
    baseUrl: normalizePasarGuardBaseUrl(provider.baseUrl),
    username: provider.username,
    password,
    providerId: provider.id,
  });
}

export async function isPasarGuardConfigured(): Promise<boolean> {
  if (await db.pasarGuardProvider.findFirst({ where: { active: true }, select: { id: true } })) return true;
  return isPasarGuardEnvConfigured();
}

export async function createPasarGuardClient(): Promise<PasarGuardClient> {
  return clientFromRecord(await activeProviderRecord());
}

export async function createPasarGuardClientForProvider(providerId: string | null): Promise<PasarGuardClient> {
  if (!providerId) return createPasarGuardClient();
  const provider = await db.pasarGuardProvider.findUnique({ where: { id: providerId } });
  if (!provider) throw new PasarGuardError("not_configured", "پنل پاسارگارد مربوط به این سرویس دیگر در دسترس نیست");
  return clientFromRecord(provider);
}

export async function activePasarGuardProviderSummary() {
  try {
    const provider = await activeProviderRecord();
    return {
      id: provider.id,
      name: provider.name,
      baseUrl: provider.baseUrl,
      username: provider.username,
      active: provider.active,
      lastTestAt: provider.lastTestAt,
      lastSyncAt: provider.lastSyncAt,
      lastError: provider.lastError,
    };
  } catch (error) {
    if (error instanceof PasarGuardError && error.code === "not_configured") return null;
    throw error;
  }
}

export async function testPasarGuardProvider(credentials: PasarGuardProviderCredentials) {
  const baseUrl = normalizePasarGuardBaseUrl(credentials.baseUrl);
  const username = credentials.username.trim();
  if (!username || !credentials.password) throw new PasarGuardError("not_configured", "نام کاربری و رمز پاسارگارد لازم است");
  const client = new PasarGuardClient({ baseUrl, username, password: credentials.password });
  const profiles = await discoverPasarGuardProfiles(client);
  if (!profiles.length) {
    throw new PasarGuardError("invalid_response", "اتصال برقرار شد اما هیچ گروه یا قالب قابل استفاده‌ای در پنل پیدا نشد");
  }
  // Listing users proves this administrator can perform the core user-management path too.
  const users = await client.listUsers();
  return {
    client,
    baseUrl,
    username,
    profiles,
    userCount: users.length,
  };
}

export async function activatePasarGuardProvider(credentials: PasarGuardProviderCredentials) {
  const tested = await testPasarGuardProvider(credentials);
  const now = new Date();
  const key = sourceKey(tested.baseUrl, tested.username);
  const passwordCiphertext = encryptConfig({ password: credentials.password });
  const provider = await db.$transaction(async (tx) => {
    await tx.pasarGuardProvider.updateMany({ where: { active: true }, data: { active: false } });
    return tx.pasarGuardProvider.upsert({
      where: { sourceKey: key },
      update: {
        name: safeProviderName(tested.baseUrl),
        baseUrl: tested.baseUrl.toString(),
        username: tested.username,
        passwordCiphertext,
        active: true,
        lastTestAt: now,
        lastSyncAt: now,
        lastError: null,
      },
      create: {
        sourceKey: key,
        name: safeProviderName(tested.baseUrl),
        baseUrl: tested.baseUrl.toString(),
        username: tested.username,
        passwordCiphertext,
        active: true,
        lastTestAt: now,
        lastSyncAt: now,
      },
    });
  });
  // Existing bindings stay on their provider. Their mappings are intentionally not copied.
  await db.pasarGuardPlanMapping.updateMany({
    where: { providerId: provider.id },
    data: { valid: false },
  });
  return {
    provider: {
      id: provider.id,
      name: provider.name,
      baseUrl: provider.baseUrl,
      username: provider.username,
      lastTestAt: provider.lastTestAt,
      lastSyncAt: provider.lastSyncAt,
    },
    profiles: tested.profiles,
    userCount: tested.userCount,
  };
}

export async function syncActivePasarGuardProfiles() {
  const provider = await activeProviderRecord();
  const client = clientFromRecord(provider);
  try {
    const profiles = await discoverPasarGuardProfiles(client);
    const validKeys = new Set(profiles.map((item) => item.key));
    const mappings = await db.pasarGuardPlanMapping.findMany({ where: { providerId: provider.id } });
    await Promise.all(mappings.map((mapping) => db.pasarGuardPlanMapping.update({
      where: { id: mapping.id },
      data: {
        valid: validKeys.has(mapping.profileKey),
        lastValidatedAt: new Date(),
      },
    })));
    await db.pasarGuardProvider.update({
      where: { id: provider.id },
      data: { lastSyncAt: new Date(), lastError: null },
    });
    return { providerId: provider.id, profiles };
  } catch (error) {
    await db.pasarGuardProvider.update({
      where: { id: provider.id },
      data: { lastError: error instanceof Error ? error.message.slice(0, 500) : "sync_failed" },
    }).catch(() => undefined);
    throw error;
  }
}

export async function savePasarGuardPlanMapping(planId: string, profileKey: string) {
  const { providerId, profiles } = await syncActivePasarGuardProfiles();
  const profile = profiles.find((item) => item.key === profileKey);
  if (!profile) throw new PasarGuardError("invalid_response", "گروه یا قالب انتخاب‌شده در پنل فعال وجود ندارد");
  return db.pasarGuardPlanMapping.upsert({
    where: { providerId_planId: { providerId, planId } },
    update: {
      profileKey: profile.key,
      profileName: profile.name,
      groupIds: profile.groupIds,
      valid: true,
      lastValidatedAt: new Date(),
    },
    create: {
      providerId,
      planId,
      profileKey: profile.key,
      profileName: profile.name,
      groupIds: profile.groupIds,
      valid: true,
      lastValidatedAt: new Date(),
    },
  });
}
