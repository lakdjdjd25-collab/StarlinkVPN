import { createHash } from "node:crypto";
import type { NodeProtocol } from "@/generated/prisma/enums";
import { singBoxRuntimeConfigSchema } from "../sing-box-config";
import { PasarGuardError } from "./client";

type JsonObject = Record<string, unknown>;

export type PasarGuardRuntimeNode = {
  tag: string;
  name: string;
  host: string;
  port: number;
  protocol: NodeProtocol;
  countryCode: string;
  countryName: string;
  runtimeConfig: JsonObject;
};

export type NormalizedPasarGuardConfig = {
  fingerprint: string;
  nodes: PasarGuardRuntimeNode[];
};

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

const countryNames: Record<string, string> = {
  ae: "امارات",
  at: "اتریش",
  ca: "کانادا",
  ch: "سوئیس",
  de: "آلمان",
  fi: "فنلاند",
  fr: "فرانسه",
  gb: "بریتانیا",
  ir: "ایران",
  nl: "هلند",
  no: "نروژ",
  ru: "روسیه",
  se: "سوئد",
  tr: "ترکیه",
  us: "آمریکا",
};

const countryAliases: Array<[RegExp, string]> = [
  [/\b(de|germany)\b|آلمان/i, "de"],
  [/\b(nl|netherlands)\b|هلند/i, "nl"],
  [/\b(us|usa|united states)\b|آمریکا/i, "us"],
  [/\b(gb|uk|united kingdom)\b|انگلیس|بریتانیا/i, "gb"],
  [/\b(fr|france)\b|فرانسه/i, "fr"],
  [/\b(fi|finland)\b|فنلاند/i, "fi"],
  [/\b(se|sweden)\b|سوئد/i, "se"],
  [/\b(no|norway)\b|نروژ/i, "no"],
  [/\b(tr|turkey)\b|ترکیه/i, "tr"],
  [/\b(ae|uae|emirates)\b|امارات/i, "ae"],
  [/\b(ca|canada)\b|کانادا/i, "ca"],
  [/\b(ch|switzerland)\b|سوئیس/i, "ch"],
  [/\b(at|austria)\b|اتریش/i, "at"],
  [/\b(ru|russia)\b|روسیه/i, "ru"],
  [/\b(ir|iran)\b|ایران/i, "ir"],
];

