import { describe, expect, it } from "vitest";
import { migrationTransferShape } from "./migration-shape";

const GB = 1024n ** 3n;

describe("PasarGuard provider migration traffic continuity", () => {
  it("moves only the remaining allowance while preserving already-consumed traffic", () => {
    const transfer = migrationTransferShape(100n * GB, 37n * GB, 0n);
    expect(transfer.usageOffsetBytes).toBe(37n * GB);
    expect(transfer.remoteDataLimitBytes).toBe(63n * GB);
    expect(transfer.usageOffsetBytes + transfer.remoteDataLimitBytes).toBe(100n * GB);
  });

  it("accounts for an existing unbound user counter on the new provider", () => {
    const transfer = migrationTransferShape(100n * GB, 37n * GB, 5n * GB);
    expect(transfer.usageOffsetBytes).toBe(32n * GB);
    expect(transfer.remoteDataLimitBytes).toBe(68n * GB);
    expect(transfer.usageOffsetBytes + 5n * GB).toBe(37n * GB);
    expect(transfer.remoteDataLimitBytes - 5n * GB).toBe(63n * GB);
  });

  it("refuses an ambiguous remote counter instead of silently changing usage", () => {
    expect(() => migrationTransferShape(100n * GB, 10n * GB, 12n * GB)).toThrow();
  });
});
