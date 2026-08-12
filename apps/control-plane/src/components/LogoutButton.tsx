"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

export function LogoutButton() {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  return (
    <button
      className="button secondary"
      style={{ width: "100%" }}
      disabled={busy}
      onClick={async () => {
        setBusy(true);
        await fetch("/api/v1/admin/session", { method: "DELETE" });
        router.replace("/login");
        router.refresh();
      }}
    >
      خروج از پنل
    </button>
  );
}
