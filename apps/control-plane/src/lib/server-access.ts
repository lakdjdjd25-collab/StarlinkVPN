import { canAccessTier, type VipAccessTier } from "./vip-access";

export type ServiceAccessShape = {
  status: "ACTIVE" | "EXPIRED" | "SUSPENDED" | "CANCELLED";
  quotaBytes: bigint;
  usedBytes: bigint;
  manualUsedBytes?: bigint;
  expiresAt: Date;
  vipAccess: boolean;
};

export type UserAccessShape = {
  status: "ACTIVE" | "SUSPENDED" | "DELETED";
};

export function effectiveUsedBytes(service: Pick<ServiceAccessShape, "usedBytes" | "manualUsedBytes">): bigint {
  return service.usedBytes + (service.manualUsedBytes ?? 0n);
}

export function remainingServiceBytes(
  service: Pick<ServiceAccessShape, "quotaBytes" | "usedBytes" | "manualUsedBytes">,
): bigint {
  const remaining = service.quotaBytes - effectiveUsedBytes(service);
  return remaining > 0n ? remaining : 0n;
}

export type ServiceAccessFailure =
  | "account_unavailable"
  | "service_unavailable"
  | "service_expired"
  | "quota_exhausted";

export function serviceAccessFailure(
  user: UserAccessShape,
  service: ServiceAccessShape,
  now = new Date(),
): ServiceAccessFailure | null {
  if (user.status !== "ACTIVE") return "account_unavailable";
  if (service.status !== "ACTIVE") return "service_unavailable";
  if (service.expiresAt.getTime() <= now.getTime()) return "service_expired";
  if (remainingServiceBytes(service) <= 0n) return "quota_exhausted";
  return null;
}

export function serverAccessState(vipAccess: boolean, accessTier: VipAccessTier) {
  const allowed = canAccessTier(vipAccess, accessTier);
  return {
    requiresVip: accessTier === "VIP",
    locked: !allowed,
    canConnect: allowed,
  };
}
