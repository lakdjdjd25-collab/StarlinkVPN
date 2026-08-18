import { describe, expect, it } from "vitest";
import { MANUAL_TRAFFIC_CAPABILITY_HEADER, supportsManualTraffic } from "./manual-client-capability";

describe("Manual Server client capability", () => {
  it("rejects clients that do not advertise traffic accounting support", () => {
    expect(supportsManualTraffic({ headers: new Headers() })).toBe(false);
  });

  it("accepts the current Android Manual Server capability", () => {
    const headers = new Headers({ [MANUAL_TRAFFIC_CAPABILITY_HEADER]: "1" });
    expect(supportsManualTraffic({ headers })).toBe(true);
  });

  it("does not accept arbitrary capability values", () => {
    const headers = new Headers({ [MANUAL_TRAFFIC_CAPABILITY_HEADER]: "true" });
    expect(supportsManualTraffic({ headers })).toBe(false);
  });
});
