import type { NextRequest } from "next/server";
import { z } from "zod";
import { fail, ok, requireBearer } from "@/lib/api";
import { ManualTrafficError, reportManualTraffic } from "@/lib/manual-traffic";

const bytes = z.string().regex(/^\d{1,19}$/).transform((value) => BigInt(value));
const schema = z.object({ uploadedBytes: bytes, downloadedBytes: bytes });

export async function POST(
  request: NextRequest,
  context: { params: Promise<{ sessionId: string }> },
) {
  const auth = await requireBearer(request);
  if (!auth.ok) return auth.response;
  const input = schema.safeParse(await request.json().catch(() => null));
  if (!input.success) return fail(400, "invalid_traffic", "Traffic counters are invalid");
  const { sessionId } = await context.params;
  try {
    return ok(await reportManualTraffic({
      userId: auth.userId,
      authServiceId: auth.serviceId,
      sessionId,
      cumulative: input.data,
      finalize: false,
    }));
  } catch (error) {
    if (error instanceof ManualTrafficError) return fail(error.status, error.code, error.message);
    throw error;
  }
}
