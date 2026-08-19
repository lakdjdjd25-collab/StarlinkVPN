export type PasarGuardLogicalIdentity = {
  providerTag?: string | null;
  name?: string | null;
  host: string;
  port: number;
  protocol: string;
};

export function pasarGuardLogicalNodeKey(node: PasarGuardLogicalIdentity): string {
  const tag = (node.providerTag?.trim() || node.name?.trim() || "unknown").toLowerCase();
  return `${tag}|${node.host.trim().toLowerCase()}|${node.port}|${node.protocol.toUpperCase()}`;
}

export type AdminManagedNodeSource = PasarGuardLogicalIdentity & {
  id: string;
  provider: string;
  accessTier: "STANDARD" | "VIP";
  status: string;
  regionName: string;
  countryCode: string;
  capacity: number;
  activeSessions: number;
  lastSeenAt: Date | null;
  lastSyncAt: Date | null;
  assignments: Array<{ userId: string; maxDevices: number }>;
};

export type AdminManagedNodeRow = {
  id: string;
  name: string;
  provider: string;
  providerTag: string | null;
  isPasarGuard: boolean;
  logicalCopies: number;
  accessTier: "STANDARD" | "VIP";
  mixedAccessTier: boolean;
  status: string;
  mixedStatus: boolean;
  regionName: string;
  countryCode: string;
  protocol: string;
  host: string;
  port: number;
  assignedUsers: number;
  allowedDevices: number;
  capacity: number | null;
  activeSessions: number | null;
  lastSeenAt: Date | null;
  lastSyncAt: Date | null;
};

function newest(values: Array<Date | null>): Date | null {
  return values.reduce<Date | null>((latest, value) => {
    if (!value) return latest;
    if (!latest || value.getTime() > latest.getTime()) return value;
    return latest;
  }, null);
}

function groupedRow(nodes: AdminManagedNodeSource[]): AdminManagedNodeRow {
  const first = nodes[0];
  const isPasarGuard = first.provider.toUpperCase() === "PASARGUARD";
  const tiers = new Set(nodes.map((node) => node.accessTier));
  const statuses = new Set(nodes.map((node) => node.status));
  const accessTier: "STANDARD" | "VIP" = tiers.size === 1 && tiers.has("VIP") ? "VIP" : "STANDARD";
  const status = statuses.size === 1 ? first.status : "DEGRADED";

  const users = new Map<string, number>();
  for (const node of nodes) {
    for (const assignment of node.assignments) {
      users.set(assignment.userId, Math.max(users.get(assignment.userId) ?? 0, assignment.maxDevices));
    }
  }

  return {
    id: first.id,
    name: first.name ?? first.providerTag ?? "PasarGuard",
    provider: first.provider,
    providerTag: first.providerTag ?? null,
    isPasarGuard,
    logicalCopies: nodes.length,
    accessTier,
    mixedAccessTier: tiers.size > 1,
    status,
    mixedStatus: statuses.size > 1,
    regionName: first.regionName,
    countryCode: first.countryCode,
    protocol: first.protocol,
    host: first.host,
    port: first.port,
    assignedUsers: users.size,
    allowedDevices: [...users.values()].reduce((sum, value) => sum + value, 0),
    capacity: isPasarGuard ? null : first.capacity,
    activeSessions: isPasarGuard ? null : first.activeSessions,
    lastSeenAt: newest(nodes.map((node) => node.lastSeenAt)),
    lastSyncAt: newest(nodes.map((node) => node.lastSyncAt)),
  };
}

export function groupManagedNodesForAdmin(nodes: AdminManagedNodeSource[]): AdminManagedNodeRow[] {
  const groups = new Map<string, AdminManagedNodeSource[]>();

  for (const node of nodes) {
    const isPasarGuard = node.provider.toUpperCase() === "PASARGUARD";
    const key = isPasarGuard ? `pasarguard:${pasarGuardLogicalNodeKey(node)}` : `node:${node.id}`;
    const current = groups.get(key);
    if (current) current.push(node);
    else groups.set(key, [node]);
  }

  return [...groups.values()].map(groupedRow);
}
