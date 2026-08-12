import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import type { NextRequest } from "next/server";
import { adminCookieName, verifyToken } from "@/lib/auth";
import { db } from "@/lib/db";

async function activeAdminFromToken(token: string) {
  const claims = await verifyToken(token, "admin");
  const user = await db.user.findUnique({
    where: { id: claims.sub },
    select: { role: true, status: true },
  });
  if (
    !user ||
    user.status !== "ACTIVE" ||
    (user.role !== "ADMIN" && user.role !== "SUPPORT")
  ) return null;
  return { ...claims, role: user.role };
}

export async function currentAdmin() {
  const store = await cookies();
  const token = store.get(adminCookieName())?.value;
  if (!token) return null;
  try {
    return await activeAdminFromToken(token);
  } catch {
    return null;
  }
}

export async function requireAdminPage() {
  const session = await currentAdmin();
  if (!session) redirect("/login");
  return session;
}

export async function adminFromRequest(request: NextRequest) {
  const token = request.cookies.get(adminCookieName())?.value;
  if (!token) return null;
  try {
    return await activeAdminFromToken(token);
  } catch {
    return null;
  }
}

export function isSameOrigin(request: NextRequest): boolean {
  const origin = request.headers.get("origin");
  if (!origin) return false;
  try {
    return new URL(origin).origin === request.nextUrl.origin;
  } catch {
    return false;
  }
}
