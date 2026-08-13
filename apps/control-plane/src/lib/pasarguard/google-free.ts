import { createHash } from "node:crypto";
import { db } from "@/lib/db";
import {
  createPasarGuardClient,
  PasarGuardError,
  type PasarGuardClient,
  type PasarGuardUser,
  type PasarGuardUserTemplate,
} from "@/lib/pasarguard/client";
import { bindPasarGuardUser, syncPasarGuardBinding } from "@/lib/pasarguard/sync";

export const GOOGLE_FREE_QUOTA_BYTES = 10n * 1024n ** 3n;
export const GOOGLE_FREE_PLAN_NAME = "Google Free 10GB";

function configuredTemplateId(): number | null {
  const raw = process.env.PASARGUARD_FREE_TEMPLATE_ID?.trim();
  if (!raw) return null;
  const value = Number(raw);
  if (!Number.isSafeInteger(value) || value <= 0) {
    throw new PasarGuardError("not_configured", "شناسه قالب سرویس رایگان پاسارگارد معتبر نیست");
  }
  return value;
}

function eligible(template: PasarGuardUserTemplate): boolean {
  return template.dataLimit === GOOGLE_FREE_QUOTA_BYTES
    && !template.isDisabled
    && template.status === "active"
    && template.resetStrategy === "no_reset";
}

function chooseTemplate(templates: PasarGuardUserTemplate[]): number | null {
  const items = templates.filter(eligible);
  if (items.length === 1) return items[0].id;
  const preferred = items
    .filter((item) => /google|free|10\s?gb|گوگل|رایگان/i.test(item.name))
    .sort((a, b) => a.id - b.id);
  return preferred.length === 1 ? preferred[0].id : null;
}

async function optionalTemplateId(client: PasarGuardClient): Promise<number | null> {
  return configuredTemplateId() ?? chooseTemplate(await client.listUserTemplates());
}

export async function resolveGoogleFreeTemplateId(client: PasarGuardClient): Promise<number> {
  const id = await optionalTemplateId(client);
  if (!id) throw new PasarGuardError("not_configured", "قالب 10GB قابل انتخابی در پاسارگارد وجود ندارد");
  return id;
}

export function googleFreeUsername(googleSubject: string): string {
  return `g_${createHash("sha256").update(googleSubject).digest("hex").slice(0, 24)}`;
}

function groupIdsFromVisibleUsers(users: PasarGuardUser[]): number[] {
  const active = users.filter((user) => user.status.toLowerCase() === "active" && user.groupIds.length > 0);
  const source = active.length > 0 ? active : users.filter((user) => user.groupIds.length > 0);
  const ids = [...new Set(source.flatMap((user) => user.groupIds))].sort((a, b) => a - b);
  if (ids.length === 0) {
    throw new PasarGuardError("not_configured", "از کاربران فعلی پاسارگارد هیچ گروه سروری برای سرویس رایگان پیدا نشد");
  }
  return ids;
}

export async function preflightGoogleFreeProvisioning(
  client: PasarGuardClient = createPasarGuardClient(),
): Promise<void> {
  groupIdsFromVisibleUsers(await client.listUsers());
}

function assertFreeUser(user: PasarGuardUser): void {
  if (user.dataLimit !== GOOGLE_FREE_QUOTA_BYTES) {
    throw new PasarGuardError("invalid_response", "حجم سرویس رایگان پاسارگارد دقیقاً 10GB نیست");
  }
  if (user.status.toLowerCase() !== "active") {
    throw new PasarGuardError("invalid_response", "سرویس رایگان پاسارگارد فعال نیست");
  }
  if (user.expiresAt && user.expiresAt.getTime() <= Date.now()) {
    throw new PasarGuardError("invalid_response", "سرویس رایگان پاسارگارد منقضی است");
  }
}

function findRemote(users: PasarGuardUser[], username: string): PasarGuardUser | null {
  const matches = users.filter((user) => user.username === username || user.username.includes(username));
  if (matches.length > 1) throw new PasarGuardError("invalid_response", "بیش از یک سرویس رایگان متناظر در پاسارگارد پیدا شد");
  return matches[0] ?? null;
}

async function createRemote(
  client: PasarGuardClient,
  username: string,
  visibleUsers: PasarGuardUser[],
): Promise<PasarGuardUser> {
  const note = "QuickPing Google signup - one-time 10GB gift";
  const templateId = await optionalTemplateId(client);
  if (templateId) return client.createUserFromTemplate(templateId, username, note);
  return client.createUser(username, GOOGLE_FREE_QUOTA_BYTES, groupIdsFromVisibleUsers(visibleUsers), note, 1);
}

export async function ensureGoogleFreeService(
  quickPingUserId: string,
  googleSubject: string,
  client: PasarGuardClient = createPasarGuardClient(),
) {
  const existing = await db.pasarGuardBinding.findFirst({
    where: {
      service: {
        userId: quickPingUserId,
        isFree: true,
        plan: { name: GOOGLE_FREE_PLAN_NAME },
      },
    },
    select: { id: true, externalUserId: true },
  });
  if (existing) {
    const externalUserId = Number(existing.externalUserId);
    if (!Number.isSafeInteger(externalUserId)) throw new PasarGuardError("invalid_response", "شناسه سرویس رایگان پاسارگارد معتبر نیست");
    const remote = await client.getUser(externalUserId);
    if (remote.dataLimit !== GOOGLE_FREE_QUOTA_BYTES) throw new PasarGuardError("invalid_response", "حجم سرویس رایگان پاسارگارد تغییر کرده است");
    return syncPasarGuardBinding(existing.id, client);
  }

  const stableUsername = googleFreeUsername(googleSubject);
  const visibleUsers = await client.listUsers();
  let remote = findRemote(visibleUsers, stableUsername);
  if (!remote) {
    try {
      remote = await createRemote(client, stableUsername, visibleUsers);
    } catch (error) {
      remote = findRemote(await client.listUsers(), stableUsername);
      if (!remote) throw error;
    }
  }
  assertFreeUser(remote);

  try {
    return await bindPasarGuardUser(quickPingUserId, remote.id, client, {
      isFree: true,
      planName: GOOGLE_FREE_PLAN_NAME,
      serviceName: "Google 10GB",
      allowAdditionalBinding: true,
      expectedQuotaBytes: GOOGLE_FREE_QUOTA_BYTES,
    });
  } catch (error) {
    const recovered = await db.pasarGuardBinding.findUnique({
      where: { externalUserId: BigInt(remote.id) },
      include: { service: { select: { userId: true, isFree: true } } },
    });
    if (recovered?.service.userId === quickPingUserId && recovered.service.isFree) return syncPasarGuardBinding(recovered.id, client);
    throw error;
  }
}
