import { z } from "zod";

const trafficValue = z.union([z.string(), z.number(), z.bigint()]).nullable().optional();
const groupIdsValue = z.array(z.coerce.number().int().positive()).nullable().optional().transform((value) => value ?? []);
const rawUserSchema = z.object({
  id: z.coerce.number().int().positive(),
  username: z.string().min(1),
  status: z.string().min(1).nullable().optional().transform((value) => value?.trim() || "active"),
  used_traffic: trafficValue,
  data_limit: trafficValue,
  expire: z.union([z.string(), z.number(), z.null()]).optional(),
  hwid_limit: z.coerce.number().int().nonnegative().nullable().optional(),
  group_ids: groupIdsValue,
}).passthrough();

const rawSimpleUserSchema = z.object({
  id: z.coerce.number().int().positive(),
  username: z.string().min(1),
}).passthrough();

const rawTemplateSchema = z.object({
  id: z.coerce.number().int().positive(),
  name: z.string().nullable().optional(),
  data_limit: trafficValue,
  expire_duration: z.coerce.number().int().nonnegative().nullable().optional(),
  group_ids: groupIdsValue,
  status: z.string().nullable().optional(),
  is_disabled: z.boolean().nullable().optional(),
  data_limit_reset_strategy: z.string().nullable().optional(),
}).passthrough();

const rawGroupSchema = z.object({
  id: z.coerce.number().int().positive(),
  name: z.string().nullable().optional(),
}).passthrough();

const rawUserArraySchema = z.array(rawUserSchema);
const rawSimpleUserArraySchema = z.array(rawSimpleUserSchema);
const rawGroupArraySchema = z.array(rawGroupSchema);
const rawTemplateArraySchema = z.array(rawTemplateSchema);

const usersResponseSchema = z.object({
  users: rawUserArraySchema,
  total: z.coerce.number().int().nonnegative().optional(),
}).passthrough();

const simpleUsersResponseSchema = z.object({
  users: rawSimpleUserArraySchema,
  total: z.coerce.number().int().nonnegative().optional(),
}).passthrough();

const groupsResponseSchema = z.object({
  groups: rawGroupArraySchema,
  total: z.coerce.number().int().nonnegative().optional(),
}).passthrough();

const tokenSchema = z.object({ access_token: z.string().min(1) });

type ParsedListPage<T> = {
  items: T[];
  total: number | null;
  complete: boolean;
};

type RawUser = z.infer<typeof rawUserSchema>;
type RawSimpleUser = z.infer<typeof rawSimpleUserSchema>;
type RawGroup = z.infer<typeof rawGroupSchema>;

function parseUsersPage(value: unknown): ParsedListPage<RawUser> | null {
  const direct = rawUserArraySchema.safeParse(value);
  if (direct.success) return { items: direct.data, total: direct.data.length, complete: true };

  const envelope = usersResponseSchema.safeParse(value);
  if (envelope.success) {
    return {
      items: envelope.data.users,
      total: envelope.data.total ?? null,
      complete: false,
    };
  }

  if (value && typeof value === "object" && "data" in value) {
    const data = (value as { data?: unknown; total?: unknown }).data;
    const outerTotal = z.coerce.number().int().nonnegative().safeParse((value as { total?: unknown }).total);
    const nestedDirect = rawUserArraySchema.safeParse(data);
    if (nestedDirect.success) {
      return {
        items: nestedDirect.data,
        total: outerTotal.success ? outerTotal.data : nestedDirect.data.length,
        complete: !outerTotal.success,
      };
    }
    const nestedEnvelope = usersResponseSchema.safeParse(data);
    if (nestedEnvelope.success) {
      return {
        items: nestedEnvelope.data.users,
        total: nestedEnvelope.data.total ?? (outerTotal.success ? outerTotal.data : null),
        complete: false,
      };
    }
  }

  return null;
}

