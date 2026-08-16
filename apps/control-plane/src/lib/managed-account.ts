import { createHash, randomInt } from "node:crypto";

const UPPERCASE = "ABCDEFGHJKLMNPQRSTUVWXYZ";
const LOWERCASE = "abcdefghijkmnopqrstuvwxyz";
const DIGITS = "23456789";
const PASSWORD_ALPHABET = `${UPPERCASE}${LOWERCASE}${DIGITS}`;

function pick(alphabet: string): string {
  return alphabet[randomInt(0, alphabet.length)]!;
}

function shuffle(value: string[]): string {
  for (let index = value.length - 1; index > 0; index -= 1) {
    const target = randomInt(0, index + 1);
    [value[index], value[target]] = [value[target]!, value[index]!];
  }
  return value.join("");
}

export function generateManagedPassword(): string {
  const characters = [pick(UPPERCASE), pick(LOWERCASE), pick(DIGITS)];
  while (characters.length < 8) characters.push(pick(PASSWORD_ALPHABET));
  return shuffle(characters);
}

export function managedIdentity(seed: string): { email: string; remoteUsername: string } {
  const digest = createHash("sha256").update(`nimhub-managed:${seed}`).digest("hex");
  return {
    email: `user${digest.slice(0, 12)}@nimhub.com`,
    remoteUsername: `nh_${digest.slice(12, 36)}`,
  };
}

export function isPublicManagedEmail(email: string): boolean {
  return /^user[a-f0-9]{12}@nimhub\.com$/i.test(email.trim());
}
