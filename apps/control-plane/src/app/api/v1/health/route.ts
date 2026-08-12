import { fail, ok } from "@/lib/api";
import { db } from "@/lib/db";

export async function GET() {
  try {
    await db.$queryRaw`SELECT 1`;
    return ok({
      status: "ok",
      database: "ready",
      service: "quickping-control-plane",
      version: "2.6.0",
    });
  } catch {
    return fail(503, "database_unavailable", "Control-plane database is unavailable");
  }
}
