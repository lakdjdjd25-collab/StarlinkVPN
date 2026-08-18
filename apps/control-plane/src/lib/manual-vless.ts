import { lookup } from "node:dns/promises";
import { isIP } from "node:net";
import { singBoxRuntimeConfigSchema } from "./sing-box-config";

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{12}$/i;
const SUPPORTED_TRANSPORTS = new Set(["tcp", "ws", "grpc", "http", "h2", "httpupgrade"]);
const SUPPORTED_SECURITY = new Set(["none", "tls", "reality"]);
const SUPPORTED_PACKET_ENCODINGS = new Set(["", "xudp", "packetaddr"]);
const REALITY_PUBLIC_KEY_PATTERN = /^[A-Za-z0-9_-]{43}$/;
const REALITY_SHORT_ID_PATTERN = /^(?:[0-9a-fA-F]{2}){0,8}$/;
const GEOIP_TIMEOUT_MS = 2_500;

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

function parseBoolean(value: string | null | undefined): boolean {
  if (!value) return false;
  return ["1", "true", "yes", "on"].includes(value.trim().toLowerCase());
}

function parseAlpn(value: string | null): string[] {
  if (!value) return [];
  return value.split(",").map((item) => item.trim()).filter(Boolean);
}

function transportConfig(parsed: URL, transport: string): Record<string, unknown> | undefined {
  const path = parsed.searchParams.get("path") || undefined;
  const hostHeader = parsed.searchParams.get("host") || undefined;
  const serviceName = parsed.searchParams.get("serviceName") || undefined;
  switch (transport) {
    case "ws": {
      const maxEarlyData = Number(parsed.searchParams.get("ed") || 0);
      const earlyDataHeaderName = parsed.searchParams.get("eh") || undefined;
      return {
        type: "ws",
        ...(path ? { path } : {}),
        ...(hostHeader ? { headers: { Host: hostHeader } } : {}),
        ...(Number.isInteger(maxEarlyData) && maxEarlyData > 0 ? { max_early_data: maxEarlyData } : {}),
        ...(earlyDataHeaderName ? { early_data_header_name: earlyDataHeaderName } : {}),
      };
    }
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

function validateEndpoint(host: string, port: number): { host: string; port: number } {
  const normalizedHost = host.trim().replace(/^\[(.*)]$/, "$1");
  if (!normalizedHost || normalizedHost.length > 253 || /\s/.test(normalizedHost) ||
      !Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error("VLESS_ENDPOINT_INVALID");
  }
  return { host: normalizedHost, port };
}

function validateReality(publicKey: string, shortId: string): void {
  if (!REALITY_PUBLIC_KEY_PATTERN.test(publicKey)) throw new Error("VLESS_REALITY_KEY_INVALID");
  let decoded: Buffer;
  try {
    decoded = Buffer.from(publicKey, "base64url");
  } catch {
    throw new Error("VLESS_REALITY_KEY_INVALID");
  }
  if (decoded.length !== 32) throw new Error("VLESS_REALITY_KEY_INVALID");
  if (!REALITY_SHORT_ID_PATTERN.test(shortId)) throw new Error("VLESS_REALITY_SHORT_ID_INVALID");
}

export function overrideVlessEndpoint(
  runtimeConfig: Record<string, unknown>,
  host: string,
  port: number,
): Record<string, unknown> {
  const endpoint = validateEndpoint(host, port);
  const clone = structuredClone(runtimeConfig) as Record<string, unknown>;
  const outbounds = Array.isArray(clone.outbounds) ? clone.outbounds : [];
  const outbound = outbounds.find((item) =>
    typeof item === "object" && item !== null && (item as Record<string, unknown>).type === "vless",
  );
  if (!outbound || typeof outbound !== "object") throw new Error("VLESS_RUNTIME_INVALID");
  const vless = outbound as Record<string, unknown>;
  vless.server = endpoint.host;
  vless.server_port = endpoint.port;
  const checked = singBoxRuntimeConfigSchema.safeParse(clone);
  if (!checked.success) throw new Error("VLESS_RUNTIME_INVALID");
  return checked.data;
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
  const endpoint = validateEndpoint(parsed.hostname, Number(parsed.port || 443));
  const host = endpoint.host;
  const port = endpoint.port;
  const transport = (parsed.searchParams.get("type") || "tcp").toLowerCase();
  if (!SUPPORTED_TRANSPORTS.has(transport)) throw new Error("VLESS_TRANSPORT_UNSUPPORTED");
  const security = (parsed.searchParams.get("security") || "none").toLowerCase();
  if (!SUPPORTED_SECURITY.has(security)) throw new Error("VLESS_SECURITY_UNSUPPORTED");
  const encryption = (parsed.searchParams.get("encryption") || "none").toLowerCase();
  if (encryption !== "none") throw new Error("VLESS_ENCRYPTION_UNSUPPORTED");
  const sni = parsed.searchParams.get("sni") || parsed.searchParams.get("serverName") || undefined;
  const requestedFingerprint = parsed.searchParams.get("fp") || parsed.searchParams.get("fingerprint") || undefined;
  const fingerprint = requestedFingerprint || (security === "reality" ? "chrome" : undefined);
  const flow = parsed.searchParams.get("flow") || undefined;
  const path = parsed.searchParams.get("path") || undefined;
  const serviceName = parsed.searchParams.get("serviceName") || undefined;
  const packetEncoding = (
    parsed.searchParams.get("packetEncoding") || parsed.searchParams.get("packet_encoding") || ""
  ).toLowerCase();
  if (!SUPPORTED_PACKET_ENCODINGS.has(packetEncoding)) throw new Error("VLESS_PACKET_ENCODING_UNSUPPORTED");

  const outbound: Record<string, unknown> = {
    type: "vless",
    tag: "proxy",
    server: host,
    server_port: port,
    uuid,
    ...(flow ? { flow } : {}),
    ...(packetEncoding ? { packet_encoding: packetEncoding } : {}),
  };
  const transportValue = transportConfig(parsed, transport);
  if (transportValue) outbound.transport = transportValue;
  if (security === "tls" || security === "reality") {
    const alpn = parseAlpn(parsed.searchParams.get("alpn"));
    const tls: Record<string, unknown> = {
      enabled: true,
      ...(sni ? { server_name: sni } : {}),
      ...(parseBoolean(parsed.searchParams.get("allowInsecure") || parsed.searchParams.get("insecure")) ? { insecure: true } : {}),
      ...(alpn.length ? { alpn } : {}),
      ...(fingerprint ? { utls: { enabled: true, fingerprint } } : {}),
    };
    if (security === "reality") {
      const publicKey = parsed.searchParams.get("pbk") || parsed.searchParams.get("publicKey") || "";
      if (!publicKey) throw new Error("VLESS_REALITY_KEY_REQUIRED");
      const shortId = parsed.searchParams.get("sid") || parsed.searchParams.get("shortId") || "";
      validateReality(publicKey, shortId);
      tls.reality = {
        enabled: true,
        public_key: publicKey,
        short_id: shortId,
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

function publicIp(ip: string): boolean {
  if (isIP(ip) === 4) {
    const [a, b] = ip.split(".").map(Number);
    return !(a === 10 || a === 127 || a === 0 || (a === 169 && b === 254) ||
      (a === 172 && b >= 16 && b <= 31) || (a === 192 && b === 168));
  }
  if (isIP(ip) === 6) {
    const value = ip.toLowerCase();
    return value !== "::1" && !value.startsWith("fc") && !value.startsWith("fd") && !value.startsWith("fe80:");
  }
  return false;
}

export async function detectGeoCountry(host: string): Promise<GeoCountry | null> {
  const ip = await resolveHostIp(host);
  if (!ip || !publicIp(ip)) return null;
  try {
    const response = await fetch(`https://ipwho.is/${encodeURIComponent(ip)}?fields=success,country,country_code`, {
      headers: { accept: "application/json", "user-agent": "NimHUB-ControlPlane/2.6" },
      signal: AbortSignal.timeout(GEOIP_TIMEOUT_MS),
      cache: "no-store",
    });
    if (!response.ok) return null;
    const body = await response.json() as { success?: boolean; country?: unknown; country_code?: unknown };
    const country = typeof body.country === "string" ? body.country.trim() : "";
    const countryCode = typeof body.country_code === "string" ? body.country_code.trim().toUpperCase() : "";
    if (body.success === false || !country || !/^[A-Z]{2}$/.test(countryCode)) return null;
    return { country, countryCode, ip };
  } catch {
    return null;
  }
}

export function countryFlag(countryCode: string | null | undefined): string {
  const code = countryCode?.trim().toUpperCase();
  if (!code || !/^[A-Z]{2}$/.test(code)) return "🌐";
  return String.fromCodePoint(...[...code].map((char) => 127397 + char.charCodeAt(0)));
}
