"use client";

import { useRouter } from "next/navigation";
import Image from "next/image";
import { FormEvent, useState } from "react";

export default function LoginPage() {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    const data = new FormData(event.currentTarget);
    const response = await fetch("/api/v1/admin/session", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        email: data.get("email"),
        password: data.get("password"),
      }),
    });
    if (!response.ok) {
      const body = (await response.json().catch(() => null)) as
        | { error?: { message?: string } }
        | null;
      setError(body?.error?.message ?? "ورود انجام نشد");
      setBusy(false);
      return;
    }
    router.replace("/admin");
    router.refresh();
  }

  return (
    <main className="login-page">
      <form className="login-card" onSubmit={submit}>
        <div className="brand-mark">
          <Image className="brand-logo" src="/nimhub-logo.png" width={42} height={42} alt="" />
          <span>NIMHUB</span>
        </div>
        <h1>ورود به پنل مدیریت</h1>
        <p style={{ color: "var(--muted)" }}>
          برای مدیریت کاربران، سرویس‌ها و سرورهای VPN وارد شوید.
        </p>
        <div className="field">
          <label htmlFor="email">ایمیل مدیر</label>
          <input id="email" name="email" className="input" type="email" required autoComplete="username" />
        </div>
        <div className="field">
          <label htmlFor="password">گذرواژه</label>
          <input id="password" name="password" className="input" type="password" required autoComplete="current-password" />
        </div>
        {error ? <p className="error">{error}</p> : null}
        <button className="button" type="submit" disabled={busy} style={{ width: "100%", marginTop: 18 }}>
          {busy ? "در حال بررسی…" : "ورود امن"}
        </button>
      </form>
    </main>
  );
}
