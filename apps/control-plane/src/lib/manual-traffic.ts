import type { Prisma } from "@/generated/prisma/client";
import { db } from "@/lib/db";
import { remainingServiceBytes, serviceAccessFailure } from "@/lib/server-access";
import { canAccessTier, VIP_ACCESS_REQUIRED } from "@/lib/vip-access";

export class ManualTrafficError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

export type TrafficCumulative = {
  uploadedBytes: bigint;
  downloadedBytes: bigint;
};

export function calculateTrafficCharge(input: {
  previousAccountedTotal: bigint;
  uploadedBytes: bigint;
  downloadedBytes: bigint;
  remainingBytes: bigint;
  countTraffic: boolean;
}) {
  const requestedTotal = input.uploadedBytes + input.downloadedBytes;
  if (input.uploadedBytes < 0n || input.downloadedBytes < 0n || requestedTotal < input.previousAccountedTotal) {
    throw new ManualTrafficError(409, "traffic_counter_regression", "Traffic counters cannot move backwards");
  }
  const delta = requestedTotal - input.previousAccountedTotal;
  const acceptedBytes = input.countTraffic
    ? (delta < input.remainingBytes ? delta : input.remainingBytes)
    : 0n;
  const remainingBytes = input.countTraffic
    ? input.remainingBytes - acceptedBytes
    : input.remainingBytes;
  return {
    requestedTotal,
    delta,
    acceptedBytes,
    remainingBytes: remainingBytes > 0n ? remainingBytes : 0n,
    exhausted: input.countTraffic && remainingBytes <= 0n,
  };
}

function retryableTransaction(error: unknown): boolean {
  return typeof error === "object" && error !== null &&
    "code" in error && (error as { code?: unknown }).code === "P2034";
}

async function serializable<T>(work: (tx: Prisma.TransactionClient) => Promise<T>): Promise<T> {
  let lastError: unknown;
  for (let attempt = 0; attempt < 3; attempt += 1) {
    try {
      return await db.$transaction(work, { isolationLevel: "Serializable" });
    } catch (error) {
      lastError = error;
      if (!retryableTransaction(error) || attempt === 2) throw error;
    }
  }
  throw lastError;
}

export async function startManualTrafficSession(input: {
  userId: string;
  authServiceId: string | null;
  serviceId: string;
  manualServerId: string;
}) {
  if (input.authServiceId && input.authServiceId !== input.serviceId) {
    throw new ManualTrafficError(403, "service_scope_mismatch", "This license cannot access the requested service");
  }
  return serializable(async (tx) => {
    const [service, server] = await Promise.all([
      tx.service.findFirst({
        where: { id: input.serviceId, userId: input.userId },
        include: { user: { select: { status: true } } },
      }),
      tx.manualServer.findFirst({
        where: { id: input.manualServerId, deletedAt: null },
      }),
    ]);
    if (!service) throw new ManualTrafficError(404, "service_not_found", "No service was found");
    if (!server || !server.enabled) {
      throw new ManualTrafficError(404, "manual_server_unavailable", "The selected manual server is unavailable");
    }
    const failure = serviceAccessFailure(service.user, service);
    if (failure === "quota_exhausted") {
      throw new ManualTrafficError(403, "quota_exhausted", "The service quota is exhausted");
    }
    if (failure) throw new ManualTrafficError(403, failure, "The service is unavailable");
    if (!canAccessTier(service.vipAccess, server.accessTier)) {
      throw new ManualTrafficError(403, VIP_ACCESS_REQUIRED, "VIP access is required for this server");
    }

    // A service may legitimately be active on more than one licensed device. Each tunnel owns its
    // own cumulative session, so starting a new one must not revoke another device's live session.
    const now = new Date();
    const session = await tx.trafficSession.create({
      data: { serviceId: service.id, manualServerId: server.id },
      select: { id: true, startedAt: true },
    });
    await tx.manualServer.update({ where: { id: server.id }, data: { lastUsedAt: now } });
    return {
      sessionId: session.id,
      serviceId: service.id,
      serverId: server.id,
      startedAt: session.startedAt,
      remainingBytes: remainingServiceBytes(service),
      countTraffic: server.countTraffic,
    };
  });
}

