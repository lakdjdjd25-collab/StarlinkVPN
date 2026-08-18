ALTER TYPE "ManualServerCategory" ADD VALUE IF NOT EXISTS 'LIMITED';

ALTER TABLE "ManualServer"
  ADD COLUMN "subcategory" TEXT,
  ADD COLUMN "volumeBytes" BIGINT;
