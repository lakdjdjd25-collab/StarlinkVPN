import type { NextRequest } from "next/server";
import { z } from "zod";
import { adminFromRequest, isSameOrigin } from "@/lib/admin-session";
import { fail, ok } from "@/lib/api";
import { db } from "@/lib/db";

const schema = z.object({
  platform: z.enum(["ANDROID", "WINDOWS"]),
  versionName: z.string().trim().min(1).max(32),
  versionCode: z.number().int().positive(),
  minimumVersionCode: z.number().int().nonnegative(),
  mandatory: z.boolean().default(false),
  changelog: z.string().trim().min(1).max(10_000),
  downloadUrl: z.url().refine((value) => {
    const url = new URL(value);
    return url.protocol === "https:" && !url.username && !url.password;
  }, {
    message: "download_url_must_use_https_without_credentials",
  }),
  sha256: z.string().regex(/^[a-f0-9]{64}$/i),
  publishNow: z.boolean().default(true),
});

export async function POST(request: NextRequest) {
  if (!isSameOrigin(request)) return fail(403, "invalid_origin", "مبدأ درخواست معتبر نیست");
  const admin = await adminFromRequest(request);
  if (!admin || admin.role !== "ADMIN") return fail(403, "forbidden", "فقط مدیر اصلی مجاز است");
  const input = schema.safeParse(await request.json().catch(() => null));
  if (!input.success || input.data.minimumVersionCode > input.data.versionCode) {
    return fail(400, "invalid_input", "اطلاعات نسخه معتبر نیست؛ لینک دانلود باید HTTPS باشد");
  }

  const latest = await db.appRelease.findFirst({
    where: { platform: input.data.platform },
    orderBy: { versionCode: "desc" },
    select: { versionCode: true, minimumVersionCode: true },
  });
  if (latest && input.data.versionCode <= latest.versionCode) {
    return fail(
      409,
      "version_code_not_monotonic",
      `کد نسخه باید از آخرین کد ثبت‌شده (${latest.versionCode}) بزرگ‌تر باشد`,
    );
  }
  if (latest && input.data.minimumVersionCode < latest.minimumVersionCode) {
    return fail(
      409,
      "minimum_version_not_monotonic",
      `حداقل کد مجاز نمی‌تواند از مقدار قبلی (${latest.minimumVersionCode}) کمتر باشد`,
    );
  }

  const { publishNow, ...releaseData } = input.data;
  const release = await db.appRelease.create({
    data: {
      ...releaseData,
      sha256: input.data.sha256.toLowerCase(),
      publishedAt: publishNow ? new Date() : null,
    },
  });
  await db.auditLog.create({
    data: {
      actorId: admin.sub,
      action: "release.create",
      entityType: "AppRelease",
      entityId: release.id,
      after: {
        platform: release.platform,
        versionName: release.versionName,
        versionCode: release.versionCode,
        minimumVersionCode: release.minimumVersionCode,
        mandatory: release.mandatory,
      },
    },
  });
  return ok(release, { status: 201 });
}
