import { z } from "zod";

const trafficValue = z.union([z.string(), z.number(), z.bigint()]).nullable().optional();
const rawUserSchema = z.object({
  id: z.coerce.number().int().positive(),
  username: z.string().min(1),
  status: z.string().min(1),
  used_traffic: trafficValue,
  data_limit: trafficValue,
  expire: z.union([z.string(), z.number(), z.null()]).optional(),
  hwid_limit: z.coerce.number().int().positive().nullable().optional(),
  group_ids: z.array(z.coerce.number().int().positive()).default([]),
});

const rawTemplateSchema = z.object({
  id: z.coerce.number().int().positive(),
  name: z.string().nullable().optional(),
  data_limit: trafficValue,
  expire_duration: z.coerce.number().int().nonnegative().nullable().optional(),
  group_ids: z.array(z.coerce.number().int().positive()).default([]),
  status: z.string().nullable().optional(),
  is_disabled: z.boolean().nullable().optional(),
  data_limit_reset_strategy: z.string().nullable().optional(),
});

const usersResponseSchema = z.object({
  users: z.array(rawUserSchema),
  total: z.number().int().nonnegative(),
});

const tokenSchema = z.object({ access_token: z.string().min(1) });

export type PasarGuardUser = {
  id: number;
  username: string;
  status: string;
  usedTraffic: bigint;
  dataLimit: bigint | null;
  expiresAt: Date | null;
  maxDevices: number | null;
  groupIds: number[];
};

export type PasarGuardUserTemplate = {
  id: number;
  name: string;
  dataLimit: bigint | null;
  expireDurationSeconds: number | null;
  groupIds: number[];
  status: string;
  isDisabled: boolean;
  resetStrategy: string;
};

type PasarGuardCredentials = { baseUrl: URL; username: string; password: string };
type PasarGuardClientOptions = PasarGuardCredentials & { fetch?: typeof fetch; timeoutMs?: number };

export class PasarGuardError extends Error {
  constructor(
    public readonly code: "not_configured" | "unreachable" | "unauthorized" | "forbidden" | "invalid_response" | "upstream_error",
    message: string,
    public readonly status?: number,
  ) {
    super(message);
    this.name = "PasarGuardError";
  }
}

function toBigInt(value: string | number | bigint | null | undefined): bigint {
  if (value === null || value === undefined || value === "") return 0n;
  try {
    if (typeof value === "number" && (!Number.isSafeInteger(value) || value < 0)) throw new Error("unsafe");
    const parsed = BigInt(value);
    if (parsed < 0n) throw new Error("negative");
    return parsed;
  } catch {
    throw new PasarGuardError("invalid_response", "مقدار مصرف دریافتی از پاسارگارد معتبر نیست");
  }
}

function parseExpiry(value: string | number | null | undefined): Date | null {
  if (value === null || value === undefined || value === 0 || value === "0" || value === "") return null;
  const date = typeof value === "number" ? new Date(value < 10_000_000_000 ? value * 1000 : value) : new Date(value);
  if (Number.isNaN(date.getTime())) throw new PasarGuardError("invalid_response", "تاریخ انقضای دریافتی از پاسارگارد معتبر نیست");
  return date;
}

function mapUser(input: z.infer<typeof rawUserSchema>): PasarGuardUser {
  const dataLimit = toBigInt(input.data_limit);
  return {
    id: input.id,
    username: input.username,
    status: input.status,
    usedTraffic: toBigInt(input.used_traffic),
    dataLimit: dataLimit > 0n ? dataLimit : null,
    expiresAt: parseExpiry(input.expire),
    maxDevices: input.hwid_limit ?? null,
    groupIds: input.group_ids,
  };
}

function mapTemplate(input: z.infer<typeof rawTemplateSchema>): PasarGuardUserTemplate {
  const dataLimit = toBigInt(input.data_limit);
  return {
    id: input.id,
    name: input.name?.trim() || `Template ${input.id}`,
    dataLimit: dataLimit > 0n ? dataLimit : null,
    expireDurationSeconds: input.expire_duration ?? null,
    groupIds: input.group_ids,
    status: input.status?.trim().toLowerCase() || "active",
    isDisabled: input.is_disabled ?? false,
    resetStrategy: input.data_limit_reset_strategy?.trim().toLowerCase() || "no_reset",
  };
}

