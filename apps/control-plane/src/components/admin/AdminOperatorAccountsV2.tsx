"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";

type Operator = {
  id: string;
  email: string;
  role: "ADMIN" | "SUPPORT";
  status: string;
  createdAt: string;
};

type ApiError = { error?: { message?: string } };

export function AdminOperatorAccountsV2({ operators }: { operators: Operator[] }) {
  const router = useRouter();
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    setBusy("create"); setError(""); setMessage("");
    try {
      const password = String(data.get("password") ?? "");
      const response = await fetch("/api/v1/admin/users", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          email: String(data.get("email") ?? ""),
          role: String(data.get("role") ?? "SUPPORT"),
          ...(password ? { password } : {}),
        }),
      });
      const body = await response.json().catch(() => null) as ApiError | null;
      if (!response.ok) { setError(body?.error?.message ?? "ایجاد حساب مدیریتی انجام نشد"); return; }
      form.reset();
      setMessage("حساب مدیریتی ایجاد شد.");
      router.refresh();
    } catch { setError("ارتباط با Control Plane برقرار نشد"); }
    finally { setBusy(""); }
  }

  async function update(operator: Operator, status: "ACTIVE" | "SUSPENDED", role: "ADMIN" | "SUPPORT") {
    setBusy(operator.id); setError(""); setMessage("");
    try {
      const response = await fetch("/api/v1/admin/users", {
        method: "PATCH",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ id: operator.id, status, role }),
      });
      const body = await response.json().catch(() => null) as ApiError | null;
      if (!response.ok) { setError(body?.error?.message ?? "تغییر دسترسی انجام نشد"); return; }
      setMessage("دسترسی حساب ذخیره شد.");
      router.refresh();
    } catch { setError("ارتباط با Control Plane برقرار نشد"); }
    finally { setBusy(""); }
  }

  return (
    <div className="v2-operator-admin" id="operator-accounts">
      <form className="v2-operator-create" onSubmit={create}>
        <div className="field"><label>ایمیل</label><input className="input" name="email" type="email" required dir="ltr" /></div>
        <div className="field"><label>Role</label><select className="select" name="role" defaultValue="SUPPORT"><option value="SUPPORT">SUPPORT</option><option value="ADMIN">ADMIN</option></select></div>
        <div className="field"><label>رمز اولیه (اختیاری)</label><input className="input" name="password" type="password" minLength={12} autoComplete="new-password" /></div>
        <button className="button" disabled={Boolean(busy)}>ایجاد Operator</button>
      </form>
      {error ? <p className="error">{error}</p> : null}
      {message ? <p className="v2-operator-message">{message}</p> : null}
      <div className="v2-operator-list">
        {operators.map((operator) => <OperatorRow key={operator.id} operator={operator} busy={busy === operator.id} onUpdate={update} />)}
        {!operators.length ? <div className="v2-settings-empty"><strong>Operator دیگری وجود ندارد</strong><span>حساب‌های ADMIN/SUPPORT در این بخش نمایش داده می‌شوند.</span></div> : null}
      </div>
    </div>
  );
}

function OperatorRow({ operator, busy, onUpdate }: {
  operator: Operator;
  busy: boolean;
  onUpdate: (operator: Operator, status: "ACTIVE" | "SUSPENDED", role: "ADMIN" | "SUPPORT") => Promise<void>;
}) {
  const [role, setRole] = useState<"ADMIN" | "SUPPORT">(operator.role);
  const [status, setStatus] = useState<"ACTIVE" | "SUSPENDED">(operator.status === "SUSPENDED" ? "SUSPENDED" : "ACTIVE");
  return (
    <div className="v2-operator-row">
      <span><strong dir="ltr">{operator.email}</strong><small>ساخته‌شده: {new Intl.DateTimeFormat("fa-IR", { year: "numeric", month: "short", day: "numeric" }).format(new Date(operator.createdAt))}</small></span>
      <select className="select" value={role} onChange={(event) => setRole(event.target.value as "ADMIN" | "SUPPORT")}><option value="SUPPORT">SUPPORT</option><option value="ADMIN">ADMIN</option></select>
      <select className="select" value={status} onChange={(event) => setStatus(event.target.value as "ACTIVE" | "SUSPENDED")}><option value="ACTIVE">ACTIVE</option><option value="SUSPENDED">SUSPENDED</option></select>
      <button className="button secondary" type="button" disabled={busy || (role === operator.role && status === operator.status)} onClick={() => void onUpdate(operator, status, role)}>ذخیره</button>
    </div>
  );
}
