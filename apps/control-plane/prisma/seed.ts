import "dotenv/config";
import { PrismaPg } from "@prisma/adapter-pg";
import { hash } from "bcryptjs";
import { PrismaClient } from "../src/generated/prisma/client";

const connectionString = process.env.DATABASE_URL;
if (!connectionString) throw new Error("DATABASE_URL is required for seeding");

const db = new PrismaClient({ adapter: new PrismaPg({ connectionString }) });

async function main() {
  const email = process.env.ADMIN_EMAIL;
  const password = process.env.ADMIN_PASSWORD;
  if (!email || !password || password.length < 12) {
    throw new Error("ADMIN_EMAIL and an ADMIN_PASSWORD of at least 12 characters are required");
  }

  await db.user.upsert({
    where: { email },
    update: {
      role: "ADMIN",
      status: "ACTIVE",
      passwordHash: await hash(password, 12),
      emailVerifiedAt: new Date(),
    },
    create: {
      email,
      role: "ADMIN",
      status: "ACTIVE",
      passwordHash: await hash(password, 12),
      emailVerifiedAt: new Date(),
      language: "fa",
    },
  });

  const plan = await db.plan.upsert({
    where: { name: "VIP Monthly" },
    update: {},
    create: {
      name: "VIP Monthly",
      interval: "MONTHLY",
      price: "5.00",
      durationDays: 30,
      dataLimitBytes: 60n * 1024n * 1024n * 1024n,
      maxDevices: 2,
    },
  });

  if (process.env.SEED_DEMO_DATA === "true") {
    const demo = await db.user.upsert({
      where: { email: "demo@quickping.local" },
      update: {},
      create: {
        email: "demo@quickping.local",
        emailVerifiedAt: new Date(),
        language: "fa",
      },
    });

    await db.service.upsert({
      where: { license: "DEMO01" },
      update: {},
      create: {
        userId: demo.id,
        planId: plan.id,
        name: "سرویس آزمایشی",
        license: "DEMO01",
        quotaBytes: 60n * 1024n * 1024n * 1024n,
        usedBytes: 3n * 1024n * 1024n * 1024n,
        expiresAt: new Date(Date.now() + 26 * 86_400_000),
        maxDevices: 2,
      },
    });
  }

  await db.globalSetting.upsert({
    where: { key: "client.bootstrap" },
    update: {},
    create: {
      key: "client.bootstrap",
      description: "Public non-secret client bootstrap flags",
      value: {
        maintenance: false,
        signupEnabled: true,
        minimumAndroidVersionCode: 160162,
      },
    },
  });

  await db.serverRegion.upsert({
    where: { code: "qa" },
    update: {},
    create: {
      code: "qa",
      name: "قطر",
      countryCode: "qa",
      priority: 100,
    },
  });

  console.log("QuickPing seed completed");
}

main()
  .then(async () => {
    await Promise.race([
      db.$disconnect(),
      new Promise<void>((resolve) => setTimeout(resolve, 2_000)),
    ]);
    process.exit(0);
  })
  .catch(async (error) => {
    console.error(error);
    await Promise.race([
      db.$disconnect(),
      new Promise<void>((resolve) => setTimeout(resolve, 2_000)),
    ]);
    process.exit(1);
  });
