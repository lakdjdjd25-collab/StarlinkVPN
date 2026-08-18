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
    expect(parsed.path).toBe("/ws");
    expect(parsed.fragment).toBe("Germany");
    expect(parsed.runtimeConfig).toBeTypeOf("object");
  });

  it("supports an IP endpoint and keeps unknown query parameters", () => {
    const parsed = parseVlessUri(
      "vless://11111111-1111-4111-8111-111111111111@203.0.113.9:8443?security=none&type=tcp&vendorFlag=alpha#IP%20Node",
    );
    expect(parsed.host).toBe("203.0.113.9");
    expect(parsed.port).toBe(8443);
    expect(parsed.security).toBe("none");
    expect(parsed.query.vendorFlag).toBe("alpha");
    expect(parsed.fragment).toBe("IP Node");
  });

  it("parses Reality parameters without logging or exposing the source URI", () => {
    const parsed = parseVlessUri(
      "vless://11111111-1111-4111-8111-111111111111@reality.example.com:443?type=tcp&security=reality&sni=www.example.com&fp=chrome&pbk=public-key-value&sid=abcd",
    );
    expect(parsed.security).toBe("reality");
    expect(parsed.sni).toBe("www.example.com");
    expect(parsed.fingerprint).toBe("chrome");
    expect(JSON.stringify(parsed.runtimeConfig)).toContain("public-key-value");
  });

  it("parses gRPC service name", () => {
    const parsed = parseVlessUri(
      "vless://11111111-1111-4111-8111-111111111111@example.com:443?type=grpc&security=tls&serviceName=nimhub&sni=example.com",
    );
    expect(parsed.transport).toBe("grpc");
    expect(parsed.serviceName).toBe("nimhub");
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

  it("rejects unsupported transport without accepting an unusable config", () => {
    expect(() => parseVlessUri(
      "vless://11111111-1111-4111-8111-111111111111@example.com:443?type=kcp&security=none",
    )).toThrow("VLESS_TRANSPORT_UNSUPPORTED");
  });
});
