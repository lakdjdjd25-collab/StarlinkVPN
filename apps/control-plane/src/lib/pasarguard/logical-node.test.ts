import { describe, expect, it } from "vitest";
import { groupManagedNodesForAdmin, pasarGuardLogicalNodeKey, type AdminManagedNodeSource } from "./logical-node";

function node(overrides: Partial<AdminManagedNodeSource> = {}): AdminManagedNodeSource {
  return {
    id: "node-1",
    provider: "PASARGUARD",
    providerTag: "🇩🇪 Germany 01",
    name: "🇩🇪 Germany 01",
    host: "de.example.com",
    port: 443,
    protocol: "VLESS",
    accessTier: "STANDARD",
    status: "ONLINE",
    regionName: "آلمان",
    countryCode: "de",
    capacity: 1000,
    activeSessions: 0,
    lastSeenAt: new Date("2026-08-19T09:00:00Z"),
    lastSyncAt: new Date("2026-08-19T09:00:00Z"),
    assignments: [{ userId: "user-1", maxDevices: 3 }],
    ...overrides,
  };
}

describe("PasarGuard logical server grouping", () => {
  it("builds a stable key from provider tag and endpoint", () => {
    expect(pasarGuardLogicalNodeKey(node())).toBe("🇩🇪 germany 01|de.example.com|443|VLESS");
  });

  it("collapses per-user copies of the same PasarGuard server", () => {
    const copies = Array.from({ length: 200 }, (_, index) => node({
      id: `node-${index}`,
      assignments: [{ userId: `user-${index}`, maxDevices: 3 }],
    }));
    const rows = groupManagedNodesForAdmin(copies);
    expect(rows).toHaveLength(1);
    expect(rows[0]).toMatchObject({
      isPasarGuard: true,
      logicalCopies: 200,
      assignedUsers: 200,
      allowedDevices: 600,
      capacity: null,
      activeSessions: null,
    });
  });

  it("keeps different endpoints or tags as separate logical servers", () => {
    const rows = groupManagedNodesForAdmin([
      node({ id: "de-1" }),
      node({ id: "nl-1", providerTag: "🇳🇱 Netherlands 01", name: "🇳🇱 Netherlands 01", host: "nl.example.com", countryCode: "nl", regionName: "هلند" }),
    ]);
    expect(rows).toHaveLength(2);
  });

  it("never groups normal managed nodes together", () => {
    const rows = groupManagedNodesForAdmin([
      node({ id: "manual-a", provider: "MANUAL", providerTag: null }),
      node({ id: "manual-b", provider: "MANUAL", providerTag: null }),
    ]);
    expect(rows).toHaveLength(2);
  });

  it("surfaces mixed legacy VIP state instead of hiding it", () => {
    const [row] = groupManagedNodesForAdmin([
      node({ id: "copy-a", accessTier: "VIP" }),
      node({ id: "copy-b", accessTier: "STANDARD" }),
    ]);
    expect(row.mixedAccessTier).toBe(true);
    expect(row.accessTier).toBe("STANDARD");
  });
});
