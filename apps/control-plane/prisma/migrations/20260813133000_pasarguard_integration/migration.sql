-- DropIndex
DROP INDEX "VpnNode_host_port_protocol_key";

-- AlterTable
ALTER TABLE "VpnNode"
ADD COLUMN "provider" TEXT NOT NULL DEFAULT 'MANUAL',
ADD COLUMN "providerKey" TEXT,
ADD COLUMN "providerTag" TEXT,
ADD COLUMN "pasarGuardBindingId" TEXT;

-- CreateTable
CREATE TABLE "PasarGuardBinding" (
    "id" TEXT NOT NULL,
    "serviceId" TEXT NOT NULL,
    "externalUserId" BIGINT NOT NULL,
    "externalUsername" TEXT NOT NULL,
    "configFingerprint" TEXT,
    "lastSyncAt" TIMESTAMP(3),
    "lastError" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "PasarGuardBinding_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "VpnNode_providerKey_key" ON "VpnNode"("providerKey");

-- CreateIndex
CREATE INDEX "VpnNode_host_port_protocol_idx" ON "VpnNode"("host", "port", "protocol");

-- CreateIndex
CREATE INDEX "VpnNode_pasarGuardBindingId_idx" ON "VpnNode"("pasarGuardBindingId");

-- CreateIndex
CREATE UNIQUE INDEX "PasarGuardBinding_serviceId_key" ON "PasarGuardBinding"("serviceId");

-- CreateIndex
CREATE UNIQUE INDEX "PasarGuardBinding_externalUserId_key" ON "PasarGuardBinding"("externalUserId");

-- CreateIndex
CREATE INDEX "PasarGuardBinding_lastSyncAt_idx" ON "PasarGuardBinding"("lastSyncAt");

-- AddForeignKey
ALTER TABLE "PasarGuardBinding" ADD CONSTRAINT "PasarGuardBinding_serviceId_fkey" FOREIGN KEY ("serviceId") REFERENCES "Service"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "VpnNode" ADD CONSTRAINT "VpnNode_pasarGuardBindingId_fkey" FOREIGN KEY ("pasarGuardBindingId") REFERENCES "PasarGuardBinding"("id") ON DELETE CASCADE ON UPDATE CASCADE;
