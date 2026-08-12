import type { NextRequest } from "next/server";
import { fail, ok, requireBearer } from "@/lib/api";
import { decryptConfig } from "@/lib/config-encryption";
import { db } from "@/lib/db";

export async function GET(
  request: NextRequest,
  context: { params: Promise<{ serviceId: string }> },
) {
  const auth = await requireBearer(request);
  if (!auth.ok) return auth.response;
  const { serviceId } = await context.params;
  const nodeId = request.nextUrl.searchParams.get("nodeId");
  if (!nodeId) return fail(400, "node_required", "A server must be selected");
  const service = await db.service.findFirst({
    where: {
      id: serviceId,
      userId: auth.userId,
      user: { status: "ACTIVE" },
      status: "ACTIVE",
      expiresAt: { gt: new Date() },
    },
    include: {
      nodes: {
        where: {
          enabled: true,
          nodeId,
          node: { status: { in: ["ONLINE", "DEGRADED"] } },
        },
        orderBy: { priority: "desc" },
        include: { node: { include: { region: true } } },
      },
    },
  });
  if (!service) return fail(404, "service_not_found", "No active service was found");
  if (service.usedBytes >= service.quotaBytes) {
    return fail(403, "quota_exhausted", "The service quota is exhausted");
  }
  const assignment = service.nodes[0];
  if (!assignment) return fail(404, "node_unavailable", "The selected server is unavailable");
  const { node, priority } = assignment;
  return ok({
    serviceId: service.id,
    expiresAt: service.expiresAt,
    node: {
      id: node.id,
      priority,
      name: node.name,
      location: node.region.name,
      countryCode: node.region.countryCode,
      protocol: node.protocol,
      coreType: node.coreType,
      runtimeConfig: decryptConfig<Record<string, unknown>>(node.configCiphertext),
    },
  });
}
