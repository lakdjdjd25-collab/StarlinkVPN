import { describe, expect, it } from "vitest";
import { overrideVlessEndpoint, parseVlessUri } from "./manual-vless";

const REALITY_PUBLIC_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

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

  it("preserves websocket early-data, ALPN, insecure and packet encoding options", () => {
    const parsed = parseVlessUri(
      "vless://11111111-1111-4111-8111-111111111111@example.com:443?type=ws&security=tls&sni=edge.example.com&path=%2Fws&ed=2048&eh=Sec-WebSocket-Protocol&alpn=h2%2Chttp%2F1.1&allowInsecure=1&packetEncoding=xudp",
    );
    const outbound = (parsed.runtimeConfig.outbounds as Array<Record<string, unknown>>)[0];
    const transport = outbound.transport as Record<string, unknown>;
    const tls = outbound.tls as Record<string, unknown>;
    expect(transport.max_early_data).toBe(2048);
    expect(transport.early_data_header_name).toBe("Sec-WebSocket-Protocol");
    expect(tls.insecure).toBe(true);
    expect(tls.alpn).toEqual(["h2", "http/1.1"]);
    expect(outbound.packet_encoding).toBe("xudp");
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

  it("parses Reality parameters and defaults uTLS fingerprint when omitted", () => {
    const parsed = parseVlessUri(
      `vless://11111111-1111-4111-8111-111111111111@reality.example.com:443?type=tcp&security=reality&sni=www.example.com&pbk=${REALITY_PUBLIC_KEY}&sid=abcd`,
    );
    expect(parsed.security).toBe("reality");
    expect(parsed.sni).toBe("www.example.com");
    expect(parsed.fingerprint).toBe("chrome");
    const outbound = (parsed.runtimeConfig.outbounds as Array<Record<string, unknown>>)[0];
    const tls = outbound.tls as Record<string, unknown>;
    expect(tls.utls).toEqual({ enabled: true, fingerprint: "chrome" });
    expect(tls.reality).toEqual({ enabled: true, public_key: REALITY_PUBLIC_KEY, short_id: "abcd" });
  });

  it("parses gRPC service name", () => {
    const parsed = parseVlessUri(
      "vless://11111111-1111-4111-8111-111111111111@example.com:443?type=grpc&security=tls&serviceName=nimhub&sni=example.com",
    );
    expect(parsed.transport).toBe("grpc");
    expect(parsed.serviceName).toBe("nimhub");
  });

  it("overrides the connection address and port without changing TLS metadata", () => {
    const parsed = parseVlessUri(
      "vless://11111111-1111-4111-8111-111111111111@origin.example.com:443?type=tcp&security=tls&sni=edge.example.com",
    );
    const runtime = overrideVlessEndpoint(parsed.runtimeConfig, "198.51.100.20", 8443);
    const outbound = (runtime.outbounds as Array<Record<string, unknown>>)[0];
    expect(outbound.server).toBe("198.51.100.20");
    expect(outbound.server_port).toBe(8443);
    expect((outbound.tls as Record<string, unknown>).server_name).toBe("edge.example.com");
  });

  it("rejects invalid endpoint overrides", () => {
    const parsed = parseVlessUri(
      "vless://11111111-1111-4111-8111-111111111111@example.com:443?security=none",
    );
    expect(() => overrideVlessEndpoint(parsed.runtimeConfig, "bad host", 70000))
      .toThrow("VLESS_ENDPOINT_INVALID");
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

  it("rejects malformed Reality public keys before they reach Android", () => {
    expect(() => parseVlessUri(
      "vless://11111111-1111-4111-8111-111111111111@example.com:443?type=tcp&security=reality&sni=edge.example.com&pbk=bad-key&sid=abcd",
    )).toThrow("VLESS_REALITY_KEY_INVALID");
  });

  it("rejects malformed Reality short IDs before they reach Android", () => {
    expect(() => parseVlessUri(
      `vless://11111111-1111-4111-8111-111111111111@example.com:443?type=tcp&security=reality&sni=edge.example.com&pbk=${REALITY_PUBLIC_KEY}&sid=abc`,
    )).toThrow("VLESS_REALITY_SHORT_ID_INVALID");
  });

  it("rejects unsupported packet encodings", () => {
    expect(() => parseVlessUri(
      "vless://11111111-1111-4111-8111-111111111111@example.com:443?type=tcp&security=none&packetEncoding=unknown",
    )).toThrow("VLESS_PACKET_ENCODING_UNSUPPORTED");
  });

  it("rejects unsupported transport without accepting an unusable config", () => {
    expect(() => parseVlessUri(
      "vless://11111111-1111-4111-8111-111111111111@example.com:443?type=kcp&security=none",
    )).toThrow("VLESS_TRANSPORT_UNSUPPORTED");
  });
});
