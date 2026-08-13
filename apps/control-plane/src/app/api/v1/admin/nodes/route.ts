import type { NextRequest } from "next/server";
import { z } from "zod";
import { adminFromRequest, isSameOrigin } from "@/lib/admin-session";
import { fail, ok } from "@/lib/api";
import { encryptConfig } from "@/lib/config-encryption";
import { db } from "@/lib/db";
import { singBoxRuntimeConfigSchema } from "@/lib/sing-box-config";

const schema = z.object({
  regionId: z.string().min(1),
  name: z.string().min(2).max(120),
  host: z.string().min(1).max(255),
  port: z.number().int().min(1).max(65535),
  protocol: z.enum(["VLESS", "VMESS", "TROJAN", "WIREGUARD", "HYSTERIA2", "SOCKS5", "SINGBOX", "XRAY"]),
  capacity: z.number().int().min(1).max(1_000_000),
  freeAllowed: z.boolean().default(false),
  config: singBoxRuntimeConfigSchema,
});

const updateSchema = z.object({
  id: z.string().min(1),
  status: z.enum(["ONLINE", "DEGRADED", "OFFLINE", "MAINTENANCE"]),
  capacity: z.number().int().min(1).max(1_000_000),
});

export async function GET(request: NextRequest) {
  const admin = await adminFromRequest(request);
  if (!admin) return fail(401, "unauthorized", "ورود مدیر لازم است");
  const nodes = await db.vpnNode.findMany({ include: { region: true }, orderBy: { name: "asc" } });
  return ok(nodes.map(({ configCiphertext: _secret, ...node }) => node));
}

export async function POST(request: NextRequest) {
  if (!isSameOrigin(request)) return fail(403, "invalid_origin", "مبدأ درخواست معتبر نیست");
  const admin = await adminFromRequest(request);
  if (!admin || admin.role !== "ADMIN") return fail(403, "forbidden", "فقط مدیر اصلی مجاز است");
  const input = schema.safeParse(await request.json().catch(() => null));
  if (!input.success) return fail(400, "invalid_input", "اطلاعات نود معتبر نیست");
  const node = await db.vpnNode.create({
    data: {
      regionId: input.data.regionId,
      name: input.data.name,
      host: input.data.host,
      port: input.data.port,
      protocol: input.data.protocol,
      capacity: input.data.capacity,
      freeAllowed: input.data.freeAllowed,
      configCiphertext: encryptConfig(input.data.config),
      status: "OFFLINE",
    },
  });
  await db.auditLog.create({
    data: { actorId: admin.sub, action: "node.create", entityType: "VpnNode", entityId: node.id, after: { name: node.name, host: node.host, protocol: node.protocol } },
  });
  const { configCiphertext: _secret, ...publicNode } = node;
  return ok(publicNode, { status: 201 });
}

export async function PATCH(request: NextRequest) {
  if (!isSameOrigin(request)) return fail(403, "invalid_origin", "مبدأ درخواست معتبر نیست");
  const admin = await adminFromRequest(request);
  if (!admin || admin.role !== "ADMIN") return fail(403, "forbidden", "فقط مدیر اصلی مجاز است");
  const input = updateSchema.safeParse(await request.json().catch(() => null));
  if (!input.success) return fail(400, "invalid_input", "تغییرات نود معتبر نیست");
  const before = await db.vpnNode.findUnique({
    where: { id: input.data.id },
    select: { id: true, status: true, capacity: true },
  });
  if (!before) return fail(404, "node_not_found", "نود پیدا نشد");
  const node = await db.vpnNode.update({
    where: { id: before.id },
    data: {
      status: input.data.status,
      capacity: input.data.capacity,
      lastSeenAt: input.data.status === "ONLINE" ? new Date() : undefined,
    },
    select: { id: true, name: true, status: true, capacity: true, lastSeenAt: true },
  });
  await db.auditLog.create({
    data: {
      actorId: admin.sub,
      action: "node.updateStatus",
      entityType: "VpnNode",
      entityId: node.id,
      before: { status: before.status, capacity: before.capacity },
      after: { status: node.status, capacity: node.capacity },
    },
  });
  return ok(node);
}
