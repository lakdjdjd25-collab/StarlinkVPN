-- Existing services remain STANDARD-only and all existing nodes remain STANDARD.
CREATE TYPE "AccessTier" AS ENUM ('STANDARD', 'VIP');

ALTER TABLE "Service"
ADD COLUMN "vipAccess" BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE "VpnNode"
ADD COLUMN "accessTier" "AccessTier" NOT NULL DEFAULT 'STANDARD';

CREATE INDEX "VpnNode_accessTier_status_idx" ON "VpnNode"("accessTier", "status");
