import type { NextRequest } from "next/server";
import { z } from "zod";
import { adminFromRequest, isSameOrigin } from "@/lib/admin-session";
import { fail, ok } from "@/lib/api";
import { countryFlag, detectGeoCountry, parseVlessUri } from "@/lib/manual-vless";

const schema = z.object({
  config: z.string().trim().min(10).max(16_384),
  countryOverride: z.string().trim().max(80).optional().nullable(),
  countryCodeOverride: z.string().trim().regex(/^[A-Za-z]{2}$/).optional().nullable(),
});

function parserMessage(error: unknown): string {
  const code = error instanceof Error ? error.message : "VLESS_CONFIG_INVALID";
  const messages: Record<string, string> = {
    VLESS_CONFIG_REQUIRED: "لینک باید با vless:// شروع شود",
    VLESS_CONFIG_INVALID: "ساختار لینک VLESS معتبر نیست",
    VLESS_UUID_INVALID: "UUID لینک VLESS معتبر نیست",
    VLESS_ENDPOINT_INVALID: "آدرس یا پورت سرور معتبر نیست",
    VLESS_TRANSPORT_UNSUPPORTED: "Transport این لینک هنوز پشتیبانی نمی‌شود",
    VLESS_SECURITY_UNSUPPORTED: "Security این لینک پشتیبانی نمی‌شود",
    VLESS_REALITY_KEY_REQUIRED: "کلید عمومی Reality در لینک وجود ندارد",
    VLESS_RUNTIME_INVALID: "این لینک به پیکربندی معتبر sing-box تبدیل نشد",
  };
  return messages[code] ?? "لینک VLESS قابل استفاده نیست";
}

export async function POST(request: NextRequest) {
  if (!isSameOrigin(request)) return fail(403, "invalid_origin", "مبدأ درخواست معتبر نیست");
  const admin = await adminFromRequest(request);
  if (!admin || admin.role !== "ADMIN") return fail(403, "forbidden", "فقط مدیر اصلی مجاز است");
  const input = schema.safeParse(await request.json().catch(() => null));
  if (!input.success) return fail(400, "invalid_input", "اطلاعات لینک معتبر نیست");
  try {
    const parsed = parseVlessUri(input.data.config);
    const geo = await detectGeoCountry(parsed.host);
    const country = input.data.countryOverride || geo?.country || "Unknown";
    const countryCode = (input.data.countryCodeOverride || geo?.countryCode || "global").toUpperCase();
    return ok({
      protocol: parsed.protocol,
      host: parsed.host,
      port: parsed.port,
      transport: parsed.transport,
      security: parsed.security,
      sni: parsed.sni ?? null,
      fingerprint: parsed.fingerprint ?? null,
      path: parsed.path ?? null,
      serviceName: parsed.serviceName ?? null,
      flow: parsed.flow ?? null,
      fragment: parsed.fragment ?? null,
      country,
      countryCode,
      flag: countryFlag(countryCode),
      geoDetected: Boolean(geo),
    });
  } catch (error) {
    return fail(400, "invalid_vless_config", parserMessage(error));
  }
}
