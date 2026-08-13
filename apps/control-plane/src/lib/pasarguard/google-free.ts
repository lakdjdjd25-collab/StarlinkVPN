import { createHash } from "node:crypto";
import { z } from "zod";
import { db } from "@/lib/db";
import {
  createPasarGuardClient,
  pasarGuardCredentialsFromEnv,
  PasarGuardError,
  type PasarGuardClient,
  type PasarGuardUser,
  type PasarGuardUserTemplate,
} from "@/lib/pasarguard/client";
import { bindPasarGuardUser, syncPasarGuardBinding } from "@/lib/pasarguard/sync";

export const GOOGLE_FREE_QUOTA_BYTES = 10n * 1024n ** 3n;
export const GOOGLE_FREE_PLAN_NAME = "Google Free 10GB";
const GOOGLE_FREE_TEMPLATE_NAME = "QuickPing Google Free 10GB";

const groupSchema = z.object({
  id: z.coerce.number().int().positive(),
  name: z.string().min(1),
  inbound_tags: z.array(z.string()).default([]),
  is_disabled: z.boolean().nullable().optional(),
});
const groupsResponseSchema = z.object({
  groups: z.array(groupSchema),
  total: z.coerce.number().int().nonnegative(),
});
const tokenSchema = z.object({ access_token: z.string().min(1) });

let templateProvisionPromise: Promise<number> | null = null;

function configuredFreeTemplateId(): number | null {
  const raw = process.env.PASARGUARD_FREE_TEMPLATE_ID?.trim();
  if (!raw) return null;
  const value = Number(raw);
  if (!Number.isSafeInteger(value) || value <= 0) {
    throw new PasarGuardError("not_configured", "شناسه قالب سرویس رایگان پاسارگارد معتبر نیست");
  }
  return value;
}

function templateNameScore(template: PasarGuardUserTemplate): number {
  const value = template.name.toLowerCase();
  return ["google", "free", "10gb", "10 gb", "گوگل", "رایگان"]
    .reduce((score, marker) => score + (value.includes(marker) ? 1 : 0), 0);
}

function eligibleTemplates(templates: PasarGuardUserTemplate[]) {
  return templates.filter((template) => (
    template.dataLimit === GOOGLE_FREE_QUOTA_BYTES
    && !template.isDisabled
    && template.status === "active"
    && template.resetStrategy === "no_reset"
  ));
}

function chooseEligibleTemplate(candidates: PasarGuardUserTemplate[]): number | null {
  if (candidates.length === 0) return null;
  if (candidates.length === 1) return candidates[0].id;

  const exact = candidates.filter((template) => template.name.trim().toLowerCase() === GOOGLE_FREE_TEMPLATE_NAME.toLowerCase());
  if (exact.length > 0) return exact.sort((left, right) => left.id - right.id)[0].id;

  const ranked = candidates
    .map((template) => ({ template, score: templateNameScore(template) }))
    .sort((left, right) => right.score - left.score || left.template.id - right.template.id);
  if (ranked[0].score > 0 && ranked[0].score > ranked[1].score) return ranked[0].template.id;
  return null;
}

async function pasarGuardAdminRequest(path: string, init: RequestInit = {}): Promise<Response> {
  const { baseUrl, username, password } = pasarGuardCredentialsFromEnv();
  const tokenResponse = await fetch(new URL("api/admin/token", baseUrl), {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "password", username, password }),
    cache: "no-store",
    signal: AbortSignal.timeout(10_000),
  });
  if (tokenResponse.status === 401) {
    throw new PasarGuardError("unauthorized", "نام کاربری یا رمز پنل پاسارگارد پذیرفته نشد", 401);
  }
  if (!tokenResponse.ok) {
    throw new PasarGuardError("upstream_error", "پنل پاسارگارد ورود API را نپذیرفت", tokenResponse.status);
  }
  const token = tokenSchema.safeParse(await tokenResponse.json().catch(() => null));
  if (!token.success) {
    throw new PasarGuardError("invalid_response", "پاسخ ورود پاسارگارد معتبر نیست");
  }

  const providedHeaders = init.headers && !Array.isArray(init.headers) && !(init.headers instanceof Headers)
    ? init.headers as Record<string, string>
    : Object.fromEntries(new Headers(init.headers).entries());
  const response = await fetch(new URL(path.replace(/^\//, ""), baseUrl), {
    ...init,
    headers: {
      ...providedHeaders,
      authorization: `Bearer ${token.data.access_token}`,
      accept: "application/json",
    },
    cache: "no-store",
    signal: AbortSignal.timeout(10_000),
  });
  if (response.status === 401) throw new PasarGuardError("unauthorized", "نشست API پاسارگارد معتبر نیست", 401);
  if (response.status === 403) throw new PasarGuardError("forbidden", "مدیر پاسارگارد مجوز ساخت قالب سرویس رایگان را ندارد", 403);
  if (!response.ok) throw new PasarGuardError("upstream_error", "پنل پاسارگارد ساخت قالب سرویس رایگان را نپذیرفت", response.status);
  return response;
}