export async function reportManualTraffic(input: {
  userId: string;
  authServiceId: string | null;
  sessionId: string;
  cumulative: TrafficCumulative;
  finalize: boolean;
}) {
  if (input.cumulative.uploadedBytes < 0n || input.cumulative.downloadedBytes < 0n) {
    throw new ManualTrafficError(400, "invalid_traffic", "Traffic counters cannot be negative");
  }
  const requestedTotal = input.cumulative.uploadedBytes + input.cumulative.downloadedBytes;
  if (requestedTotal > 9_000_000_000_000_000_000n) {
    throw new ManualTrafficError(400, "traffic_overflow", "Traffic counters are too large");
  }
  return serializable(async (tx) => {
    const session = await tx.trafficSession.findUnique({
      where: { id: input.sessionId },
      include: {
        service: { include: { user: { select: { id: true, status: true } } } },
        manualServer: true,
      },
    });
    if (!session || session.service.user.id !== input.userId) {
      throw new ManualTrafficError(404, "traffic_session_not_found", "Traffic session was not found");
    }
    if (input.authServiceId && input.authServiceId !== session.serviceId) {
      throw new ManualTrafficError(403, "service_scope_mismatch", "This license cannot access the traffic session");
    }
    if (input.cumulative.uploadedBytes < session.uploadedBytes ||
        input.cumulative.downloadedBytes < session.downloadedBytes ||
        requestedTotal < session.lastAccountedBytes) {
      throw new ManualTrafficError(409, "traffic_counter_regression", "Traffic counters cannot move backwards");
    }

    const finalizingRevoked = session.status === "REVOKED" && input.finalize;
    if (session.status !== "ACTIVE" && !finalizingRevoked) {
      if (requestedTotal === session.totalBytes &&
          input.cumulative.uploadedBytes === session.uploadedBytes &&
          input.cumulative.downloadedBytes === session.downloadedBytes) {
        return {
          sessionId: session.id,
          acceptedBytes: 0n,
          totalBytes: session.totalBytes,
          remainingBytes: remainingServiceBytes(session.service),
          status: session.status,
          disconnect: session.status === "EXHAUSTED" || session.status === "REVOKED",
        };
      }
      throw new ManualTrafficError(409, "traffic_session_closed", "Traffic session is already closed");
    }

    if (!finalizingRevoked) {
      if (!session.manualServer.enabled || session.manualServer.deletedAt) {
        await tx.trafficSession.update({
          where: { id: session.id },
          data: { status: "REVOKED", endedAt: new Date() },
        });
        throw new ManualTrafficError(403, "manual_server_unavailable", "The selected manual server is unavailable");
      }
      const failure = serviceAccessFailure(session.service.user, session.service);
      if (failure && failure !== "quota_exhausted") {
        await tx.trafficSession.update({
          where: { id: session.id },
          data: { status: "REVOKED", endedAt: new Date() },
        });
        throw new ManualTrafficError(403, failure, "The service is unavailable");
      }
      if (!canAccessTier(session.service.vipAccess, session.manualServer.accessTier)) {
        await tx.trafficSession.update({
          where: { id: session.id },
          data: { status: "REVOKED", endedAt: new Date() },
        });
        throw new ManualTrafficError(403, VIP_ACCESS_REQUIRED, "VIP access is required for this server");
      }
    }

    const charge = calculateTrafficCharge({
      previousAccountedTotal: session.lastAccountedBytes,
      uploadedBytes: input.cumulative.uploadedBytes,
      downloadedBytes: input.cumulative.downloadedBytes,
      remainingBytes: remainingServiceBytes(session.service),
      countTraffic: session.manualServer.countTraffic,
    });
    const now = new Date();
    const status = finalizingRevoked
      ? "REVOKED" as const
      : charge.exhausted
        ? "EXHAUSTED" as const
        : input.finalize
          ? "ENDED" as const
          : "ACTIVE" as const;

    if (charge.acceptedBytes > 0n) {
      await tx.service.update({
        where: { id: session.serviceId },
        data: { manualUsedBytes: { increment: charge.acceptedBytes } },
      });
    }
    await tx.trafficSession.update({
      where: { id: session.id },
      data: {
        uploadedBytes: input.cumulative.uploadedBytes,
        downloadedBytes: input.cumulative.downloadedBytes,
        totalBytes: charge.requestedTotal,
        lastAccountedBytes: charge.requestedTotal,
        lastReportAt: now,
        status,
        ...(status !== "ACTIVE" ? { endedAt: session.endedAt ?? now } : {}),
      },
    });
    await tx.manualServer.update({ where: { id: session.manualServerId }, data: { lastUsedAt: now } });

    return {
      sessionId: session.id,
      acceptedBytes: charge.acceptedBytes,
      totalBytes: charge.requestedTotal,
      remainingBytes: charge.remainingBytes,
      status,
      disconnect: charge.exhausted || status === "REVOKED",
    };
  });
}