function parseSimpleUsersPage(value: unknown): ParsedListPage<RawSimpleUser> | null {
  const direct = rawSimpleUserArraySchema.safeParse(value);
  if (direct.success) return { items: direct.data, total: direct.data.length, complete: true };

  const envelope = simpleUsersResponseSchema.safeParse(value);
  if (envelope.success) {
    return {
      items: envelope.data.users,
      total: envelope.data.total ?? null,
      complete: false,
    };
  }

  if (value && typeof value === "object" && "data" in value) {
    const data = (value as { data?: unknown; total?: unknown }).data;
    const outerTotal = z.coerce.number().int().nonnegative().safeParse((value as { total?: unknown }).total);
    const nestedDirect = rawSimpleUserArraySchema.safeParse(data);
    if (nestedDirect.success) {
      return {
        items: nestedDirect.data,
        total: outerTotal.success ? outerTotal.data : nestedDirect.data.length,
        complete: !outerTotal.success,
      };
    }
    const nestedEnvelope = simpleUsersResponseSchema.safeParse(data);
    if (nestedEnvelope.success) {
      return {
        items: nestedEnvelope.data.users,
        total: nestedEnvelope.data.total ?? (outerTotal.success ? outerTotal.data : null),
        complete: false,
      };
    }
  }

  return null;
}

function parseGroupsPage(value: unknown): ParsedListPage<RawGroup> | null {
  const direct = rawGroupArraySchema.safeParse(value);
  if (direct.success) return { items: direct.data, total: direct.data.length, complete: true };

  const envelope = groupsResponseSchema.safeParse(value);
  if (envelope.success) {
    return {
      items: envelope.data.groups,
      total: envelope.data.total ?? null,
      complete: false,
    };
  }

  if (value && typeof value === "object" && "data" in value) {
    const data = (value as { data?: unknown; total?: unknown }).data;
    const outerTotal = z.coerce.number().int().nonnegative().safeParse((value as { total?: unknown }).total);
    const nestedDirect = rawGroupArraySchema.safeParse(data);
    if (nestedDirect.success) {
      return {
        items: nestedDirect.data,
        total: outerTotal.success ? outerTotal.data : nestedDirect.data.length,
        complete: !outerTotal.success,
      };
    }
    const nestedEnvelope = groupsResponseSchema.safeParse(data);
    if (nestedEnvelope.success) {
      return {
        items: nestedEnvelope.data.groups,
        total: nestedEnvelope.data.total ?? (outerTotal.success ? outerTotal.data : null),
        complete: false,
      };
    }
  }

  return null;
}

function parseTemplates(value: unknown): z.infer<typeof rawTemplateSchema>[] | null {
  const direct = rawTemplateArraySchema.safeParse(value);
  if (direct.success) return direct.data;
  if (!value || typeof value !== "object") return null;

  for (const key of ["templates", "data", "items"] as const) {
    const parsed = rawTemplateArraySchema.safeParse((value as Record<string, unknown>)[key]);
    if (parsed.success) return parsed.data;
  }
  return null;
}

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

export type PasarGuardGroup = {
  id: number;
  name: string;
};

export type PasarGuardUserUpdate = {
  dataLimit?: bigint;
  expiresAt?: Date | null;
  maxDevices?: number | null;
  status?: string;
  groupIds?: number[];
  note?: string;
};

export type PasarGuardCredentials = { baseUrl: URL; username: string; password: string };
type PasarGuardClientOptions = PasarGuardCredentials & { fetch?: typeof fetch; timeoutMs?: number; providerId?: string | null };

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

function safeDataLimit(value: bigint): number {
  if (value < 0n || value > BigInt(Number.MAX_SAFE_INTEGER)) {
    throw new PasarGuardError("invalid_response", "حجم سرویس پاسارگارد خارج از محدودهٔ معتبر است");
  }
  return Number(value);
}

function parseExpiry(value: string | number | null | undefined): Date | null {
  if (value === null || value === undefined || value === 0 || value === "0" || value === "") return null;
  const date = typeof value === "number" ? new Date(value < 10_000_000_000 ? value * 1000 : value) : new Date(value);
  if (Number.isNaN(date.getTime())) throw new PasarGuardError("invalid_response", "تاریخ انقضای دریافتی از پاسارگارد معتبر نیست");
  return date;
}

