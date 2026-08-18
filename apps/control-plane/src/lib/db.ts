import { PrismaPg } from "@prisma/adapter-pg";
import { PrismaClient } from "../generated/prisma/client";

const globalForPrisma = globalThis as unknown as {
  quickPingPrisma?: PrismaClient;
};

function createClient(): PrismaClient {
  const connectionString =
    process.env.DATABASE_URL ??
    "postgresql://quickping:quickping@localhost:5432/quickping";
  const adapter = new PrismaPg({ connectionString });
  return new PrismaClient({ adapter });
}

export const db = globalForPrisma.quickPingPrisma ?? createClient();

if (process.env.NODE_ENV !== "production") {
  globalForPrisma.quickPingPrisma = db;
}