function isObject(value: unknown): value is JsonObject {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function cloneObject(value: JsonObject): JsonObject {
  return JSON.parse(JSON.stringify(value)) as JsonObject;
}

function listOfObjects(value: unknown): JsonObject[] {
  return Array.isArray(value) ? value.filter(isObject) : [];
}

function flagCountryCode(value: string): string | null {
  for (let index = 0; index < value.length; index += 1) {
    const first = value.codePointAt(index);
    if (!first || first < 0x1f1e6 || first > 0x1f1ff) continue;
    const firstLength = first > 0xffff ? 2 : 1;
    const second = value.codePointAt(index + firstLength);
    if (!second || second < 0x1f1e6 || second > 0x1f1ff) continue;
    return String.fromCharCode(first - 0x1f1e6 + 65, second - 0x1f1e6 + 65).toLowerCase();
  }
  return null;
}

export function inferCountry(tag: string): { code: string; name: string } {
  const code = flagCountryCode(tag) ?? countryAliases.find(([pattern]) => pattern.test(tag))?.[1] ?? "global";
  return { code, name: countryNames[code] ?? (code === "global" ? "شبکهٔ جهانی" : code.toUpperCase()) };
}

function protocolFor(type: string): NodeProtocol {
  switch (type) {
    case "vless": return "VLESS";
    case "vmess": return "VMESS";
    case "trojan": return "TROJAN";
    case "wireguard": return "WIREGUARD";
    case "hysteria":
    case "hysteria2": return "HYSTERIA2";
    default: return "SINGBOX";
  }
}

function endpointAddress(entry: JsonObject): { host: string; port: number } | null {
  if (entry.type === "wireguard") {
    const peer = listOfObjects(entry.peers)[0];
    const host = typeof peer?.address === "string" ? peer.address : "";
    const port = Number(peer?.port ?? 0);
    return host && Number.isInteger(port) && port > 0 && port <= 65_535 ? { host, port } : null;
  }
  const host = typeof entry.server === "string" ? entry.server : "";
  const port = Number(entry.server_port ?? 0);
  return host && Number.isInteger(port) && port > 0 && port <= 65_535 ? { host, port } : null;
}

function ensureTun(config: JsonObject): void {
  const inbounds = listOfObjects(config.inbounds);
  if (!inbounds.some((item) => item.type === "tun")) {
    inbounds.unshift({
      type: "tun",
      tag: "tun-in",
      interface_name: "sing-tun",
      address: ["172.19.0.1/30", "fdfe:dcba:9876::1/126"],
      auto_route: true,
      strict_route: true,
    });
  }
  config.inbounds = inbounds;
}

function configForTag(baseConfig: JsonObject, tag: string, selectorTags: Set<string>): JsonObject {
  const config = cloneObject(baseConfig);
  ensureTun(config);
  const route = isObject(config.route) ? config.route : {};
  route.final = tag;
  route.auto_detect_interface = true;
  route.override_android_vpn = true;
  if (Array.isArray(route.rules)) {
    route.rules = route.rules.map((value) => {
      if (!isObject(value)) return value;
      if (typeof value.outbound === "string" && selectorTags.has(value.outbound)) {
        return { ...value, outbound: tag };
      }
      return value;
    });
  }
  config.route = route;
  if (isObject(config.dns) && Array.isArray(config.dns.servers)) {
    config.dns.servers = config.dns.servers.map((value) => {
      if (!isObject(value)) return value;
      if (typeof value.detour === "string" && selectorTags.has(value.detour)) {
        return { ...value, detour: tag };
      }
      return value;
    });
  }
  const parsed = singBoxRuntimeConfigSchema.safeParse(config);
  if (!parsed.success) {
    throw new PasarGuardError("invalid_response", "کانفیگ پاسارگارد برای هستهٔ Android قابل استفاده نیست");
  }
  return parsed.data;
}

export function normalizePasarGuardConfig(value: unknown): NormalizedPasarGuardConfig {
  if (!isObject(value)) {
    throw new PasarGuardError("invalid_response", "کانفیگ sing-box پاسارگارد یک شیء JSON نیست");
  }
  const config = cloneObject(value);
  const outbounds = listOfObjects(config.outbounds);
  const endpoints = listOfObjects(config.endpoints);
  config.outbounds = outbounds;
  if (endpoints.length) config.endpoints = endpoints;
  const selectorTags = new Set(
    outbounds
      .filter((entry) => entry.type === "selector" || entry.type === "urltest")
      .map((entry) => entry.tag)
      .filter((tag): tag is string => typeof tag === "string"),
  );
  const remoteEntries = [
    ...outbounds.filter((entry) => typeof entry.type === "string" && remoteOutboundTypes.has(entry.type)),
    ...endpoints.filter((entry) => entry.type === "wireguard"),
  ];
  const seenTags = new Set<string>();
  const nodes = remoteEntries.flatMap((entry): PasarGuardRuntimeNode[] => {
    const tag = typeof entry.tag === "string" ? entry.tag.trim() : "";
    const type = typeof entry.type === "string" ? entry.type : "";
    const address = endpointAddress(entry);
    if (!tag || seenTags.has(tag) || !address) return [];
    seenTags.add(tag);
    const country = inferCountry(tag);
    return [{
      tag,
      name: tag,
      host: address.host,
      port: address.port,
      protocol: protocolFor(type),
      countryCode: country.code,
      countryName: country.name,
      runtimeConfig: configForTag(config, tag, selectorTags),
    }];
  });
  if (!nodes.length) {
    throw new PasarGuardError("invalid_response", "هیچ سرور قابل اتصال در اشتراک sing-box پاسارگارد پیدا نشد");
  }
  return {
    fingerprint: createHash("sha256").update(JSON.stringify(config)).digest("hex"),
    nodes,
  };
}
