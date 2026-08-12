import type { NextRequest } from "next/server";
import { z } from "zod";
import { adminFromRequest, isSameOrigin } from "@/lib/admin-session";
import { fail, ok } from "@/lib/api";
import { db } from "@/lib/db";

const schema = z.object({
  serviceId: z.string().min(1),
  nodeId: z.string().min(1),
  priority: z.number().int().min(-10_000).max(10_000).default(0),
  enabled: z.boolean().default(true),
});

export async function POST(request: NextRequest) {
  if (!isSameOrigin(request)) return fail(403, "invalid_origin", "مبدأ درخواست معتبر نیست");
  const admin = await adminFromRequest(request);
  if (!admin || admin.role !== "ADMIN") return fail(403, "forbidden", "فقط مدیر اصلی مجاز است");
  const input = schema.safeParse(await request.json().catch(() => null));
  if (!input.success) return fail(400, "invalid_input", "انتساب سرور معتبر نیست");
  const [service, node] = await Promise.all([
    db.service.findUnique({ where: { id: input.data.serviceId }, select: { id: true } }),
    db.vpnNode.findUnique({ where: { id: input.data.nodeId }, select: { id: true } }),
  ]);
  if (!service || !node) return fail(404, "target_not_found", "سرویس یا نود پیدا نشد");
  const assignment = await db.serviceNode.upsert({
    where: { serviceId_nodeId: { serviceId: service.id, nodeId: node.id } },
    update: { priority: input.data.priority, enabled: input.data.enabled },
    create: {
      serviceId: service.id,
      nodeId: node.id,
      priority: input.data.priority,
      enabled: input.data.enabled,
    },
  });
  await db.auditLog.create({
    data: {
      actorId: admin.sub,
      action: "service.node.assign",
      entityType: "ServiceNode",
      entityId: `${service.id}:${node.id}`,
      after: { priority: assignment.priority, enabled: assignment.enabled },
    },
  });
  return ok(assignment, { status: 201 });
}
