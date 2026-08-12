import type { NextRequest } from "next/server";
import { z } from "zod";
import { Prisma } from "@/generated/prisma/client";
import { adminFromRequest, isSameOrigin } from "@/lib/admin-session";
import { fail, ok } from "@/lib/api";
import { db } from "@/lib/db";

const schema = z.object({
  key: z.string().min(2).max(120).regex(/^[a-z0-9._-]+$/i),
  value: z.unknown(),
  description: z.string().max(500).optional(),
});

function asJsonInput(value: unknown): Prisma.InputJsonValue | typeof Prisma.JsonNull {
  return value === null ? Prisma.JsonNull : value as Prisma.InputJsonValue;
}

export async function POST(request: NextRequest) {
  if (!isSameOrigin(request)) return fail(403, "invalid_origin", "مبدأ درخواست معتبر نیست");
  const admin = await adminFromRequest(request);
  if (!admin || admin.role !== "ADMIN") return fail(403, "forbidden", "فقط مدیر اصلی مجاز است");
  const input = schema.safeParse(await request.json().catch(() => null));
  if (!input.success) return fail(400, "invalid_input", "تنظیم واردشده معتبر نیست");
  const before = await db.globalSetting.findUnique({ where: { key: input.data.key } });
  const setting = await db.globalSetting.upsert({
    where: { key: input.data.key },
    update: { value: asJsonInput(input.data.value), description: input.data.description },
    create: {
      key: input.data.key,
      value: asJsonInput(input.data.value),
      description: input.data.description,
    },
  });
  await db.auditLog.create({
    data: {
      actorId: admin.sub,
      action: before ? "setting.update" : "setting.create",
      entityType: "GlobalSetting",
      entityId: setting.key,
      before: before ? asJsonInput(before.value) : undefined,
      after: asJsonInput(setting.value),
    },
  });
  return ok(setting, { status: before ? 200 : 201 });
}
