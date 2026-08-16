-- Preserve already-consumed traffic when a service is moved to a different PasarGuard provider.
ALTER TABLE "PasarGuardBinding"
ADD COLUMN IF NOT EXISTS "usageOffsetBytes" BIGINT NOT NULL DEFAULT 0;
