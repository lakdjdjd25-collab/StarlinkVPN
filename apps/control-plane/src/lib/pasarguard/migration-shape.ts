import { PasarGuardError } from "./client";

export type MigrationTransferShape = {
  usageOffsetBytes: bigint;
  remoteDataLimitBytes: bigint;
  remainingBytes: bigint;
};

export function migrationTransferShape(
  totalBytes: bigint,
  usedBytes: bigint,
  remoteUsedBytes: bigint,
): MigrationTransferShape {
  if (totalBytes <= 0n || usedBytes < 0n || remoteUsedBytes < 0n) {
    throw new PasarGuardError("invalid_response", "وضعیت حجم سرویس برای انتقال معتبر نیست");
  }
  if (usedBytes >= totalBytes) {
    throw new PasarGuardError("invalid_response", "حجم این سرویس تمام شده است؛ پس از افزایش حجم می‌توان آن را منتقل کرد");
  }
  if (remoteUsedBytes > usedBytes) {
    throw new PasarGuardError(
      "invalid_response",
      "کاربر هم‌نام در پنل جدید مصرف بیشتری از سابقه NimHUB دارد و نیازمند بررسی دستی است",
    );
  }
  const remainingBytes = totalBytes - usedBytes;
  const usageOffsetBytes = usedBytes - remoteUsedBytes;
  const remoteDataLimitBytes = totalBytes - usageOffsetBytes;
  if (remoteDataLimitBytes <= remoteUsedBytes) {
    throw new PasarGuardError("invalid_response", "حجم باقی‌مانده برای انتقال معتبر نیست");
  }
  return { usageOffsetBytes, remoteDataLimitBytes, remainingBytes };
}
