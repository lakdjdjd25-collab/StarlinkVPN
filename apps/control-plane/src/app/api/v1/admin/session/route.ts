import type { NextRequest } from "next/server";
import { z } from "zod";
import { isSameOrigin } from "@/lib/admin-session";
import { adminCookieName, issueToken, verifyPassword } from "@/lib/auth";
import { fail, ok } from "@/lib/api";
import { db } from "@/lib/db";

const schema = z.object({
  email: z.email().transform((value) => value.toLowerCase()),
  password: z.string().min(1).max(256),
});

export async function POST(request: NextRequest) {
  if (!isSameOrigin(request)) return fail(403, "invalid_origin", "مبدأ درخواست معتبر نیست");
  const input = schema.safeParse(await request.json().catch(() => null));
  if (!input.success) return fail(400, "invalid_input", "ایمیل یا گذرواژه معتبر نیست");
  const user = await db.user.findUnique({ where: { email: input.data.email } });
  if (
    !user?.passwordHash ||
    user.status !== "ACTIVE" ||
    (user.role !== "ADMIN" && user.role !== "SUPPORT") ||
    !(await verifyPassword(input.data.password, user.passwordHash))
  ) {
    return fail(401, "invalid_credentials", "ایمیل یا گذرواژه صحیح نیست");
  }
  const token = await issueToken(user.id, user.role, "admin");
  const response = ok({ user: { id: user.id, email: user.email, role: user.role } });
  response.cookies.set(adminCookieName(), token, {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "strict",
    path: "/",
    maxAge: 8 * 60 * 60,
  });
  return response;
}

export function DELETE(request: NextRequest) {
  if (!isSameOrigin(request)) return fail(403, "invalid_origin", "مبدأ درخواست معتبر نیست");
  const response = ok({ signedOut: true });
  response.cookies.set(adminCookieName(), "", {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "strict",
    path: "/",
    maxAge: 0,
  });
  return response;
}
