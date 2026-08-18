import { describe, expect, it } from "vitest";
import { calculateTrafficCharge, ManualTrafficError } from "./manual-traffic";

describe("manual traffic accounting", () => {
  it("charges only the cumulative delta", () => {
    const result = calculateTrafficCharge({
      previousAccountedTotal: 300n,
      uploadedBytes: 250n,
      downloadedBytes: 200n,
      remainingBytes: 1_000n,
      countTraffic: true,
    });
    expect(result.delta).toBe(150n);
    expect(result.acceptedBytes).toBe(150n);
    expect(result.remainingBytes).toBe(850n);
    expect(result.exhausted).toBe(false);
  });

  it("is idempotent when the same cumulative counters are reported again", () => {
    const result = calculateTrafficCharge({
      previousAccountedTotal: 450n,
      uploadedBytes: 250n,
      downloadedBytes: 200n,
      remainingBytes: 850n,
      countTraffic: true,
    });
    expect(result.delta).toBe(0n);
    expect(result.acceptedBytes).toBe(0n);
    expect(result.remainingBytes).toBe(850n);
  });

  it("caps accepted usage at the shared remaining quota", () => {
    const result = calculateTrafficCharge({
      previousAccountedTotal: 0n,
      uploadedBytes: 700n,
      downloadedBytes: 500n,
      remainingBytes: 1_000n,
      countTraffic: true,
    });
    expect(result.delta).toBe(1_200n);
    expect(result.acceptedBytes).toBe(1_000n);
    expect(result.remainingBytes).toBe(0n);
    expect(result.exhausted).toBe(true);
  });

  it("keeps quota unchanged when traffic counting is disabled", () => {
    const result = calculateTrafficCharge({
      previousAccountedTotal: 100n,
      uploadedBytes: 300n,
      downloadedBytes: 400n,
      remainingBytes: 900n,
      countTraffic: false,
    });
    expect(result.delta).toBe(600n);
    expect(result.acceptedBytes).toBe(0n);
    expect(result.remainingBytes).toBe(900n);
    expect(result.exhausted).toBe(false);
  });

  it("rejects cumulative counter regression", () => {
    expect(() => calculateTrafficCharge({
      previousAccountedTotal: 600n,
      uploadedBytes: 200n,
      downloadedBytes: 300n,
      remainingBytes: 1_000n,
      countTraffic: true,
    })).toThrowError(ManualTrafficError);
  });
});
