import { z } from "zod";
import {
  pasarGuardCredentialsFromEnv,
  PasarGuardError,
  type PasarGuardUser,
} from "@/lib/pasarguard/client";

const trafficValue = z.union([z.string(), z.number(), z.bigint()]).nullable().optional();
const tokenSchema = z.object({ access_token: z.string().min(1) });
const userSchema = z.object({
  id: z.coerce.number().int().positive(),
  username: z.string().min(1),
  status: z.string().min(1),
  used_traffic: trafficValue,
  data_limit: trafficValue,
  expire: z.union([z.string(), z.number(), z.null()]).optional(),
  hwid_limit: z.coerce.number().int().positive().nullable().optional(),
});
const groupSchema = z.object({
  id: z.coerce.number().int().positive(),
  inbound_tags: z.array(z.string()).default([]),
  is_disabled: z.boolean().nullable().optional(),
});
const groupsResponseSchema = z.object({
  groups: z.array(groupSchema),
  total: z.coerce.number().int().nonnegative(),
});

function toBigInt(value: string | number | bigint | null | undefined): bigint {
  if (value === null || value === undefined || value === "") return 0n;
  try {
    const parsed = BigInt(value);
    if (parsed < 0n) throw new Error("negative");
    return parsed;
  } catch {
    throw new PasarGuardError("invalid_response", "مقدار حجم دریافتی از پاسارگارد معتبر نیست");
  }
}

function parseExpiry(value: string | number | null | undefined): Date | null {
  if (value === null || value === undefined || value === 0 || value === "0" || value === "") return null;
  const date = typeof value === "number"
    ? new Date(value < 10_000_000_000 ? value * 1000 : value)
    : new Date(value);
  if (Number.isNaN(date.getTime())) {
    throw new PasarGuardError("invalid_response", "تاریخ انقضای دریافتی از پاسارگارد معتبر نیست");
  }
  return date;
}

function mapUser(input: z.infer<typeof userSchema>): PasarGuardUser {
  const dataLimit = toBigInt(input.data_limit);
  return {
    id: input.id,
    username: input.username,
    status: input.status,
    usedTraffic: toBigInt(input.used_traffic),
    dataLimit: dataLimit > 0n ? dataLimit : null,
    expiresAt: parseExpiry(input.expire),
    maxDevices: input.hwid_limit ?? null,
  };
}

async function authorized(path: string, init: RequestInit = {}): Promise<Response> {
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
  if (response.status === 403) throw new PasarGuardError("forbidden", "مدیر پاسارگارد مجوز ساخت سرویس کاربر را ندارد", 403);
  if (!response.ok) throw new PasarGuardError("upstream_error", "پنل پاسارگارد عملیات سرویس رایگان را نپذیرفت", response.status);
  return response;
}

export async function googleFreeGroupIds(): Promise<number[]> {
  const response = await authorized("api/groups");
  const parsed = groupsResponseSchema.safeParse(await response.json().catch(() => null));
  if (!parsed.success) {
    throw new PasarGuardError("invalid_response", "فهرست گروه‌های پاسارگارد ساختار معتبری ندارد");
  }
  const ids = parsed.data.groups
    .filter((group) => !group.is_disabled && group.inbound_tags.length > 0)
    .map((group) => group.id)
    .sort((left, right) => left - right);
  if (ids.length === 0) {
    throw new PasarGuardError("not_configured", "هیچ گروه فعال دارای سرور در پاسارگارد برای سرویس رایگان وجود ندارد");
  }
  return ids;
}

export async function preflightDirectGoogleFreeUser(): Promise<void> {
  await googleFreeGroupIds();
}

export async function createDirectGoogleFreeUser(
  username: string,
  quotaBytes: bigint,
  note: string,
): Promise<PasarGuardUser> {
  if (quotaBytes <= 0n || quotaBytes > BigInt(Number.MAX_SAFE_INTEGER)) {
    throw new PasarGuardError("invalid_response", "حجم سرویس رایگان قابل ارسال به پاسارگارد نیست");
  }
  const response = await authorized("api/user", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      username,
      status: "active",
      expire: 0,
      data_limit: Number(quotaBytes),
      data_limit_reset_strategy: "no_reset",
      group_ids: await googleFreeGroupIds(),
      note,
      hwid_limit: 1,
    }),
  });
  const parsed = userSchema.safeParse(await response.json().catch(() => null));
  if (!parsed.success) {
    throw new PasarGuardError("invalid_response", "کاربر رایگان ساخته‌شده در پاسارگارد ساختار معتبری ندارد");
  }
  return mapUser(parsed.data);
}
