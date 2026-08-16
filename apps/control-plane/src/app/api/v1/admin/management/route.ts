import type { NextRequest } from "next/server";
import { z } from "zod";
import { Prisma } from "@/generated/prisma/client";
import { adminFromRequest, isSameOrigin } from "@/lib/admin-session";
import { fail, ok } from "@/lib/api";
import { db } from "@/lib/db";

const schema = z.object({
  telegramUsername: z.string().trim().min(1).max(64).transform((value) => value.replace(/^@+/, ""))
    .refine((value) => /^[A-Za-z0-9_]{5,32}$/.test(value), "آیدی تلگرام معتبر نیست"),
});

function valueOf(setting: unknown): { telegramUsername: string } {
  if (setting && typeof setting === "object" && "telegramUsername" in setting) {
    const value = (setting as { telegramUsername?: unknown }).telegramUsername;
    if (typeof value === "string" && /^[A-Za-z0-9_]{5,32}$/.test(value)) return { telegramUsername: value };
  }
  return { telegramUsername: "Folwn" };
}

export async function GET(request: NextRequest) {
  const admin = await adminFromRequest(request);
  if (!admin) return fail(401, "unauthorized", "ورود مدیر لازم است");
  const setting = await db.globalSetting.findUnique({ where: { key: "client.management" } });
  return ok(valueOf(setting?.value));
}

export async function POST(request: NextRequest) {
  if (!isSameOrigin(request)) return fail(403, "invalid_origin", "مبدأ درخواست معتبر نیست");
  const admin = await adminFromRequest(request);
  if (!admin || admin.role !== "ADMIN") return fail(403, "forbidden", "فقط مدیر اصلی مجاز است");
  const input = schema.safeParse(await request.json().catch(() => null));
  if (!input.success) return fail(400, "invalid_input", input.error.issues[0]?.message ?? "اطلاعات مدیریت معتبر نیست");
  const before = await db.globalSetting.findUnique({ where: { key: "client.management" } });
  const value = { telegramUsername: input.data.telegramUsername } satisfies Prisma.InputJsonObject;
  const saved = await db.globalSetting.upsert({
    where: { key: "client.management" },
    update: { value, description: "Public management contact used by the Android upgrade action" },
    create: { key: "client.management", value, description: "Public management contact used by the Android upgrade action" },
  });
  await db.auditLog.create({
    data: {
      actorId: admin.sub,
      action: "management.update",
      entityType: "GlobalSetting",
      entityId: saved.key,
      before: before ? before.value as Prisma.InputJsonValue : undefined,
      after: value,
    },
  });
  return ok(value);
}
