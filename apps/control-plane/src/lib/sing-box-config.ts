import { z } from "zod";

const jsonObject = z.record(z.string(), z.unknown());
const remoteOutboundTypes = new Set([
  "anytls",
  "hysteria",
  "hysteria2",
  "naive",
  "shadowsocks",
  "ssh",
  "trojan",
  "tuic",
  "vless",
  "vmess",
  "wireguard",
]);

export const singBoxRuntimeConfigSchema = z.object({
  inbounds: z.array(jsonObject).min(1),
  outbounds: z.array(jsonObject).min(1),
  endpoints: z.array(jsonObject).optional(),
}).passthrough().superRefine((config, context) => {
  const hasTunInbound = config.inbounds.some((inbound) => inbound.type === "tun");
  if (!hasTunInbound) {
    context.addIssue({
      code: "custom",
      path: ["inbounds"],
      message: "A tun inbound is required for the Android client",
    });
  }
  const hasRemoteOutbound = config.outbounds.some((outbound) =>
    typeof outbound.type === "string" && remoteOutboundTypes.has(outbound.type),
  ) || config.endpoints?.some((endpoint) => endpoint.type === "wireguard");
  if (!hasRemoteOutbound) {
    context.addIssue({
      code: "custom",
      path: ["outbounds"],
      message: "A remote proxy outbound is required; direct-only configurations are unsafe",
    });
  }
});

export const defaultSingBoxRuntimeConfig = {
  log: {
    level: "info",
    timestamp: true,
  },
  dns: {
    servers: [
      {
        type: "tls",
        tag: "remote-dns",
        server: "1.1.1.1",
      },
    ],
  },
  inbounds: [
    {
      type: "tun",
      tag: "tun-in",
      address: ["172.19.0.1/30", "fdfe:dcba:9876::1/126"],
      auto_route: true,
      strict_route: true,
      stack: "mixed",
    },
  ],
  outbounds: [
    {
      type: "vless",
      tag: "proxy",
      server: "vpn.example.com",
      server_port: 443,
      uuid: "00000000-0000-4000-8000-000000000000",
      tls: {
        enabled: true,
        server_name: "vpn.example.com",
      },
    },
  ],
  route: {
    rules: [
      { action: "sniff" },
      { protocol: "dns", action: "hijack-dns" },
    ],
    auto_detect_interface: true,
    final: "proxy",
  },
};
