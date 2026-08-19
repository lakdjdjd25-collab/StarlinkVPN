"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { defaultSingBoxRuntimeConfig } from "@/lib/sing-box-config";

type Option = { id: string; label: string };

function useMutation(method: "POST" | "PATCH") {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function send(value: unknown) {
    setBusy(true);
    setError("");
    try {
      const response = await fetch("/api/v1/admin/nodes", {
        method,
        headers: { "content-type": "application/json" },
        body: JSON.stringify(value),
      });
      if (!response.ok) {
        const body = await response.json().catch(() => null) as { error?: { message?: string } } | null;
        setError(body?.error?.message ?? "ذخیره سرور انجام نشد");
        return false;
      }
      router.refresh();
      return true;
    } finally {
      setBusy(false);
    }
  }
  return { busy, error, send };
}

export function VipCreateNodeForm({ regions }: { regions: Option[] }) {
  const action = useMutation("POST");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    let config: unknown;
    try {
      config = JSON.parse(String(data.get("config")));
    } catch {
      return;
    }
    const done = await action.send({
      regionId: data.get("regionId"),
      name: data.get("name"),
      host: data.get("host"),
      port: Number(data.get("port")),
      protocol: data.get("protocol"),
      capacity: Number(data.get("capacity")),
      freeAllowed: data.get("freeAllowed") === "on",
      accessTier: data.get("vip") === "on" ? "VIP" : "STANDARD",
      config,
    });
    if (done) form.reset();
  }

  return (
    <form onSubmit={submit}>
      <div className="form-grid">
        <div className="field"><label>منطقه</label><select className="select" name="regionId" required>{regions.map((item) => <option value={item.id} key={item.id}>{item.label}</option>)}</select></div>
        <div className="field"><label>نام نود</label><input className="input" name="name" required /></div>
        <div className="field"><label>میزبان</label><input className="input" name="host" required dir="ltr" /></div>
        <div className="field"><label>پورت</label><input className="input" name="port" type="number" min={1} max={65535} required /></div>
        <div className="field"><label>پروتکل</label><select className="select" name="protocol" defaultValue="SINGBOX"><option>VLESS</option><option>VMESS</option><option>TROJAN</option><option>WIREGUARD</option><option>HYSTERIA2</option><option>SINGBOX</option><option>XRAY</option></select></div>
        <div className="field"><label>ظرفیت</label><input className="input" name="capacity" type="number" min={1} defaultValue={1000} /></div>
      </div>
      <div className="field"><label>پیکربندی کامل sing-box</label><textarea className="textarea" name="config" rows={14} defaultValue={JSON.stringify(defaultSingBoxRuntimeConfig, null, 2)} dir="ltr" required /></div>
      <div style={{ display: "flex", gap: 18, flexWrap: "wrap", marginTop: 12 }}>
        <label style={{ display: "flex", gap: 8, alignItems: "center", color: "var(--muted)" }}><input type="checkbox" name="freeAllowed" /> قابل استفاده برای سرویس رایگان</label>
        <label style={{ display: "flex", gap: 8, alignItems: "center", color: "var(--muted)" }}><input type="checkbox" name="vip" /> <strong style={{ color: "#d8ccff" }}>سرور VIP</strong></label>
      </div>
      <p style={{ color: "var(--muted)", fontSize: 12 }}>VIP فقط سطح دسترسی NimHUB است؛ رمز و کانفیگ سرور همچنان فقط از Backend تحویل داده می‌شود.</p>
      {action.error ? <p className="error">{action.error}</p> : null}
      <button className="button" disabled={action.busy} style={{ marginTop: 14 }}>افزودن نود</button>
    </form>
  );
}

export function VipNodeControlForm({
  id,
  status,
  capacity,
  accessTier,
  logicalScope,
}: {
  id: string;
  status: string;
  capacity?: number | null;
  accessTier: "STANDARD" | "VIP";
  logicalScope?: "PASARGUARD";
}) {
  const action = useMutation("PATCH");
  const [vip, setVip] = useState(accessTier === "VIP");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    await action.send({
      id,
      status: data.get("status"),
      ...(capacity != null ? { capacity: Number(data.get("capacity")) } : {}),
      accessTier: vip ? "VIP" : "STANDARD",
      ...(logicalScope ? { scope: logicalScope } : {}),
    });
  }
  return (
    <form onSubmit={submit} style={{ display: "flex", gap: 6, alignItems: "center", minWidth: logicalScope ? 280 : 390, flexWrap: "wrap" }}>
      <select className="select" name="status" defaultValue={status} aria-label="وضعیت نود"><option>ONLINE</option><option>DEGRADED</option><option>OFFLINE</option><option>MAINTENANCE</option></select>
      {capacity != null ? <input className="input" name="capacity" type="number" min={1} defaultValue={capacity} aria-label="ظرفیت" style={{ width: 88 }} /> : null}
      <label style={{ display: "flex", alignItems: "center", gap: 5, whiteSpace: "nowrap", color: vip ? "#d8ccff" : "var(--muted)" }}><input type="checkbox" checked={vip} onChange={(event) => setVip(event.target.checked)} /> VIP</label>
      <button className="button secondary" disabled={action.busy}>ثبت</button>
      {action.error ? <span className="error">{action.error}</span> : null}
    </form>
  );
}
