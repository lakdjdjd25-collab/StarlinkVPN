import type { NextRequest } from "next/server";
import { z } from "zod";
import { adminFromRequest, isSameOrigin } from "@/lib/admin-session";
import { fail, ok } from "@/lib/api";
import { db } from "@/lib/db";

const schema = z.object({
  code: z.string().min(2).max(24).regex(/^[a-z0-9-]+$/),
  name: z.string().min(2).max(120),
  countryCode: z.string().length(2).transform((value) => value.toLowerCase()),
  priority: z.number().int().min(-10_000).max(10_000).default(0),
});

export async function GET(request: NextRequest) {
  const admin = await adminFromRequest(request);
  if (!admin) return fail(401, "unauthorized", "ورود مدیر لازم است");
  return ok(await db.serverRegion.findMany({ orderBy: [{ priority: "desc" }, { name: "asc" }] }));
}

export async function POST(request: NextRequest) {
  if (!isSameOrigin(request)) return fail(403, "invalid_origin", "مبدأ درخواست معتبر نیست");
  const admin = await adminFromRequest(request);
  if (!admin || admin.role !== "ADMIN") return fail(403, "forbidden", "فقط مدیر اصلی مجاز است");
  const input = schema.safeParse(await request.json().catch(() => null));
  if (!input.success) return fail(400, "invalid_input", "اطلاعات منطقه معتبر نیست");
  const region = await db.serverRegion.create({ data: input.data });
  await db.auditLog.create({
    data: {
      actorId: admin.sub,
      action: "region.create",
      entityType: "ServerRegion",
      entityId: region.id,
      after: { code: region.code, name: region.name, countryCode: region.countryCode },
    },
  });
  return ok(region, { status: 201 });
}
