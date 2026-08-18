-- Additive Manual VLESS infrastructure. Existing users, services, nodes and usage stay untouched.
CREATE TYPE "ManualServerCategory" AS ENUM ('UNLIMITED', 'GAMING');
CREATE TYPE "TrafficSessionStatus" AS ENUM ('ACTIVE', 'ENDED', 'EXHAUSTED', 'REVOKED');

ALTER TABLE "Service"
ADD COLUMN "manualUsedBytes" BIGINT NOT NULL DEFAULT 0;

CREATE TABLE "ManualServer" (
    "id" TEXT NOT NULL,
    "displayName" TEXT NOT NULL,
    "sourceCiphertext" TEXT NOT NULL,
    "configCiphertext" TEXT NOT NULL,
    "protocol" "NodeProtocol" NOT NULL DEFAULT 'VLESS',
    "host" TEXT NOT NULL,
    "port" INTEGER NOT NULL,
    "country" TEXT,
    "countryCode" TEXT,
    "countryOverride" TEXT,
    "category" "ManualServerCategory" NOT NULL DEFAULT 'UNLIMITED',
    "accessTier" "AccessTier" NOT NULL DEFAULT 'STANDARD',
    "enabled" BOOLEAN NOT NULL DEFAULT false,
    "sortOrder" INTEGER NOT NULL DEFAULT 0,
    "countTraffic" BOOLEAN NOT NULL DEFAULT true,
    "lastUsedAt" TIMESTAMP(3),
    "deletedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    CONSTRAINT "ManualServer_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "TrafficSession" (
    "id" TEXT NOT NULL,
    "serviceId" TEXT NOT NULL,
    "manualServerId" TEXT NOT NULL,
    "startedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "lastReportAt" TIMESTAMP(3),
    "endedAt" TIMESTAMP(3),
    "uploadedBytes" BIGINT NOT NULL DEFAULT 0,
    "downloadedBytes" BIGINT NOT NULL DEFAULT 0,
    "totalBytes" BIGINT NOT NULL DEFAULT 0,
    "lastAccountedBytes" BIGINT NOT NULL DEFAULT 0,
    "status" "TrafficSessionStatus" NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT "TrafficSession_pkey" PRIMARY KEY ("id")
);

CREATE INDEX "ManualServer_enabled_sortOrder_idx" ON "ManualServer"("enabled", "sortOrder");
CREATE INDEX "ManualServer_accessTier_enabled_idx" ON "ManualServer"("accessTier", "enabled");
CREATE INDEX "ManualServer_deletedAt_idx" ON "ManualServer"("deletedAt");
CREATE INDEX "TrafficSession_serviceId_status_idx" ON "TrafficSession"("serviceId", "status");
CREATE INDEX "TrafficSession_manualServerId_startedAt_idx" ON "TrafficSession"("manualServerId", "startedAt");
CREATE INDEX "TrafficSession_status_lastReportAt_idx" ON "TrafficSession"("status", "lastReportAt");

ALTER TABLE "TrafficSession"
ADD CONSTRAINT "TrafficSession_serviceId_fkey"
FOREIGN KEY ("serviceId") REFERENCES "Service"("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "TrafficSession"
ADD CONSTRAINT "TrafficSession_manualServerId_fkey"
FOREIGN KEY ("manualServerId") REFERENCES "ManualServer"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
