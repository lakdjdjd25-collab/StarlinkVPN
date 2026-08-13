"use client";

import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import { defaultSingBoxRuntimeConfig } from "@/lib/sing-box-config";

function useMutation(endpoint: string, method: "POST" | "PATCH" = "POST") {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function send(value: unknown) {
    setBusy(true);
    setError("");
    const response = await fetch(endpoint, {
      method,
      headers: { "content-type": "application/json" },
      body: JSON.stringify(value),
    });
    if (!response.ok) {
      const body = (await response.json().catch(() => null)) as { error?: { message?: string } } | null;
      setError(body?.error?.message ?? "ثبت اطلاعات انجام نشد");
      setBusy(false);
      return false;
    }
    setBusy(false);
    router.refresh();
    return true;
  }
  return { busy, error, send };
}

export function CreateUserForm() {
  const action = useMutation("/api/v1/admin/users");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    const done = await action.send({
      email: data.get("email"),
      password: data.get("password") || undefined,
      role: data.get("role"),
    });
    if (done) form.reset();
  }
  return (
    <form onSubmit={submit}>
      <div className="form-grid">
        <div className="field"><label>ایمیل</label><input className="input" name="email" type="email" required /></div>
        <div className="field"><label>نقش</label><select className="select" name="role" defaultValue="CUSTOMER"><option>CUSTOMER</option><option>SUPPORT</option><option>ADMIN</option></select></div>
        <div className="field"><label>گذرواژه اولیه (اختیاری)</label><input className="input" name="password" type="password" minLength={12} /></div>
      </div>
      {action.error ? <p className="error">{action.error}</p> : null}
      <button className="button" disabled={action.busy} style={{ marginTop: 14 }}>ایجاد کاربر</button>
    </form>
  );
}

type Option = { id: string; label: string };

export function CreateServiceForm({ users, plans }: { users: Option[]; plans: Option[] }) {
  const action = useMutation("/api/v1/admin/services");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    const done = await action.send({
      userId: data.get("userId"),
      planId: data.get("planId"),
      name: data.get("name"),
      license: data.get("license"),
      days: Number(data.get("days")),
    });
    if (done) form.reset();
  }
  return (
    <form onSubmit={submit}>
      <div className="form-grid">
        <div className="field"><label>کاربر</label><select className="select" name="userId" required>{users.map((item) => <option value={item.id} key={item.id}>{item.label}</option>)}</select></div>
        <div className="field"><label>پلن</label><select className="select" name="planId" required>{plans.map((item) => <option value={item.id} key={item.id}>{item.label}</option>)}</select></div>
        <div className="field"><label>نام سرویس</label><input className="input" name="name" defaultValue="سرویس شخصی" required /></div>
        <div className="field"><label>کلید مجوز</label><input className="input" name="license" minLength={6} required dir="ltr" /></div>
        <div className="field"><label>تعداد روز</label><input className="input" name="days" type="number" defaultValue={30} min={1} max={3650} required /></div>
      </div>
      {action.error ? <p className="error">{action.error}</p> : null}
      <button className="button" disabled={action.busy} style={{ marginTop: 14 }}>ایجاد سرویس</button>
    </form>
  );
}

export function CreateNodeForm({ regions }: { regions: Option[] }) {
  const action = useMutation("/api/v1/admin/nodes");
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
      <div className="field"><label>پیکربندی کامل sing-box (رمزگذاری می‌شود)</label><textarea className="textarea" name="config" rows={14} defaultValue={JSON.stringify(defaultSingBoxRuntimeConfig, null, 2)} dir="ltr" required /></div>
      <label style={{ display: "flex", gap: 8, marginTop: 12, color: "var(--muted)" }}><input type="checkbox" name="freeAllowed" /> قابل استفاده برای سرویس رایگان</label>
      {action.error ? <p className="error">{action.error}</p> : null}
      <button className="button" disabled={action.busy} style={{ marginTop: 14 }}>افزودن نود</button>
    </form>
  );
}

