export type VipAccessTier = "STANDARD" | "VIP";

export function canAccessTier(vipAccess: boolean, accessTier: VipAccessTier): boolean {
  return accessTier === "STANDARD" || vipAccess;
}

export function filterAccessibleNodes<T extends { accessTier: VipAccessTier }>(
  nodes: T[],
  vipAccess: boolean,
): T[] {
  return nodes.filter((node) => canAccessTier(vipAccess, node.accessTier));
}