export function normalizePasarGuardBaseUrl(value: string): URL {
  let url: URL;
  try { url = new URL(value.trim()); } catch { throw new PasarGuardError("not_configured", "آدرس پنل پاسارگارد معتبر نیست"); }
  if (url.protocol !== "https:" && url.hostname !== "localhost" && url.hostname !== "127.0.0.1") {
    throw new PasarGuardError("not_configured", "اتصال پاسارگارد باید از HTTPS استفاده کند");
  }
  url.username = "";
  url.password = "";
  url.search = "";
  url.hash = "";
  const dashboardIndex = url.pathname.toLowerCase().indexOf("/dashboard");
  if (dashboardIndex >= 0) url.pathname = url.pathname.slice(0, dashboardIndex) || "/";
  url.pathname = `${url.pathname.replace(/\/+$/, "")}/`;
  return url;
}

export function pasarGuardCredentialsFromEnv(): PasarGuardCredentials {
  const baseUrl = process.env.PASARGUARD_BASE_URL?.trim();
  const username = process.env.PASARGUARD_USERNAME?.trim();
  const password = process.env.PASARGUARD_PASSWORD;
  if (!baseUrl || !username || !password) throw new PasarGuardError("not_configured", "اطلاعات اتصال پاسارگارد در Secretهای سرور کامل نیست");
  return { baseUrl: normalizePasarGuardBaseUrl(baseUrl), username, password };
}

export function isPasarGuardConfigured(): boolean {
  return Boolean(process.env.PASARGUARD_BASE_URL?.trim() && process.env.PASARGUARD_USERNAME?.trim() && process.env.PASARGUARD_PASSWORD);
}

export class PasarGuardClient {
  private readonly fetcher: typeof fetch;
  private readonly timeoutMs: number;
  private tokenPromise?: Promise<string>;

  constructor(private readonly options: PasarGuardClientOptions) {
    this.fetcher = options.fetch ?? fetch;
    this.timeoutMs = options.timeoutMs ?? 10_000;
  }

