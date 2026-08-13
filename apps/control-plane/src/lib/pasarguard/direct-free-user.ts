import {
  createPasarGuardClient,
  PasarGuardError,
  type PasarGuardClient,
  type PasarGuardUser,
} from "@/lib/pasarguard/client";

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
  client: PasarGuardClient = createPasarGuardClient(),
): Promise<number[]> {
  return groupIds(await client.listUsers());
}

export async function preflightDirectGoogleFreeUser(): Promise<void> {
  await googleFreeGroupIds();
}

export async function createDirectGoogleFreeUser(
  username: string,
  quotaBytes: bigint,
  note: string,
  client: PasarGuardClient = createPasarGuardClient(),
): Promise<PasarGuardUser> {
  return client.createUser(username, quotaBytes, await googleFreeGroupIds(client), note, 1);
}
