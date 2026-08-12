import type { NextRequest } from "next/server";
import { z } from "zod";
import { adminFromRequest, isSameOrigin } from "@/lib/admin-session";
import { fail, ok } from "@/lib/api";
import { db } from "@/lib/db";

const schema = z.object({
  name: z.string().min(2).max(120),
  interval: z.enum(["FREE", "MONTHLY", "YEARLY", "CUSTOM"]),
  price: z.number().nonnegative().max(1_000_000),
  durationDays: z.number().int().min(1).max(3650),
  dataLimitGb: z.number().positive().max(1_000_000),
  maxDevices: z.number().int().min(1).max(1000),
  isPublic: z.boolean().default(true),
});

export async function GET(request: NextRequest) {
  const admin = await adminFromRequest(request);
  if (!admin) return fail(401, "unauthorized", "ورود مدیر لازم است");
  return ok(await db.plan.findMany({ orderBy: [{ isActive: "desc" }, { price: "asc" }] }));
}

export async function POST(request: NextRequest) {
  if (!isSameOrigin(request)) return fail(403, "invalid_origin", "مبدأ درخواست معتبر نیست");
  const admin = await adminFromRequest(request);
  if (!admin || admin.role !== "ADMIN") return fail(403, "forbidden", "فقط مدیر اصلی مجاز است");
  const input = schema.safeParse(await request.json().catch(() => null));
  if (!input.success) return fail(400, "invalid_input", "اطلاعات پلن معتبر نیست");
  const plan = await db.plan.create({
    data: {
      name: input.data.name,
      interval: input.data.interval,
      price: input.data.price,
      durationDays: input.data.durationDays,
      dataLimitBytes: BigInt(Math.round(input.data.dataLimitGb * 1024 ** 3)),
      maxDevices: input.data.maxDevices,
      isPublic: input.data.isPublic,
    },
  });
  await db.auditLog.create({
    data: {
      actorId: admin.sub,
      action: "plan.create",
      entityType: "Plan",
      entityId: plan.id,
      after: { name: plan.name, interval: plan.interval, price: String(plan.price) },
    },
  });
  return ok(plan, { status: 201 });
}
