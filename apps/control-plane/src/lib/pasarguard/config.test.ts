import { describe, expect, it } from "vitest";
import { normalizePasarGuardConfig } from "./config";

describe("PasarGuard sing-box normalization", () => {
  it("discovers every remote outbound and produces an Android TUN config per server", () => {
    const normalized = normalizePasarGuardConfig({
      dns: {
        servers: [{ type: "udp", tag: "remote", server: "1.1.1.1", detour: "proxy" }],
      },
      inbounds: [],
      outbounds: [
        { type: "selector", tag: "proxy", outbounds: ["🇩🇪 DE-1", "NL-2"] },
        { type: "vless", tag: "🇩🇪 DE-1", server: "de.example.com", server_port: 443, uuid: "id" },
        { type: "trojan", tag: "NL-2", server: "nl.example.com", server_port: 8443, password: "secret" },
        { type: "direct", tag: "direct" },
      ],
      route: { final: "proxy", rules: [{ protocol: "dns", action: "hijack-dns" }] },
    });

    expect(normalized.nodes).toHaveLength(2);
    expect(normalized.nodes[0]).toMatchObject({
      tag: "🇩🇪 DE-1",
      countryCode: "de",
      host: "de.example.com",
      port: 443,
      protocol: "VLESS",
    });
    expect(normalized.nodes[0].runtimeConfig.inbounds).toEqual([
      expect.objectContaining({ type: "tun", auto_route: true, strict_route: true }),
    ]);
    expect(normalized.nodes[0].runtimeConfig.route).toMatchObject({ final: "🇩🇪 DE-1" });
    expect(normalized.nodes[0].runtimeConfig.dns).toMatchObject({
      servers: [expect.objectContaining({ detour: "🇩🇪 DE-1" })],
    });
    expect(normalized.nodes[1]).toMatchObject({ countryCode: "nl", protocol: "TROJAN" });
  });

  it("supports modern sing-box WireGuard endpoints", () => {
    const normalized = normalizePasarGuardConfig({
      inbounds: [{ type: "tun", address: ["172.19.0.1/30"], auto_route: true }],
      outbounds: [{ type: "selector", tag: "proxy", outbounds: ["WG"] }, { type: "direct", tag: "direct" }],
      endpoints: [{
        type: "wireguard",
        tag: "WG",
        address: ["10.0.0.2/32"],
        private_key: "private",
        peers: [{ address: "wg.example.com", port: 51820, public_key: "public" }],
      }],
      route: { final: "proxy" },
    });
    expect(normalized.nodes).toEqual([
      expect.objectContaining({ tag: "WG", host: "wg.example.com", port: 51820, protocol: "WIREGUARD" }),
    ]);
  });
});
