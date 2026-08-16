import type { NextRequest } from "next/server";
import { fail, ok, requireBearer } from "@/lib/api";
import { decryptConfig } from "@/lib/config-encryption";
import { db } from "@/lib/db";
import { activePasarGuardProviderSummary } from "@/lib/pasarguard/provider";

export async function GET(
  request: NextRequest,
  context: { params: Promise<{ serviceId: string }> },
) {
  const auth = await requireBearer(request);
  if (!auth.ok) return auth.response;
  const { serviceId } = await context.params;
  if (auth.serviceId && auth.serviceId !== serviceId) {
    return fail(403, "service_scope_mismatch", "This license cannot access the requested service");
  }
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
      pasarGuardBinding: { select: { providerId: true, lastSyncAt: true } },
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
  if (service.pasarGuardBinding) {
    const activeProvider = await activePasarGuardProviderSummary();
    if (!activeProvider
      || service.pasarGuardBinding.providerId !== activeProvider.id
      || !service.pasarGuardBinding.lastSyncAt) {
      return fail(409, "service_pending_review", "The service is waiting for provider migration");
    }
  }
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
