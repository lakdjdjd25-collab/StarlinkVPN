import { NextResponse } from "next/server";
import type { UserRole } from "@/generated/prisma/enums";
import { bearerToken, verifyToken } from "@/lib/auth";

type ApiError = {
  code: string;
  message: string;
  details?: unknown;
};

function jsonSafe<T>(value: T): T {
  return JSON.parse(
    JSON.stringify(value, (_key, item) => {
      if (typeof item === "bigint") return item.toString();
      if (item instanceof Date) return item.toISOString();
      return item;
    }),
  ) as T;
}

export function ok<T>(data: T, init?: ResponseInit): NextResponse {
  return NextResponse.json({ data: jsonSafe(data) }, init);
}

export function fail(
  status: number,
  code: string,
  message: string,
  details?: unknown,
): NextResponse {
  const error: ApiError = { code, message, ...(details ? { details } : {}) };
  return NextResponse.json({ error }, { status });
}

export async function requireBearer(
  request: Request,
  roles?: UserRole[],
): Promise<
  | { ok: true; userId: string; role: UserRole; serviceId: string | null }
  | { ok: false; response: NextResponse }
> {
  const token = bearerToken(request);
  if (!token) {
    return {
      ok: false,
      response: fail(401, "missing_token", "Authentication is required"),
    };
  }
  try {
    const claims = await verifyToken(token, "access");
    if (roles && !roles.includes(claims.role)) {
      return {
        ok: false,
        response: fail(403, "forbidden", "This account cannot perform the action"),
      };
    }
    return {
      ok: true,
      userId: claims.sub,
      role: claims.role,
      serviceId: claims.serviceId ?? null,
    };
  } catch {
    return {
      ok: false,
      response: fail(401, "invalid_token", "The access token is invalid or expired"),
    };
  }
}
