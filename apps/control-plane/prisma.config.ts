import "dotenv/config";
import { defineConfig } from "prisma/config";

export default defineConfig({
  schema: "prisma/schema.prisma",
  migrations: {
    path: "prisma/migrations",
    seed: "tsx prisma/seed.ts",
  },
  datasource: {
    // Client generation does not open a connection. Runtime and migrations
    // still require Railway's real DATABASE_URL.
    url:
      process.env.DATABASE_URL ??
      "postgresql://quickping:quickping@localhost:5432/quickping",
  },
});
