import { describe, expect, it } from "vitest";
import { parseVlessUri } from "./manual-vless";

describe("manual VLESS parser", () => {
  it("parses a supported VLESS TLS websocket link into sing-box runtime config", () => {
    const parsed = parseVlessUri(
      "vless://11111111-1111-4111-8111-111111111111@example.com:443?type=ws&security=tls&sni=edge.example.com&path=%2Fws#Germany",
    );
    expect(parsed.protocol).toBe("VLESS");
    expect(parsed.host).toBe("example.com");
    expect(parsed.port).toBe(443);
    expect(parsed.transport).toBe("ws");
    expect(parsed.security).toBe("tls");
    expect(parsed.sni).toBe("edge.example.com");
    expect(parsed.fragment).toBe("Germany");
    expect(parsed.runtimeConfig).toBeTypeOf("object");
  });

  it("rejects an invalid VLESS UUID", () => {
    expect(() => parseVlessUri("vless://bad@example.com:443?security=tls"))
      .toThrow("VLESS_UUID_INVALID");
  });

  it("requires a Reality public key", () => {
    expect(() => parseVlessUri(
      "vless://11111111-1111-4111-8111-111111111111@example.com:443?type=tcp&security=reality&sni=edge.example.com",
    )).toThrow("VLESS_REALITY_KEY_REQUIRED");
  });
});
