import { describe, expect, it } from "vitest";
import {
  generateManagedPassword,
  isPublicManagedEmail,
  managedIdentity,
} from "./managed-account";

describe("managed NimHUB accounts", () => {
  it("derives stable random-looking login identifiers from an idempotency key", () => {
    const first = managedIdentity("4a9e685e-4ee4-4ff0-b21d-45350c1cb1fb");
    const repeated = managedIdentity("4a9e685e-4ee4-4ff0-b21d-45350c1cb1fb");
    const other = managedIdentity("3a69e777-4134-4117-b86e-e14ae657aac0");

    expect(first).toEqual(repeated);
    expect(first).not.toEqual(other);
    expect(first.email).toMatch(/^user[a-f0-9]{12}@nimhub\.com$/);
    expect(first.remoteUsername).toMatch(/^nh_[a-f0-9]{24}$/);
    expect(isPublicManagedEmail(first.email)).toBe(true);
    expect(isPublicManagedEmail("pg-test@license.nimhub.local")).toBe(false);
  });

  it("creates an eight-character, easy-to-type mixed password", () => {
    for (let attempt = 0; attempt < 32; attempt += 1) {
      const password = generateManagedPassword();
      expect(password).toHaveLength(8);
      expect(password).toMatch(/[A-Z]/);
      expect(password).toMatch(/[a-z]/);
      expect(password).toMatch(/[2-9]/);
      expect(password).toMatch(/^[A-HJ-NP-Za-km-z2-9]+$/);
    }
  });
});
