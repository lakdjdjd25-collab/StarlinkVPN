import { timingSafeEqual } from "node:crypto";
import type { NextRequest } from "next/server";
import { z } from "zod";
import { fail, ok } from "@/lib/api";
import { normalizePasarGuardBaseUrl } from "@/lib/pasarguard/client";

const templateSchema = z.object({
  id: z.coerce.number().int().positive(),
  name: z.string().nullable().optional(),
  data_limit: z.union([z.string(), z.number(), z.bigint()]).nullable().optional(),
  group_ids: z.array(z.coerce.number().int().positive()).default([]),
  status: z.string().nullable().optional(),
  is_disabled: z.boolean().nullable().optional(),
  data_limit_reset_strategy: z.string().nullable().optional(),
});
const groupSchema = z.object({
  id: z.coerce.number().int().positive(),
  name: z.string().min(1),
  inbound_tags: z.array(z.string()).default([]),
  is_disabled: z.boolean().nullable().optional(),
  total_users: z.coerce.number().int().nonnegative().nullable().optional(),
});
const groupsSchema = z.object({ groups: z.array(groupSchema), total: z.coerce.number().int().nonnegative() });

function authorized(request: NextRequest): boolean {
  const expected = process.env.PASARGUARD_BOOTSTRAP_TOKEN?.trim() ?? "";
  const header = request.headers.get("authorization") ?? "";
  const provided = header.startsWith("Bearer ") ? header.slice(7).trim() : "";
  const a = Buffer.from(expected);
  const b = Buffer.from(provided);
  return Boolean(expected && provided && a.length === b.length && timingSafeEqual(a, b));
}

async function token() {
  const base = process.env.PASARGUARD_BASE_URL?.trim();
  const username = process.env.PASARGUARD_USERNAME?.trim();
  const password = process.env.PASARGUARD_PASSWORD;
  if (!base || !username || !password) throw new Error("PasarGuard is not configured");
  const baseUrl = normalizePasarGuardBaseUrl(base);
  const response = await fetch(new URL("api/admin/token", baseUrl), {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "password", username, password }),
    cache: "no-store",
  });
  if (!response.ok) throw new Error(`PasarGuard auth failed (${response.status})`);
  const parsed = z.object({ access_token: z.string().min(1) }).parse(await response.json());
  return { baseUrl, accessToken: parsed.access_token };
}

async function read(path: string) {
  const { baseUrl, accessToken } = await token();
  const response = await fetch(new URL(path, baseUrl), {
    headers: { authorization: `Bearer ${accessToken}`, accept: "application/json" },
    cache: "no-store",
  });
  if (!response.ok) throw new Error(`PasarGuard read failed (${response.status})`);
  return response.json();
}

export async function GET(request: NextRequest) {
  if (!authorized(request)) return fail(401, "unauthorized", "Bootstrap authorization required");
  try {
    const [templateJson, groupJson] = await Promise.all([read("api/user_templates"), read("api/groups")]);
    const templates = z.array(templateSchema).parse(templateJson).map((item) => ({
      id: item.id,
      name: item.name ?? `Template ${item.id}`,
      dataLimit: item.data_limit === null || item.data_limit === undefined ? 0 : Number(item.data_limit),
      groupIds: item.group_ids,
      status: item.status ?? "active",
      isDisabled: item.is_disabled ?? false,
      resetStrategy: item.data_limit_reset_strategy ?? "no_reset",
    }));
    const groups = groupsSchema.parse(groupJson).groups.map((item) => ({
      id: item.id,
      name: item.name,
      inboundTags: item.inbound_tags,
      isDisabled: item.is_disabled ?? false,
      totalUsers: item.total_users ?? 0,
    }));
    return ok({ templates, groups });
  } catch (error) {
    return fail(502, "pasarguard_bootstrap_failed", error instanceof Error ? error.message : "PasarGuard read failed");
  }
}