export function CreatePlanForm() {
  const action = useMutation("/api/v1/admin/plans");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    const done = await action.send({
      name: data.get("name"),
      interval: data.get("interval"),
      price: Number(data.get("price")),
      durationDays: Number(data.get("durationDays")),
      dataLimitGb: Number(data.get("dataLimitGb")),
      maxDevices: Number(data.get("maxDevices")),
      isPublic: data.get("isPublic") === "on",
    });
    if (done) form.reset();
  }
  return (
    <form onSubmit={submit}>
      <div className="form-grid">
        <div className="field"><label>نام پلن</label><input className="input" name="name" required /></div>
        <div className="field"><label>دوره</label><select className="select" name="interval" defaultValue="MONTHLY"><option>FREE</option><option>MONTHLY</option><option>YEARLY</option><option>CUSTOM</option></select></div>
        <div className="field"><label>قیمت</label><input className="input" name="price" type="number" min={0} step="0.01" required /></div>
        <div className="field"><label>مدت (روز)</label><input className="input" name="durationDays" type="number" min={1} defaultValue={30} required /></div>
        <div className="field"><label>حجم (GB)</label><input className="input" name="dataLimitGb" type="number" min="0.1" step="0.1" defaultValue={60} required /></div>
        <div className="field"><label>دستگاه هم‌زمان</label><input className="input" name="maxDevices" type="number" min={1} defaultValue={2} required /></div>
      </div>
      <label style={{ display: "flex", gap: 8, marginTop: 12, color: "var(--muted)" }}><input type="checkbox" name="isPublic" defaultChecked /> نمایش عمومی پلن</label>
      {action.error ? <p className="error">{action.error}</p> : null}
      <button className="button" disabled={action.busy} style={{ marginTop: 14 }}>ساخت پلن</button>
    </form>
  );
}

export function CreateRegionForm() {
  const action = useMutation("/api/v1/admin/regions");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    const done = await action.send({
      code: data.get("code"),
      name: data.get("name"),
      countryCode: data.get("countryCode"),
      priority: Number(data.get("priority")),
    });
    if (done) form.reset();
  }
  return (
    <form onSubmit={submit}>
      <div className="form-grid">
        <div className="field"><label>کد یکتا</label><input className="input" name="code" dir="ltr" pattern="[a-z0-9-]+" required /></div>
        <div className="field"><label>نام نمایشی</label><input className="input" name="name" required /></div>
        <div className="field"><label>کد کشور</label><input className="input" name="countryCode" dir="ltr" minLength={2} maxLength={2} required /></div>
        <div className="field"><label>اولویت</label><input className="input" name="priority" type="number" defaultValue={0} required /></div>
      </div>
      {action.error ? <p className="error">{action.error}</p> : null}
      <button className="button" disabled={action.busy} style={{ marginTop: 14 }}>افزودن منطقه</button>
    </form>
  );
}

export function AssignNodeForm({ services, nodes }: { services: Option[]; nodes: Option[] }) {
  const action = useMutation("/api/v1/admin/service-nodes");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    await action.send({
      serviceId: data.get("serviceId"),
      nodeId: data.get("nodeId"),
      priority: Number(data.get("priority")),
      enabled: true,
    });
  }
  return (
    <form onSubmit={submit}>
      <div className="form-grid">
        <div className="field"><label>سرویس</label><select className="select" name="serviceId" required>{services.map((item) => <option value={item.id} key={item.id}>{item.label}</option>)}</select></div>
        <div className="field"><label>نود</label><select className="select" name="nodeId" required>{nodes.map((item) => <option value={item.id} key={item.id}>{item.label}</option>)}</select></div>
        <div className="field"><label>اولویت اتصال</label><input className="input" name="priority" type="number" defaultValue={0} required /></div>
      </div>
      {action.error ? <p className="error">{action.error}</p> : null}
      <button className="button" disabled={action.busy || !services.length || !nodes.length} style={{ marginTop: 14 }}>اتصال نود به سرویس</button>
    </form>
  );
}

