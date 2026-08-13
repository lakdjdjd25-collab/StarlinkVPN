-- AlterTable
ALTER TABLE "User" ADD COLUMN "googleSubject" TEXT;

-- CreateTable
CREATE TABLE "FederatedAuthNonce" (
    "id" TEXT NOT NULL,
    "provider" TEXT NOT NULL,
    "nonceHash" TEXT NOT NULL,
    "installationId" TEXT NOT NULL,
    "expiresAt" TIMESTAMP(3) NOT NULL,
    "usedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "FederatedAuthNonce_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "User_googleSubject_key" ON "User"("googleSubject");

-- CreateIndex
CREATE INDEX "FederatedAuthNonce_provider_installationId_expiresAt_idx" ON "FederatedAuthNonce"("provider", "installationId", "expiresAt");
