export const MANUAL_TRAFFIC_CAPABILITY_HEADER = "x-nimhub-manual-traffic";

export function supportsManualTraffic(request: Pick<Request, "headers">): boolean {
  return request.headers.get(MANUAL_TRAFFIC_CAPABILITY_HEADER) === "1";
}
