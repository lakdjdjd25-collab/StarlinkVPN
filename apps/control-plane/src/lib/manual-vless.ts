import { lookup } from "node:dns/promises";
import { isIP } from "node:net";
import { singBoxRuntimeConfigSchema } from "@/lib/sing-box-config";

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const SUPPORTED_TRANSPORTS = new Set(["tcp", "ws", "grpc", "http", "h2", "httpupgrade"]);
const SUPPORTED_SECURITY = new Set(["none", "tls", "reality"]);

export type ParsedVless = {
  protocol: "VLESS";
  uuid: string;
  host: string;
  port: number;
  transport: string;
  security: string;
  sni?: string;
  fingerprint?: string;
  path?: string;
  serviceName?: string;
  flow?: string;
  fragment?: string;
  query: Record<string, string>;
  runtimeConfig: Record<string, unknown>;
};

export type GeoCountry = {
  country: string;
  countryCode: string;
  ip: string;
};

function queryObject(params: URLSearchParams): Record<string, string> {
  const result: Record<string, string> = {};
  for (const [key, value] of params.entries()) result[key] = value;
  return result;
}

function transportConfig(parsed: URL, transport: string): Record<string, unknown> | undefined {
  const path = parsed.searchParams.get("path") || undefined;
  const hostHeader = parsed.searchParams.get("host") || undefined;
  const serviceName = parsed.searchParams.get("serviceName") || undefined;
  switch (transport) {
    case "ws":
      return {
        type: "ws",
        ...(path ? { path } : {}),
        ...(hostHeader ? { headers: { Host: hostHeader } } : {}),
      };
    case "grpc":
      return { type: "grpc", ...(serviceName ? { service_name: serviceName } : {}) };
    case "http":
    case "h2":
      return {
        type: "http",
        ...(path ? { path } : {}),
        ...(hostHeader ? { host: [hostHeader] } : {}),
      };
    case "httpupgrade":
      return {
        type: "httpupgrade",
        ...(path ? { path } : {}),
        ...(hostHeader ? { host: hostHeader } : {}),
      };
    default:
      return undefined;
  }
}

export function parseVlessUri(value: string): ParsedVless {
  const raw = value.trim();
  if (!raw.toLowerCase().startsWith("vless://")) throw new Error("VLESS_CONFIG_REQUIRED");
  let parsed: URL;
  try {
    parsed = new URL(raw);
  } catch {
    throw new Error("VLESS_CONFIG_INVALID");
  }
  const uuid = decodeURIComponent(parsed.username || "");
  if (!UUID_PATTERN.test(uuid)) throw new Error("VLESS_UUID_INVALID");
  const host = parsed.hostname;
  const port = Number(parsed.port || 443);
  if (!host || !Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error("VLESS_ENDPOINT_INVALID");
  }
  const transport = (parsed.searchParams.get("type") || "tcp").toLowerCase();
  if (!SUPPORTED_TRANSPORTS.has(transport)) throw new Error("VLESS_TRANSPORT_UNSUPPORTED");
  const security = (parsed.searchParams.get("security") || "none").toLowerCase();
  if (!SUPPORTED_SECURITY.has(security)) throw new Error("VLESS_SECURITY_UNSUPPORTED");
  const sni = parsed.searchParams.get("sni") || parsed.searchParams.get("serverName") || undefined;
  const fingerprint = parsed.searchParams.get("fp") || parsed.searchParams.get("fingerprint") || undefined;
  const flow = parsed.searchParams.get("flow") || undefined;
  const path = parsed.searchParams.get("path") || undefined;
  const serviceName = parsed.searchParams.get("serviceName") || undefined;
  const outbound: Record<string, unknown> = {
    type: "vless",
    tag: "proxy",
    server: host,
    server_port: port,
    uuid,
    ...(flow ? { flow } : {}),
  };
  const transportValue = transportConfig(parsed, transport);
  if (transportValue) outbound.transport = transportValue;
  if (security === "tls" || security === "reality") {
    const tls: Record<string, unknown> = {
      enabled: true,
      ...(sni ? { server_name: sni } : {}),
      ...(fingerprint ? { utls: { enabled: true, fingerprint } } : {}),
    };
    if (security === "reality") {
      const publicKey = parsed.searchParams.get("pbk") || parsed.searchParams.get("publicKey") || "";
      if (!publicKey) throw new Error("VLESS_REALITY_KEY_REQUIRED");
      tls.reality = {
        enabled: true,
        public_key: publicKey,
        short_id: parsed.searchParams.get("sid") || parsed.searchParams.get("shortId") || "",
      };
    }
    outbound.tls = tls;
  }
  const runtimeConfig = {
    log: { level: "info", timestamp: true },
    dns: { servers: [{ type: "tls", tag: "remote-dns", server: "1.1.1.1" }] },
    inbounds: [{
      type: "tun",
      tag: "tun-in",
      address: ["172.19.0.1/30", "fdfe:dcba:9876::1/126"],
      auto_route: true,
      strict_route: true,
      stack: "mixed",
    }],
    outbounds: [outbound],
    route: {
      rules: [{ action: "sniff" }, { protocol: "dns", action: "hijack-dns" }],
      auto_detect_interface: true,
      final: "proxy",
    },
  };
  const checked = singBoxRuntimeConfigSchema.safeParse(runtimeConfig);
  if (!checked.success) throw new Error("VLESS_RUNTIME_INVALID");
  return {
    protocol: "VLESS",
    uuid,
    host,
    port,
    transport,
    security,
    sni,
    fingerprint,
    path,
    serviceName,
    flow,
    fragment: parsed.hash ? decodeURIComponent(parsed.hash.slice(1)) : undefined,
    query: queryObject(parsed.searchParams),
    runtimeConfig: checked.data,
  };
}

export async function resolveHostIp(host: string): Promise<string | null> {
  if (isIP(host)) return host;
  return lookup(host).then(({ address }) => address).catch(() => null);
}

export function countryFlag(countryCode: string | null | undefined): string {
  const code = countryCode?.trim().toUpperCase();
  if (!code || !/^[A-Z]{2}$/.test(code)) return "🌐";
  return String.fromCodePoint(...[...code].map((char) => 127397 + char.charCodeAt(0)));
}
