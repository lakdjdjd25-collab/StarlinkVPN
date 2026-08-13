import type { NextRequest } from "next/server";
import { z } from "zod";
import { adminFromRequest, isSameOrigin } from "@/lib/admin-session";
import { fail, ok } from "@/lib/api";
import { db } from "@/lib/db";
import {
  createPasarGuardClient,
  isPasarGuardConfigured,
  PasarGuardError,
} from "@/lib/pasarguard/client";
import { bindPasarGuardUser, syncPasarGuardBinding } from "@/lib/pasarguard/sync";

const actionSchema = z.discriminatedUnion("action", [
  z.object({
    action: z.literal("bind"),
    quickPingUserId: z.string().min(1),
    externalUserId: z.number().int().positive(),
  }),
  z.object({
    action: z.literal("sync"),
    bindingId: z.string().min(1),
  }),
]);

function integrationFailure(error: unknown) {
  if (error instanceof PasarGuardError) {
    const status = error.code === "not_configured" ? 503 : error.code === "invalid_response" ? 422 : 502;
    return fail(status, `pasarguard_${error.code}`, error.message);
  }
  return fail(500, "pasarguard_sync_failed", "اتصال پاسارگارد با خطای پیش‌بینی‌نشده متوقف شد");
}

export async function GET(request: NextRequest) {
  const admin = await adminFromRequest(request);
  if (!admin) return fail(401, "unauthorized", "ورود مدیر لازم است");
  const bindings = await db.pasarGuardBinding.findMany({
    orderBy: { createdAt: "desc" },
    include: {
      service: { include: { user: { select: { id: true, email: true } } } },
      nodes: { select: { id: true, name: true, status: true } },
    },
  });
  if (request.nextUrl.searchParams.get("remote") !== "1") {
    return ok({ configured: isPasarGuardConfigured(), bindings });
  }
  if (!isPasarGuardConfigured()) return fail(503, "pasarguard_not_configured", "Secretهای پاسارگارد تنظیم نشده‌اند");
  try {
    const users = await createPasarGuardClient().listUsers();
    return ok({
      configured: true,
      bindings,
      users: users.map((user) => ({
        id: user.id,
        username: user.username,
        status: user.status,
        usedTraffic: user.usedTraffic,
        dataLimit: user.dataLimit,
        expiresAt: user.expiresAt,
      })),
    });
  } catch (error) {
    return integrationFailure(error);
  }
}

export async function POST(request: NextRequest) {
  if (!isSameOrigin(request)) return fail(403, "invalid_origin", "مبدأ درخواست معتبر نیست");
  const admin = await adminFromRequest(request);
  if (!admin || admin.role !== "ADMIN") return fail(403, "forbidden", "فقط مدیر اصلی مجاز است");
  const input = actionSchema.safeParse(await request.json().catch(() => null));
  if (!input.success) return fail(400, "invalid_input", "درخواست اتصال پاسارگارد معتبر نیست");
  try {
    const binding = input.data.action === "bind"
      ? await bindPasarGuardUser(input.data.quickPingUserId, input.data.externalUserId)
      : await syncPasarGuardBinding(input.data.bindingId);
    await db.auditLog.create({
      data: {
        actorId: admin.sub,
        action: input.data.action === "bind" ? "pasarguard.bind" : "pasarguard.sync",
        entityType: "PasarGuardBinding",
        entityId: binding.id,
        after: {
          externalUserId: String(binding.externalUserId),
          externalUsername: binding.externalUsername,
          nodeCount: binding.nodes.length,
        },
      },
    });
    return ok(binding);
  } catch (error) {
    return integrationFailure(error);
  }
}
