import {
  PasarGuardError,
  type PasarGuardClient,
  type PasarGuardUser,
} from "@/lib/pasarguard/client";
import { createPasarGuardClient } from "@/lib/pasarguard/provider";

function groupIds(users: PasarGuardUser[]): number[] {
  const active = users.filter((user) => user.status.toLowerCase() === "active" && user.groupIds.length > 0);
  const source = active.length > 0 ? active : users.filter((user) => user.groupIds.length > 0);
  const ids = [...new Set(source.flatMap((user) => user.groupIds))].sort((a, b) => a - b);
  if (ids.length === 0) {
    throw new PasarGuardError("not_configured", "از کاربران فعلی پاسارگارد هیچ گروه سروری برای سرویس رایگان پیدا نشد");
  }
  return ids;
}

export async function googleFreeGroupIds(
  client?: PasarGuardClient,
): Promise<number[]> {
  const resolved = client ?? await createPasarGuardClient();
  return groupIds(await resolved.listUsers());
}

export async function preflightDirectGoogleFreeUser(): Promise<void> {
  await googleFreeGroupIds();
}

export async function createDirectGoogleFreeUser(
  username: string,
  quotaBytes: bigint,
  note: string,
  client?: PasarGuardClient,
): Promise<PasarGuardUser> {
  const resolved = client ?? await createPasarGuardClient();
  return resolved.createUser(username, quotaBytes, await googleFreeGroupIds(resolved), note, 1);
}
