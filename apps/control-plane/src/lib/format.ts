export function formatBytes(value: bigint | number | string): string {
  const bytes = typeof value === "bigint" ? Number(value) : Number(value);
  if (!Number.isFinite(bytes) || bytes <= 0) return "0 GB";
  return `${(bytes / 1024 / 1024 / 1024).toFixed(1)} GB`;
}

export function formatDate(value: Date | string | null): string {
  if (!value) return "—";
  return new Intl.DateTimeFormat("fa-IR", {
    year: "numeric",
    month: "short",
    day: "numeric",
  }).format(new Date(value));
}
