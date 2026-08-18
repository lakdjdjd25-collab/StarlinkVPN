import type { NextRequest } from "next/server";
import { z } from "zod";
import { fail, ok, requireBearer } from "@/lib/api";
import { ManualTrafficError, startManualTrafficSession } from "@/lib/manual-traffic";

const schema = z.object({
  serviceId: z.string().min(1),
  serverId: z.string().min(1),
});

export async function POST(request: NextRequest) {
  const auth = await requireBearer(request);
  if (!auth.ok) return auth.response;
  const input = schema.safeParse(await request.json().catch(() => null));
  if (!input.success) return fail(400, "invalid_input", "Traffic session request is invalid");
  try {
    const session = await startManualTrafficSession({
      userId: auth.userId,
      authServiceId: auth.serviceId,
      serviceId: input.data.serviceId,
      manualServerId: input.data.serverId,
    });
    return ok(session, { status: 201 });
  } catch (error) {
    if (error instanceof ManualTrafficError) return fail(error.status, error.code, error.message);
    throw error;
  }
}