async function provisionGoogleFreeTemplate(client: PasarGuardClient): Promise<number> {
  const groupsResponse = await pasarGuardAdminRequest("api/groups");
  const groups = groupsResponseSchema.safeParse(await groupsResponse.json().catch(() => null));
  if (!groups.success) {
    throw new PasarGuardError("invalid_response", "فهرست گروه‌های پاسارگارد ساختار معتبری ندارد");
  }
  const enabledGroupIds = groups.data.groups
    .filter((group) => !group.is_disabled && group.inbound_tags.length > 0)
    .map((group) => group.id)
    .sort((left, right) => left - right);
  if (enabledGroupIds.length === 0) {
    throw new PasarGuardError("not_configured", "هیچ گروه فعال دارای سرور در پاسارگارد برای سرویس رایگان وجود ندارد");
  }

  try {
    await pasarGuardAdminRequest("api/user_template", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        name: GOOGLE_FREE_TEMPLATE_NAME,
        data_limit: Number(GOOGLE_FREE_QUOTA_BYTES),
        hwid_limit: 1,
        expire_duration: 0,
        group_ids: enabledGroupIds,
        status: "active",
        reset_usages: false,
        data_limit_reset_strategy: "no_reset",
        is_disabled: false,
      }),
    });
  } catch (error) {
    const recovered = chooseEligibleTemplate(eligibleTemplates(await client.listUserTemplates()));
    if (recovered) return recovered;
    throw error;
  }

  const created = chooseEligibleTemplate(eligibleTemplates(await client.listUserTemplates()));
  if (!created) {
    throw new PasarGuardError("invalid_response", "قالب 10GB در پاسارگارد ساخته شد اما قابل تأیید نیست");
  }
  return created;
}

export async function resolveGoogleFreeTemplateId(client: PasarGuardClient): Promise<number> {
  const configured = configuredFreeTemplateId();
  if (configured) return configured;

  const candidates = eligibleTemplates(await client.listUserTemplates());
  const selected = chooseEligibleTemplate(candidates);
  if (selected) return selected;
  if (candidates.length > 1) {
    throw new PasarGuardError(
      "not_configured",
      "چند قالب معتبر 10GB در پاسارگارد وجود دارد و انتخاب خودکار امن نیست",
    );
  }

  if (!templateProvisionPromise) {
    templateProvisionPromise = provisionGoogleFreeTemplate(client);
  }
  try {
    return await templateProvisionPromise;
  } finally {
    templateProvisionPromise = null;
  }
}

export function googleFreeUsername(googleSubject: string): string {
  const hash = createHash("sha256").update(googleSubject).digest("hex").slice(0, 24);
  return `g_${hash}`;
}

function assertFreeQuota(user: PasarGuardUser): void {
  if (user.dataLimit !== GOOGLE_FREE_QUOTA_BYTES) {
    throw new PasarGuardError(
      "invalid_response",
      `سرویس رایگان پاسارگارد باید دقیقاً ${GOOGLE_FREE_QUOTA_BYTES.toString()} بایت حجم داشته باشد`,
    );
  }
}

function assertNewFreeUser(user: PasarGuardUser): void {
  assertFreeQuota(user);
  if (user.status.toLowerCase() !== "active") {
    throw new PasarGuardError("invalid_response", "سرویس رایگان تازه‌ساخته‌شده در پاسارگارد فعال نیست");
  }
  if (user.expiresAt && user.expiresAt.getTime() <= Date.now()) {
    throw new PasarGuardError("invalid_response", "سرویس رایگان تازه‌ساخته‌شده در پاسارگارد منقضی است");
  }
}

function findRemoteUser(users: PasarGuardUser[], stablePart: string): PasarGuardUser | null {
  const matches = users.filter((user) => user.username === stablePart || user.username.includes(stablePart));
  if (matches.length > 1) {
    throw new PasarGuardError("invalid_response", "بیش از یک کاربر رایگان متناظر در پاسارگارد پیدا شد");
  }
  return matches[0] ?? null;
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
    if (!Number.isSafeInteger(externalUserId)) {
      throw new PasarGuardError("invalid_response", "شناسه سرویس رایگان پاسارگارد معتبر نیست");
    }
    const remote = await client.getUser(externalUserId);
    assertFreeQuota(remote);
    return syncPasarGuardBinding(existing.id, client);
  }

  const stablePart = googleFreeUsername(googleSubject);
  let remote = findRemoteUser(await client.listUsers(), stablePart);
  if (!remote) {
    try {
      remote = await client.createUserFromTemplate(
        await resolveGoogleFreeTemplateId(client),
        stablePart,
        "QuickPing Google signup - one-time 10GB gift",
      );
    } catch (error) {
      remote = findRemoteUser(await client.listUsers(), stablePart);
      if (!remote) throw error;
    }
  }
  assertNewFreeUser(remote);

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
    if (recovered?.service.userId === quickPingUserId && recovered.service.isFree) {
      return syncPasarGuardBinding(recovered.id, client);
    }
    throw error;
  }
}