  private endpoint(path: string): URL { return new URL(path.replace(/^\//, ""), this.options.baseUrl); }

  private async fetchWithTimeout(path: string, init: RequestInit): Promise<Response> {
    try {
      return await this.fetcher(this.endpoint(path), { ...init, cache: "no-store", signal: AbortSignal.timeout(this.timeoutMs) });
    } catch (error) {
      if (error instanceof PasarGuardError) throw error;
      throw new PasarGuardError("unreachable", "ارتباط با پنل پاسارگارد برقرار نشد");
    }
  }

  private async token(): Promise<string> {
    if (!this.tokenPromise) {
      this.tokenPromise = (async () => {
        const body = new URLSearchParams({ grant_type: "password", username: this.options.username, password: this.options.password });
        const response = await this.fetchWithTimeout("api/admin/token", {
          method: "POST",
          headers: { "content-type": "application/x-www-form-urlencoded" },
          body,
        });
        if (response.status === 401) throw new PasarGuardError("unauthorized", "نام کاربری یا رمز پنل پاسارگارد پذیرفته نشد", 401);
        if (!response.ok) throw new PasarGuardError("upstream_error", "پنل پاسارگارد ورود API را نپذیرفت", response.status);
        const parsed = tokenSchema.safeParse(await response.json().catch(() => null));
        if (!parsed.success) throw new PasarGuardError("invalid_response", "پاسخ ورود پاسارگارد معتبر نیست");
        return parsed.data.access_token;
      })();
    }
    try { return await this.tokenPromise; } catch (error) { this.tokenPromise = undefined; throw error; }
  }

  private async authorized(path: string, init: RequestInit = {}, retry = true): Promise<Response> {
    const providedHeaders = init.headers && !Array.isArray(init.headers) && !(init.headers instanceof Headers)
      ? init.headers as Record<string, string>
      : Object.fromEntries(new Headers(init.headers).entries());
    const headers = { ...providedHeaders, authorization: `Bearer ${await this.token()}`, accept: "application/json" };
    const response = await this.fetchWithTimeout(path, { ...init, headers });
    if (response.status === 401 && retry) { this.tokenPromise = undefined; return this.authorized(path, init, false); }
    if (response.status === 401) throw new PasarGuardError("unauthorized", "نشست API پاسارگارد معتبر نیست", 401);
    if (response.status === 403) throw new PasarGuardError("forbidden", "این مدیر پاسارگارد مجوز لازم برای این عملیات را ندارد", 403);
    if (!response.ok) throw new PasarGuardError("upstream_error", "پنل پاسارگارد پاسخ موفق نداد", response.status);
    return response;
  }

  async listUsers(): Promise<PasarGuardUser[]> {
    const response = await this.authorized("api/users?load_sub=false");
    const parsed = usersResponseSchema.safeParse(await response.json().catch(() => null));
    if (!parsed.success) throw new PasarGuardError("invalid_response", "فهرست کاربران پاسارگارد ساختار معتبری ندارد");
    return parsed.data.users.map(mapUser);
  }

  async listUserTemplates(): Promise<PasarGuardUserTemplate[]> {
    const response = await this.authorized("api/user_templates");
    const parsed = z.array(rawTemplateSchema).safeParse(await response.json().catch(() => null));
    if (!parsed.success) throw new PasarGuardError("invalid_response", "فهرست قالب‌های پاسارگارد ساختار معتبری ندارد");
    return parsed.data.map(mapTemplate);
  }

  async getUser(id: number): Promise<PasarGuardUser> {
    const response = await this.authorized(`api/user/by-id/${id}`);
    const parsed = rawUserSchema.safeParse(await response.json().catch(() => null));
    if (!parsed.success) throw new PasarGuardError("invalid_response", "اطلاعات کاربر پاسارگارد ساختار معتبری ندارد");
    return mapUser(parsed.data);
  }

  async createUser(username: string, dataLimit: bigint, groupIds: number[], note: string, maxDevices = 1): Promise<PasarGuardUser> {
    if (dataLimit <= 0n || dataLimit > BigInt(Number.MAX_SAFE_INTEGER) || groupIds.length === 0) {
      throw new PasarGuardError("invalid_response", "مشخصات سرویس رایگان پاسارگارد معتبر نیست");
    }
    const response = await this.authorized("api/user", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        username,
        status: "active",
        expire: 0,
        data_limit: Number(dataLimit),
        data_limit_reset_strategy: "no_reset",
        group_ids: [...new Set(groupIds)].sort((a, b) => a - b),
        note,
        hwid_limit: maxDevices,
      }),
    });
    const parsed = rawUserSchema.safeParse(await response.json().catch(() => null));
    if (!parsed.success) throw new PasarGuardError("invalid_response", "کاربر ساخته‌شده در پاسارگارد ساختار معتبری ندارد");
    return mapUser(parsed.data);
  }

  async createUserFromTemplate(templateId: number, username: string, note: string): Promise<PasarGuardUser> {
    const response = await this.authorized("api/user/from_template", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ user_template_id: templateId, username, note }),
    });
    const parsed = rawUserSchema.safeParse(await response.json().catch(() => null));
    if (!parsed.success) throw new PasarGuardError("invalid_response", "کاربر ساخته‌شده در پاسارگارد ساختار معتبری ندارد");
    return mapUser(parsed.data);
  }

  async getSingBoxConfig(id: number): Promise<unknown> {
    const response = await this.authorized(`api/user/${id}/subscription/sing_box`);
    const text = await response.text();
    try { return JSON.parse(text) as unknown; } catch { throw new PasarGuardError("invalid_response", "اشتراک sing-box دریافتی از پاسارگارد JSON معتبر نیست"); }
  }
}

export function createPasarGuardClient(): PasarGuardClient { return new PasarGuardClient(pasarGuardCredentialsFromEnv()); }
