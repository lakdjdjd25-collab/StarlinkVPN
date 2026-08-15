import type { NextRequest } from "next/server";
import { z } from "zod";
import { hashPassword, verifyPassword } from "@/lib/auth";
import { fail, ok, requireBearer } from "@/lib/api";
import { db } from "@/lib/db";

const schema = z.object({
  currentPassword: z.string().min(1).max(256),
  newPassword: z.string().min(8).max(72),
});

export async function POST(request: NextRequest) {
  const auth = await requireBearer(request, ["CUSTOMER", "ADMIN"]);
  if (!auth.ok) return auth.response;
  if (auth.serviceId) {
    return fail(403, "license_session_not_allowed", "برای تغییر گذرواژه باید با حساب کاربری وارد شوید");
  }

  const input = schema.safeParse(await request.json().catch(() => null));
  if (!input.success) {
    return fail(400, "invalid_input", "گذرواژهٔ فعلی و گذرواژهٔ جدید را به‌درستی وارد کنید");
  }

  const user = await db.user.findFirst({
    where: { id: auth.userId, status: "ACTIVE" },
    select: { id: true, passwordHash: true },
  });
  if (!user) return fail(403, "account_unavailable", "حساب کاربری در دسترس نیست");

  if (!user.passwordHash || !(await verifyPassword(input.data.currentPassword, user.passwordHash))) {
    return fail(401, "invalid_current_password", "گذرواژهٔ فعلی صحیح نیست");
  }

  const passwordHash = await hashPassword(input.data.newPassword);
  const now = new Date();
  await db.$transaction(async (transaction) => {
    await transaction.user.update({
      where: { id: user.id },
      data: { passwordHash },
    });
    await transaction.refreshToken.updateMany({
      where: { userId: user.id, revokedAt: null },
      data: { revokedAt: now },
    });
  });

  return ok({ changed: true, signInRequired: true });
}