export function UserAccessForm({ id, status, role }: { id: string; status: string; role: string }) {
  const action = useMutation("/api/v1/admin/users", "PATCH");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    await action.send({ id, status: data.get("status"), role: data.get("role") });
  }
  return (
    <form onSubmit={submit} style={{ display: "flex", gap: 6, alignItems: "center", minWidth: 330 }}>
      <select className="select" name="status" defaultValue={status} aria-label="وضعیت"><option>ACTIVE</option><option>SUSPENDED</option></select>
      <select className="select" name="role" defaultValue={role} aria-label="نقش"><option>CUSTOMER</option><option>SUPPORT</option><option>ADMIN</option></select>
      <button className="button secondary" disabled={action.busy}>ثبت</button>
      {action.error ? <span className="error">{action.error}</span> : null}
    </form>
  );
}

export function NodeStatusForm({ id, status, capacity }: { id: string; status: string; capacity: number }) {
  const action = useMutation("/api/v1/admin/nodes", "PATCH");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    await action.send({ id, status: data.get("status"), capacity: Number(data.get("capacity")) });
  }
  return (
    <form onSubmit={submit} style={{ display: "flex", gap: 6, alignItems: "center", minWidth: 300 }}>
      <select className="select" name="status" defaultValue={status} aria-label="وضعیت نود"><option>ONLINE</option><option>DEGRADED</option><option>OFFLINE</option><option>MAINTENANCE</option></select>
      <input className="input" name="capacity" type="number" min={1} defaultValue={capacity} aria-label="ظرفیت" style={{ width: 88 }} />
      <button className="button secondary" disabled={action.busy}>ثبت</button>
    </form>
  );
}

export function ServiceUpdateForm({
  id,
  status,
  quotaGb,
  maxDevices,
  daysLeft,
}: {
  id: string;
  status: string;
  quotaGb: number;
  maxDevices: number;
  daysLeft: number;
}) {
  const action = useMutation("/api/v1/admin/services", "PATCH");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    await action.send({
      id,
      status: data.get("status"),
      quotaGb: Number(data.get("quotaGb")),
      daysFromNow: Number(data.get("daysFromNow")),
      maxDevices: Number(data.get("maxDevices")),
    });
  }
  return (
    <form onSubmit={submit} style={{ display: "grid", gridTemplateColumns: "1fr 88px 72px 72px auto", gap: 6, minWidth: 500 }}>
      <select className="select" name="status" defaultValue={status} aria-label="وضعیت سرویس"><option>ACTIVE</option><option>SUSPENDED</option><option>EXPIRED</option><option>CANCELLED</option></select>
      <input className="input" name="quotaGb" type="number" min={0} step="0.1" defaultValue={quotaGb} title="حجم GB" />
      <input className="input" name="daysFromNow" type="number" min={0} defaultValue={daysLeft} title="اعتبار از امروز" />
      <input className="input" name="maxDevices" type="number" min={1} defaultValue={maxDevices} title="دستگاه" />
      <button className="button secondary" disabled={action.busy}>ثبت</button>
    </form>
  );
}

export function GlobalSettingForm() {
  const action = useMutation("/api/v1/admin/settings");
  const [jsonError, setJsonError] = useState("");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    let value: unknown;
    try {
      value = JSON.parse(String(data.get("value")));
      setJsonError("");
    } catch {
      setJsonError("ساختار JSON معتبر نیست");
      return;
    }
    await action.send({ key: data.get("key"), value, description: data.get("description") || undefined });
  }
  return (
    <form onSubmit={submit}>
      <div className="form-grid">
        <div className="field"><label>کلید</label><input className="input" name="key" defaultValue="client.bootstrap" dir="ltr" required /></div>
        <div className="field"><label>توضیح</label><input className="input" name="description" /></div>
      </div>
      <div className="field"><label>مقدار JSON</label><textarea className="textarea" name="value" rows={7} dir="ltr" defaultValue={'{\n  "maintenance": false,\n  "signupEnabled": true,\n  "minimumAndroidVersionCode": 160162\n}'} required /></div>
      {jsonError || action.error ? <p className="error">{jsonError || action.error}</p> : null}
      <button className="button" disabled={action.busy} style={{ marginTop: 14 }}>ذخیره تنظیم</button>
    </form>
  );
}

