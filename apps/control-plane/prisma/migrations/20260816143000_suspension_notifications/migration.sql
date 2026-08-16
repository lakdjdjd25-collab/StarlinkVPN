CREATE OR REPLACE FUNCTION "nimhub_notify_suspended_user"()
RETURNS trigger AS $$
DECLARE
  notification_id TEXT;
BEGIN
  IF NEW."status" = 'SUSPENDED' AND OLD."status" IS DISTINCT FROM NEW."status" THEN
    notification_id := 'susp_' || substr(md5(NEW."id" || clock_timestamp()::text || random()::text), 1, 20);

    INSERT INTO "Notification" (
      "id", "title", "body", "audience", "actionUrl", "publishedAt", "expiresAt", "createdAt", "updatedAt"
    ) VALUES (
      notification_id,
      'حساب شما مسدود شد',
      'دسترسی حساب و سرویس‌های شما توسط مدیریت مسدود شده است. برای پیگیری با پشتیبانی تماس بگیرید.',
      'SELECTED',
      'nimhub://account/suspended',
      CURRENT_TIMESTAMP,
      NULL,
      CURRENT_TIMESTAMP,
      CURRENT_TIMESTAMP
    );

    INSERT INTO "NotificationDelivery" (
      "notificationId", "userId", "deliveredAt", "readAt"
    ) VALUES (
      notification_id,
      NEW."id",
      CURRENT_TIMESTAMP,
      NULL
    );
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS "nimhub_user_suspension_notification" ON "User";
CREATE TRIGGER "nimhub_user_suspension_notification"
AFTER UPDATE OF "status" ON "User"
FOR EACH ROW
EXECUTE FUNCTION "nimhub_notify_suspended_user"();

CREATE OR REPLACE FUNCTION "nimhub_cleanup_suspension_notification"()
RETURNS trigger AS $$
BEGIN
  IF OLD."notificationId" LIKE 'susp_%'
     AND NOT EXISTS (
       SELECT 1 FROM "NotificationDelivery" WHERE "notificationId" = OLD."notificationId"
     ) THEN
    DELETE FROM "Notification" WHERE "id" = OLD."notificationId";
  END IF;
  RETURN OLD;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS "nimhub_suspension_notification_cleanup" ON "NotificationDelivery";
CREATE TRIGGER "nimhub_suspension_notification_cleanup"
AFTER DELETE ON "NotificationDelivery"
FOR EACH ROW
EXECUTE FUNCTION "nimhub_cleanup_suspension_notification"();
