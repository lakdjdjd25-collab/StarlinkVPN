import { describe, expect, it } from "vitest";
import { licenseQrPayload, normalizeLicense } from "./license";
import { managedIdentity } from "./managed-account";
import {
  effectiveUsedBytes,
  remainingServiceBytes,
  serverAccessState,
  serviceAccessFailure,
} from "./server-access";

describe("Admin V2 protected client contracts", () => {
  it("preserves license normalization and the Android QR payload contract", () => {
    const license = normalizeLicense("  nh-abcd-2345-efgh-6789  ");
    expect(license).toBe("NH-ABCD-2345-EFGH-6789");
    expect(licenseQrPayload(license)).toBe("NIMHUB:NH-ABCD-2345-EFGH-6789");
  });

  it("preserves deterministic managed NimHUB identities", () => {
    const key = "4a9e685e-4ee4-4ff0-b21d-45350c1cb1fb";
    const first = managedIdentity(key);
    expect(managedIdentity(key)).toEqual(first);
    expect(first.email).toMatch(/^user[a-f0-9]{12}@nimhub\.com$/);
    expect(first.remoteUsername).toMatch(/^nh_[a-f0-9]{24}$/);
  });

  it("keeps STANDARD servers connectable regardless of VIP entitlement", () => {
    expect(serverAccessState(false, "STANDARD")).toEqual({
      requiresVip: false,
      locked: false,
      canConnect: true,
    });
    expect(serverAccessState(true, "STANDARD")).toEqual({
      requiresVip: false,
      locked: false,
      canConnect: true,
    });
  });

  it("keeps VIP authorization strict", () => {
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

  it("uses one shared usage definition for managed and manual traffic", () => {
    const service = { quotaBytes: 10_000n, usedBytes: 2_500n, manualUsedBytes: 1_500n };
    expect(effectiveUsedBytes(service)).toBe(4_000n);
    expect(remainingServiceBytes(service)).toBe(6_000n);
  });

  it("preserves independent access failures for account, service, expiry and quota", () => {
    const now = new Date("2026-08-18T12:00:00.000Z");
    const base = {
      status: "ACTIVE" as const,
      quotaBytes: 1_000n,
      usedBytes: 200n,
      manualUsedBytes: 100n,
      expiresAt: new Date("2026-08-19T12:00:00.000Z"),
      vipAccess: false,
    };

    expect(serviceAccessFailure({ status: "ACTIVE" }, base, now)).toBeNull();
    expect(serviceAccessFailure({ status: "SUSPENDED" }, base, now)).toBe("account_unavailable");
    expect(serviceAccessFailure({ status: "ACTIVE" }, { ...base, status: "SUSPENDED" }, now))
      .toBe("service_unavailable");
    expect(serviceAccessFailure({ status: "ACTIVE" }, { ...base, expiresAt: now }, now))
      .toBe("service_expired");
    expect(serviceAccessFailure({ status: "ACTIVE" }, {
      ...base,
      usedBytes: 900n,
      manualUsedBytes: 100n,
    }, now)).toBe("quota_exhausted");
  });
});