export function CreateNotificationForm({ users }: { users: Option[] }) {
  const action = useMutation("/api/v1/admin/notifications");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    const selected = String(data.get("selectedUserIds") ?? "")
      .split(",")
      .map((item) => item.trim())
      .filter(Boolean);
    const done = await action.send({
      title: data.get("title"),
      body: data.get("body"),
      audience: data.get("audience"),
      actionUrl: data.get("actionUrl") || undefined,
      publishNow: data.get("publishNow") === "on",
      selectedUserIds: selected,
    });
    if (done) form.reset();
  }
  return (
    <form onSubmit={submit}>
      <div className="form-grid">
        <div className="field"><label>عنوان</label><input className="input" name="title" required /></div>
        <div className="field"><label>مخاطب</label><select className="select" name="audience" defaultValue="ALL"><option>ALL</option><option>FREE</option><option>PAID</option><option>SELECTED</option></select></div>
        <div className="field"><label>پیوند اقدام (اختیاری)</label><input className="input" name="actionUrl" type="url" dir="ltr" /></div>
        <div className="field"><label>شناسه کاربران انتخابی با ویرگول</label><input className="input" name="selectedUserIds" list="quickping-user-ids" dir="ltr" /></div>
      </div>
      <datalist id="quickping-user-ids">{users.map((user) => <option value={user.id} key={user.id}>{user.label}</option>)}</datalist>
      <div className="field"><label>متن اعلان</label><textarea className="textarea" name="body" rows={5} required /></div>
      <label style={{ display: "flex", gap: 8, marginTop: 12, color: "var(--muted)" }}><input type="checkbox" name="publishNow" defaultChecked /> انتشار فوری</label>
      {action.error ? <p className="error">{action.error}</p> : null}
      <button className="button" disabled={action.busy} style={{ marginTop: 14 }}>ثبت اعلان</button>
    </form>
  );
}

export function CreateReleaseForm() {
  const action = useMutation("/api/v1/admin/releases");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    const done = await action.send({
      platform: data.get("platform"),
      versionName: data.get("versionName"),
      versionCode: Number(data.get("versionCode")),
      minimumVersionCode: Number(data.get("minimumVersionCode")),
      mandatory: data.get("mandatory") === "on",
      changelog: data.get("changelog"),
      downloadUrl: data.get("downloadUrl"),
      sha256: data.get("sha256"),
      publishNow: data.get("publishNow") === "on",
    });
    if (done) form.reset();
  }
  return (
    <form onSubmit={submit}>
      <div className="form-grid">
        <div className="field"><label>پلتفرم</label><select className="select" name="platform"><option>ANDROID</option><option>WINDOWS</option></select></div>
        <div className="field"><label>نام نسخه</label><input className="input" name="versionName" placeholder="2.6.0" dir="ltr" required /></div>
        <div className="field"><label>کد نسخه</label><input className="input" name="versionCode" type="number" min={1} dir="ltr" required /></div>
        <div className="field"><label>حداقل کد مجاز</label><input className="input" name="minimumVersionCode" type="number" min={0} dir="ltr" required /></div>
        <div className="field"><label>پیوند دانلود</label><input className="input" name="downloadUrl" type="url" dir="ltr" required /></div>
        <div className="field"><label>SHA-256</label><input className="input" name="sha256" pattern="[a-fA-F0-9]{64}" minLength={64} maxLength={64} dir="ltr" required /></div>
      </div>
      <div className="field"><label>تغییرات نسخه</label><textarea className="textarea" name="changelog" rows={5} required /></div>
      <div style={{ display: "flex", gap: 18, marginTop: 12, color: "var(--muted)" }}>
        <label><input type="checkbox" name="publishNow" defaultChecked /> انتشار فوری</label>
        <label><input type="checkbox" name="mandatory" /> به‌روزرسانی اجباری</label>
      </div>
      {action.error ? <p className="error">{action.error}</p> : null}
      <button className="button" disabled={action.busy} style={{ marginTop: 14 }}>ثبت نسخه</button>
    </form>
  );
}
