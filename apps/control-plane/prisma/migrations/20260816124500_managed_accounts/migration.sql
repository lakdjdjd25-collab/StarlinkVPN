ALTER TABLE "User"
ADD COLUMN "managedAccount" BOOLEAN NOT NULL DEFAULT false;

UPDATE "User"
SET "managedAccount" = true
WHERE "email" LIKE 'pg-%@license.nimhub.local';
