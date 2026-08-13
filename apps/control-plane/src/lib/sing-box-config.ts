import { z } from "zod";

const jsonObject = z.record(z.string(), z.unknown());

export const singBoxRuntimeConfigSchema = z.object({
  inbounds: z.array(jsonObject).min(1),
  outbounds: z.array(jsonObject).min(1),
}).passthrough().superRefine((config, context) => {
  const hasTunInbound = config.inbounds.some((inbound) => inbound.type === "tun");
  if (!hasTunInbound) {
    context.addIssue({
      code: "custom",
      path: ["inbounds"],
      message: "A tun inbound is required for the Android client",
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
      type: "direct",
      tag: "proxy",
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
