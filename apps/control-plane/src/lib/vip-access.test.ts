import { describe, expect, it } from "vitest";
import { canAccessTier, filterAccessibleNodes } from "@/lib/vip-access";

describe("VIP access policy", () => {
  it("allows STANDARD nodes for every service", () => {
    expect(canAccessTier(false, "STANDARD")).toBe(true);
    expect(canAccessTier(true, "STANDARD")).toBe(true);
  });

  it("blocks VIP nodes unless the service has VIP entitlement", () => {
    expect(canAccessTier(false, "VIP")).toBe(false);
    expect(canAccessTier(true, "VIP")).toBe(true);
  });

  it("filters VIP metadata out of STANDARD bootstrap responses", () => {
    const nodes = [
      { id: "standard", accessTier: "STANDARD" as const },
      { id: "vip", accessTier: "VIP" as const },
    ];
    expect(filterAccessibleNodes(nodes, false).map((item) => item.id)).toEqual(["standard"]);
    expect(filterAccessibleNodes(nodes, true).map((item) => item.id)).toEqual(["standard", "vip"]);
  });
});