function mapUser(input: RawUser): PasarGuardUser {
  const dataLimit = toBigInt(input.data_limit);
  return {
    id: input.id,
    username: input.username,
    status: input.status,
    usedTraffic: toBigInt(input.used_traffic),
    dataLimit: dataLimit > 0n ? dataLimit : null,
    expiresAt: parseExpiry(input.expire),
    maxDevices: input.hwid_limit && input.hwid_limit > 0 ? input.hwid_limit : null,
    groupIds: input.group_ids,
  };
}

function mapSimpleUser(input: RawSimpleUser): PasarGuardUser {
  return {
    id: input.id,
    username: input.username,
    status: "active",
    usedTraffic: 0n,
    dataLimit: null,
    expiresAt: null,
    maxDevices: null,
    groupIds: [],
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

function mapGroup(input: RawGroup): PasarGuardGroup {
  return {
    id: input.id,
    name: input.name?.trim() || `Group ${input.id}`,
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

export function isPasarGuardEnvConfigured(): boolean {
  return Boolean(process.env.PASARGUARD_BASE_URL?.trim() && process.env.PASARGUARD_USERNAME?.trim() && process.env.PASARGUARD_PASSWORD);
}

export class PasarGuardClient {
  readonly providerId: string | null;
  private readonly fetcher: typeof fetch;
  private readonly timeoutMs: number;
  private tokenPromise?: Promise<string>;

  constructor(private readonly options: PasarGuardClientOptions) {
    this.providerId = options.providerId ?? null;
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

  private async listFullUsers(): Promise<PasarGuardUser[]> {
    const pageSize = 100;
    const users: PasarGuardUser[] = [];
    let offset = 0;
    let expectedTotal: number | null = null;

    while (true) {
      const response = await this.authorized(`api/users?load_sub=false&limit=${pageSize}&offset=${offset}`);
      const page = parseUsersPage(await response.json().catch(() => null));
      if (!page) throw new PasarGuardError("invalid_response", "فهرست کاربران پاسارگارد ساختار معتبری ندارد");

      if (page.total !== null) expectedTotal = page.total;
      users.push(...page.items.map(mapUser));
      offset += page.items.length;

      if (page.complete) break;
      if (expectedTotal !== null && users.length >= expectedTotal) break;
      if (page.items.length < pageSize && expectedTotal === null) break;
      if (page.items.length === 0 || offset > 100_000) {
        throw new PasarGuardError("invalid_response", "صفحه‌بندی کاربران پاسارگارد کامل دریافت نشد");
      }
    }

    return users.slice(0, expectedTotal ?? users.length);
  }

  private async listSimpleUsers(): Promise<PasarGuardUser[]> {
    const pageSize = 100;
    const users: PasarGuardUser[] = [];
    let offset = 0;
    let expectedTotal: number | null = null;

    while (true) {
      const response = await this.authorized(`api/users/simple?limit=${pageSize}&offset=${offset}`);
      const page = parseSimpleUsersPage(await response.json().catch(() => null));
      if (!page) throw new PasarGuardError("invalid_response", "فهرست ساده کاربران پاسارگارد ساختار معتبری ندارد");

      if (page.total !== null) expectedTotal = page.total;
      users.push(...page.items.map(mapSimpleUser));
      offset += page.items.length;

      if (page.complete) break;
      if (expectedTotal !== null && users.length >= expectedTotal) break;
      if (page.items.length < pageSize && expectedTotal === null) break;
      if (page.items.length === 0 || offset > 100_000) {
        throw new PasarGuardError("invalid_response", "صفحه‌بندی کاربران پاسارگارد کامل دریافت نشد");
      }
    }

    return users.slice(0, expectedTotal ?? users.length);
  }

  async listUsers(): Promise<PasarGuardUser[]> {
    try {
      return await this.listFullUsers();
    } catch (error) {
      if (!(error instanceof PasarGuardError) || (error.code !== "invalid_response" && error.code !== "forbidden")) throw error;
      return this.listSimpleUsers();
    }
  }

  async listUserTemplates(): Promise<PasarGuardUserTemplate[]> {
    const response = await this.authorized("api/user_templates");
    const parsed = parseTemplates(await response.json().catch(() => null));
    if (!parsed) throw new PasarGuardError("invalid_response", "فهرست قالب‌های پاسارگارد ساختار معتبری ندارد");
    return parsed.map(mapTemplate);
  }

  async listGroups(): Promise<PasarGuardGroup[]> {
    const pageSize = 100;
    const groups: PasarGuardGroup[] = [];
    let offset = 0;
    let expectedTotal: number | null = null;

    while (true) {
      const response = await this.authorized(`api/groups/simple?limit=${pageSize}&offset=${offset}`);
      const page = parseGroupsPage(await response.json().catch(() => null));
      if (!page) throw new PasarGuardError("invalid_response", "فهرست گروه‌های پاسارگارد ساختار معتبری ندارد");

      if (page.total !== null) expectedTotal = page.total;
      groups.push(...page.items.map(mapGroup));
      offset += page.items.length;

      if (page.complete) break;
      if (expectedTotal !== null && groups.length >= expectedTotal) break;
      if (page.items.length < pageSize && expectedTotal === null) break;
      if (page.items.length === 0 || offset > 100_000) {
        throw new PasarGuardError("invalid_response", "صفحه‌بندی گروه‌های پاسارگارد کامل دریافت نشد");
      }
    }

    return groups.slice(0, expectedTotal ?? groups.length);
  }

  async getUser(id: number): Promise<PasarGuardUser> {
    const response = await this.authorized(`api/user/by-id/${id}`);
    const parsed = rawUserSchema.safeParse(await response.json().catch(() => null));
    if (!parsed.success) throw new PasarGuardError("invalid_response", "اطلاعات کاربر پاسارگارد ساختار معتبری ندارد");
    return mapUser(parsed.data);
  }

  async createUser(
    username: string,
    dataLimit: bigint,
    groupIds: number[],
    note: string,
    maxDevices = 1,
    expiresAt: Date | null = null,
  ): Promise<PasarGuardUser> {
    if (dataLimit <= 0n || groupIds.length === 0 || maxDevices <= 0) {
      throw new PasarGuardError("invalid_response", "مشخصات سرویس پاسارگارد معتبر نیست");
    }
    const response = await this.authorized("api/user", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        username,
        status: "active",
        expire: expiresAt ? expiresAt.toISOString() : 0,
        data_limit: safeDataLimit(dataLimit),
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

  async updateUser(username: string, changes: PasarGuardUserUpdate): Promise<PasarGuardUser> {
    const body: Record<string, unknown> = {};
    if (changes.dataLimit !== undefined) body.data_limit = safeDataLimit(changes.dataLimit);
    if (changes.expiresAt !== undefined) body.expire = changes.expiresAt ? changes.expiresAt.toISOString() : 0;
    if (changes.maxDevices !== undefined) {
      if (changes.maxDevices !== null && changes.maxDevices <= 0) {
        throw new PasarGuardError("invalid_response", "تعداد دستگاه پاسارگارد معتبر نیست");
      }
      body.hwid_limit = changes.maxDevices;
    }
    if (changes.status !== undefined) body.status = changes.status;
    if (changes.groupIds !== undefined) {
      if (changes.groupIds.length === 0) throw new PasarGuardError("invalid_response", "گروه سرور پاسارگارد خالی است");
      body.group_ids = [...new Set(changes.groupIds)].sort((a, b) => a - b);
    }
    if (changes.note !== undefined) body.note = changes.note;
    if (Object.keys(body).length === 0) return this.getUserByUsername(username);

    const response = await this.authorized(`api/user/${encodeURIComponent(username)}`, {
      method: "PUT",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(body),
    });
    const parsed = rawUserSchema.safeParse(await response.json().catch(() => null));
    if (!parsed.success) throw new PasarGuardError("invalid_response", "کاربر ویرایش‌شده در پاسارگارد ساختار معتبری ندارد");
    return mapUser(parsed.data);
  }

  async getUserByUsername(username: string): Promise<PasarGuardUser> {
    const response = await this.authorized(`api/user/${encodeURIComponent(username)}`);
    const parsed = rawUserSchema.safeParse(await response.json().catch(() => null));
    if (!parsed.success) throw new PasarGuardError("invalid_response", "اطلاعات کاربر پاسارگارد ساختار معتبری ندارد");
    return mapUser(parsed.data);
  }

  async deleteUser(username: string): Promise<void> {
    await this.authorized(`api/user/${encodeURIComponent(username)}`, { method: "DELETE" });
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
