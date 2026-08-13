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

export async function resolveGoogleFreeTemplateId(client: PasarGuardClient): Promise<number> {
  const configured = configuredFreeTemplateId();
  if (configured) return configured;

  const candidates = (await client.listUserTemplates()).filter((template) => (
    template.dataLimit === GOOGLE_FREE_QUOTA_BYTES
    && !template.isDisabled
    && template.status === "active"
    && template.resetStrategy === "no_reset"
  ));
  if (candidates.length === 0) {
    throw new PasarGuardError(
      "not_configured",
      "هیچ قالب فعال 10GB بدون تمدید خودکار در پاسارگارد پیدا نشد",
    );
  }
  if (candidates.length === 1) return candidates[0].id;

  const ranked = candidates
    .map((template) => ({ template, score: templateNameScore(template) }))
    .sort((left, right) => right.score - left.score);
  if (ranked[0].score > 0 && ranked[0].score > ranked[1].score) return ranked[0].template.id;

  throw new PasarGuardError(
    "not_configured",
    "چند قالب معتبر 10GB در پاسارگارد وجود دارد و انتخاب خودکار امن نیست",
  );
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
    // A one-time gift stays tied to the same remote account after quota exhaustion or expiry.
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
