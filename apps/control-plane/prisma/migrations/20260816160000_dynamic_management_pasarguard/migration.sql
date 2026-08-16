-- Snapshot notification recipients at send time, persist swappable PasarGuard providers,
-- and keep plan/profile mappings independent from provider-specific names/IDs.
ALTER TABLE "Notification" ADD COLUMN IF NOT EXISTS "category" TEXT NOT NULL DEFAULT 'SYSTEM';

-- Preserve existing announcements for users who already existed at migration time.
-- Future users receive nothing from these historical broadcasts because delivery rows
-- are now the sole source of the client inbox.
INSERT INTO "NotificationDelivery" ("notificationId", "userId", "deliveredAt", "readAt")
SELECT n."id", u."id", COALESCE(n."publishedAt", n."createdAt"), NULL
FROM "Notification" n
JOIN "User" u ON u."role" = 'CUSTOMER' AND u."status" <> 'DELETED'
WHERE n."publishedAt" IS NOT NULL
  AND (
    n."audience" = 'ALL'
    OR (n."audience" = 'FREE' AND EXISTS (
      SELECT 1 FROM "Service" s WHERE s."userId" = u."id" AND s."isFree" = true
    ))
    OR (n."audience" = 'PAID' AND EXISTS (
      SELECT 1 FROM "Service" s WHERE s."userId" = u."id" AND s."isFree" = false
    ))
  )
ON CONFLICT ("notificationId", "userId") DO NOTHING;

CREATE TABLE IF NOT EXISTS "PasarGuardProvider" (
  "id" TEXT NOT NULL,
  "sourceKey" TEXT NOT NULL,
  "name" TEXT NOT NULL DEFAULT 'PasarGuard',
  "baseUrl" TEXT NOT NULL,
  "username" TEXT NOT NULL,
  "passwordCiphertext" TEXT NOT NULL,
  "active" BOOLEAN NOT NULL DEFAULT false,
  "lastTestAt" TIMESTAMP(3),
  "lastSyncAt" TIMESTAMP(3),
  "lastError" TEXT,
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP(3) NOT NULL,
  CONSTRAINT "PasarGuardProvider_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX IF NOT EXISTS "PasarGuardProvider_sourceKey_key" ON "PasarGuardProvider"("sourceKey");
CREATE INDEX IF NOT EXISTS "PasarGuardProvider_active_updatedAt_idx" ON "PasarGuardProvider"("active", "updatedAt");

ALTER TABLE "PasarGuardBinding" ADD COLUMN IF NOT EXISTS "providerId" TEXT;
DROP INDEX IF EXISTS "PasarGuardBinding_externalUserId_key";
CREATE UNIQUE INDEX IF NOT EXISTS "PasarGuardBinding_providerId_externalUserId_key" ON "PasarGuardBinding"("providerId", "externalUserId");
CREATE INDEX IF NOT EXISTS "PasarGuardBinding_providerId_lastSyncAt_idx" ON "PasarGuardBinding"("providerId", "lastSyncAt");
DO $$ BEGIN
  ALTER TABLE "PasarGuardBinding" ADD CONSTRAINT "PasarGuardBinding_providerId_fkey"
  FOREIGN KEY ("providerId") REFERENCES "PasarGuardProvider"("id") ON DELETE SET NULL ON UPDATE CASCADE;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

CREATE TABLE IF NOT EXISTS "PasarGuardPlanMapping" (
  "id" TEXT NOT NULL,
  "providerId" TEXT NOT NULL,
  "planId" TEXT NOT NULL,
  "profileKey" TEXT NOT NULL,
  "profileName" TEXT NOT NULL,
  "groupIds" JSONB NOT NULL,
  "valid" BOOLEAN NOT NULL DEFAULT true,
  "lastValidatedAt" TIMESTAMP(3),
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP(3) NOT NULL,
  CONSTRAINT "PasarGuardPlanMapping_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX IF NOT EXISTS "PasarGuardPlanMapping_providerId_planId_key" ON "PasarGuardPlanMapping"("providerId", "planId");
CREATE INDEX IF NOT EXISTS "PasarGuardPlanMapping_planId_valid_idx" ON "PasarGuardPlanMapping"("planId", "valid");
DO $$ BEGIN
  ALTER TABLE "PasarGuardPlanMapping" ADD CONSTRAINT "PasarGuardPlanMapping_providerId_fkey"
  FOREIGN KEY ("providerId") REFERENCES "PasarGuardProvider"("id") ON DELETE CASCADE ON UPDATE CASCADE;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
DO $$ BEGIN
  ALTER TABLE "PasarGuardPlanMapping" ADD CONSTRAINT "PasarGuardPlanMapping_planId_fkey"
  FOREIGN KEY ("planId") REFERENCES "Plan"("id") ON DELETE CASCADE ON UPDATE CASCADE;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- The suspension trigger predates the category field. Keep its generated notifications categorized.
CREATE OR REPLACE FUNCTION "nimhub_notify_suspended_user"()
RETURNS trigger AS $$
DECLARE notification_id TEXT;
BEGIN
  IF NEW."status" = 'SUSPENDED' AND OLD."status" IS DISTINCT FROM NEW."status" THEN
    notification_id := 'susp_' || substr(md5(NEW."id" || clock_timestamp()::text || random()::text), 1, 20);
    INSERT INTO "Notification" (
      "id", "title", "body", "category", "audience", "actionUrl", "publishedAt", "expiresAt", "createdAt", "updatedAt"
    ) VALUES (
      notification_id,
      'حساب شما مسدود شد',
      'دسترسی حساب و سرویس‌های شما توسط مدیریت مسدود شده است. برای پیگیری با پشتیبانی تماس بگیرید.',
      'ACCOUNT',
      'SELECTED',
      'nimhub://account/suspended',
      CURRENT_TIMESTAMP,
      NULL,
      CURRENT_TIMESTAMP,
      CURRENT_TIMESTAMP
    );
    INSERT INTO "NotificationDelivery" ("notificationId", "userId", "deliveredAt", "readAt")
    VALUES (notification_id, NEW."id", CURRENT_TIMESTAMP, NULL);
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
