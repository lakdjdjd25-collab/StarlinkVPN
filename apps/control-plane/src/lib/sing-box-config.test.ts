import { describe, expect, it } from "vitest";
import { defaultSingBoxRuntimeConfig, singBoxRuntimeConfigSchema } from "./sing-box-config";

describe("sing-box runtime configuration", () => {
  it("accepts the complete Android template without stripping fields", () => {
    const parsed = singBoxRuntimeConfigSchema.parse(defaultSingBoxRuntimeConfig);
    expect(parsed.inbounds[0]).toMatchObject({ type: "tun", auto_route: true });
    expect(parsed.route).toMatchObject({ auto_detect_interface: true, final: "proxy" });
  });

  it("rejects configs that cannot create an Android TUN", () => {
    const result = singBoxRuntimeConfigSchema.safeParse({
      inbounds: [{ type: "mixed", listen: "127.0.0.1", listen_port: 1080 }],
      outbounds: [{ type: "direct" }],
    });
    expect(result.success).toBe(false);
  });
});
