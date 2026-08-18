import { describe, expect, it } from "vitest";
import { adminAccountTransition } from "./admin-v2-account-status";

describe("Admin V2 account status contract", () => {
  it("keeps service status independent when suspending an account", () => {
    expect(adminAccountTransition("ACTIVE", "SUSPENDED")).toEqual({
      changed: true,
      revokeSessions: true,
      serviceStatusesChanged: false,
    });
  });

  it("reactivates only the account and does not mutate services", () => {
    expect(adminAccountTransition("SUSPENDED", "ACTIVE")).toEqual({
      changed: true,
      revokeSessions: false,
      serviceStatusesChanged: false,
    });
  });

  it("is a no-op when the requested account status already matches", () => {
    expect(adminAccountTransition("ACTIVE", "ACTIVE")).toEqual({
      changed: false,
      revokeSessions: false,
      serviceStatusesChanged: false,
    });
  });
});
