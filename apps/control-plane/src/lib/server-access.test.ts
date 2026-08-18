import { describe, expect, it } from "vitest";
import { effectiveUsedBytes, remainingServiceBytes, serverAccessState } from "./server-access";

describe("shared server access policy", () => {
  it("adds manual traffic to the existing service usage", () => {
    const service = { quotaBytes: 1_000n, usedBytes: 250n, manualUsedBytes: 125n };
    expect(effectiveUsedBytes(service)).toBe(375n);
    expect(remainingServiceBytes(service)).toBe(625n);
  });

  it("never reports negative remaining quota", () => {
    expect(remainingServiceBytes({ quotaBytes: 100n, usedBytes: 90n, manualUsedBytes: 25n })).toBe(0n);
  });

  it("exposes VIP as visible but locked without entitlement", () => {
    expect(serverAccessState(false, "VIP")).toEqual({
      requiresVip: true,
      locked: true,
      canConnect: false,
    });
    expect(serverAccessState(true, "VIP")).toEqual({
      requiresVip: true,
      locked: false,
      canConnect: true,
    });
  });
});
