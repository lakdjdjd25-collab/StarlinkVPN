"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { AdminIcon } from "./AdminIcon";

type ProviderState = "LOCAL" | "MIGRATION_REQUIRED" | "OFFLINE" | "STALE" | "SYNCED";
type Tone = "success" | "warning" | "danger" | "neutral" | "vip" | "info";

type ServiceItem = {
  id: string;
  name: string;
  license: string;
  status: string;
  quotaBytes: string;
  usedBytes: string;
  remainingBytes: string;
  expiresAt: string;
  maxDevices: number;
  vipAccess: boolean;
  serverGroup: string;
  serverGroupKey: string | null;
  serverCount: number;
  providerState: ProviderState;
  providerName: string | null;
  remoteUsername: string | null;
  lastSyncAt: string | null;
  providerError: string | null;
};

type DeviceItem = {
  id: string;
  name: string;
  platform: string;
  appVersion: string | null;
  lastSeenAt: string;
  revokedAt: string | null;
  status: "ACTIVE" | "REVOKED";
};

type ActivityItem = {
  id: string;
  action: string;
  entityType: string;
  createdAt: string;
  actor: string;
};

type UserItem = {
  id: string;
  name: string;
  email: string;
  accountStatus: string;
  managedAccount: boolean;
  createdAt: string;
  serviceCount: number;
  activeDevices: number;
  primaryServiceId: string | null;
  primaryService: ServiceItem | null;
  services: ServiceItem[];
  devices: DeviceItem[];
  warning: { code: string; label: string; tone: "warning" | "danger" } | null;
  activity: ActivityItem[];
};

type UsersResponse = {
  items: UserItem[];
  pagination: { page: number; pageSize: number; total: number; pages: number };
  provider: { id: string; name: string } | null;
};

type DrawerTab = "overview" | "subscription" | "devices" | "traffic" | "credentials" | "activity" | "advanced";

type CredentialReceipt = { email: string; initialPassword: string };

const tabLabels: Array<{ id: DrawerTab; label: string }> = [
  { id: "overview", label: "نمای کلی" },
  { id: "subscription", label: "اشتراک" },
  { id: "devices", label: "دستگاه‌ها" },
  { id: "traffic", label: "ترافیک" },
  { id: "credentials", label: "ورود و مجوز" },
  { id: "activity", label: "فعالیت" },
  { id: "advanced", label: "پیشرفته" },
];

const activityLabels: Record<string, string> = {
  "managed_license.create": "کاربر و مجوز ساخته شد",
  "managed_license.update": "اشتراک ویرایش شد",
  "managed_license.resetCredentials": "رمز ورود بازنشانی شد",
  "managed_license.migrateProvider": "Provider اشتراک تغییر کرد",
  "user.suspend": "حساب کاربر معلق شد",
  "user.reactivate": "حساب کاربر فعال شد",
  "service.vipAccess": "دسترسی VIP تغییر کرد",
  "service.addTraffic": "حجم اضافه شد",
  "service.extend": "اشتراک تمدید شد",
  "service.suspend": "اشتراک متوقف شد",
  "service.reactivate": "اشتراک فعال شد",
  "service.deviceLimit": "محدودیت دستگاه تغییر کرد",
  "device.revoke": "یک دستگاه لغو شد",
  "device.revokeAll": "همه دستگاه‌ها لغو شدند",
};

function formatNumber(value: number): string {
  return new Intl.NumberFormat("fa-IR").format(value);
}

function formatBytes(value: string): string {
  const bytes = Number(value);
  if (!Number.isFinite(bytes) || bytes <= 0) return "0 GB";
  const gb = bytes / 1024 ** 3;
  return `${new Intl.NumberFormat("fa-IR", { maximumFractionDigits: gb < 10 ? 1 : 0 }).format(gb)} GB`;
}

function formatDate(value: string | null): string {
  if (!value) return "—";
  return new Intl.DateTimeFormat("fa-IR", { year: "numeric", month: "short", day: "numeric" }).format(new Date(value));
}

function formatDateTime(value: string | null): string {
  if (!value) return "—";
  return new Intl.DateTimeFormat("fa-IR", {
    year: "numeric", month: "short", day: "numeric", hour: "2-digit", minute: "2-digit",
  }).format(new Date(value));
}

function usagePercent(service: ServiceItem | null): number {
  if (!service) return 0;
  const quota = Math.max(1, Number(service.quotaBytes));
  return Math.min(100, Math.max(0, Math.round(Number(service.usedBytes) / quota * 100)));
}

function providerLabel(state: ProviderState): string {
  return state === "SYNCED" ? "Synced"
    : state === "STALE" ? "Stale"
      : state === "OFFLINE" ? "Offline"
        : state === "MIGRATION_REQUIRED" ? "نیازمند انتقال"
          : "Local";
}

function providerTone(state: ProviderState): Tone {
  return state === "SYNCED" ? "success"
    : state === "STALE" || state === "MIGRATION_REQUIRED" ? "warning"
      : state === "OFFLINE" ? "danger"
        : "neutral";
}

function StatusBadge({ tone, children }: { tone: Tone; children: React.ReactNode }) {
  return <span className={`v2-user-badge is-${tone}`}>{children}</span>;
}

async function copyText(value: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(value);
    return true;
  } catch {
    return false;
  }
}

export function AdminUsersV2() {
  const [items, setItems] = useState<UserItem[]>([]);
  const [pagination, setPagination] = useState({ page: 1, pageSize: 25, total: 0, pages: 1 });
  const [provider, setProvider] = useState<UsersResponse["provider"]>(null);
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [status, setStatus] = useState("ALL");
  const [vip, setVip] = useState("ALL");
  const [expiring, setExpiring] = useState(false);
  const [attention, setAttention] = useState(false);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [drawerTab, setDrawerTab] = useState<DrawerTab>("overview");
  const [busy, setBusy] = useState("");
  const [credentialReceipt, setCredentialReceipt] = useState<CredentialReceipt | null>(null);
  const [toast, setToast] = useState<{ text: string; tone: "success" | "danger" } | null>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedQuery(query.trim());
      setPage(1);
    }, 280);
    return () => window.clearTimeout(timer);
  }, [query]);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), 3200);
    return () => window.clearTimeout(timer);
  }, [toast]);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    const params = new URLSearchParams({ page: String(page), pageSize: "25" });
    if (debouncedQuery) params.set("q", debouncedQuery);
    if (status !== "ALL") params.set("status", status);
    if (vip !== "ALL") params.set("vip", vip);
    if (expiring) params.set("expiring", "1");
    if (attention) params.set("attention", "1");
    try {
      const response = await fetch(`/api/v1/admin/control-center/users?${params.toString()}`, { cache: "no-store" });
      const body = await response.json().catch(() => null) as { data?: UsersResponse; error?: { message?: string } } | null;
      if (!response.ok || !body?.data) {
        setError(body?.error?.message ?? "دریافت کاربران انجام نشد");
        return;
      }
      setItems(body.data.items);
      setPagination(body.data.pagination);
      setProvider(body.data.provider);
    } catch {
      setError("ارتباط با کنترل‌پلین برای دریافت کاربران برقرار نشد");
    } finally {
      setLoading(false);
    }
  }, [attention, debouncedQuery, expiring, page, status, vip]);

  useEffect(() => { void load(); }, [load]);

  const selected = useMemo(() => items.find((item) => item.id === selectedId) ?? null, [items, selectedId]);

  function openUser(id: string) {
    setCredentialReceipt(null);
    setDrawerTab("overview");
    setSelectedId(id);
  }

  function closeDrawer() {
    setSelectedId(null);
    setCredentialReceipt(null);
  }

  async function mutate(payload: Record<string, unknown>, success: string, receipt = false) {
    const actionKey = `${String(payload.action)}:${String(payload.serviceId ?? payload.deviceId ?? payload.userId ?? "")}`;
    const endpoint = payload.action === "set_account_status"
      ? "/api/v1/admin/control-center/users/account-status"
      : "/api/v1/admin/control-center/users";
    setBusy(actionKey);
    setError("");
    try {
      const response = await fetch(endpoint, {
        method: "PATCH",
        headers: { "content-type": "application/json" },
        body: JSON.stringify(payload),
      });
      const body = await response.json().catch(() => null) as {
        data?: { credentials?: CredentialReceipt };
        error?: { message?: string; details?: { technical?: string } };
      } | null;
      if (!response.ok) {
        const technical = body?.error?.details?.technical;
        setToast({ text: technical ? `${body?.error?.message ?? "عملیات انجام نشد"} — ${technical}` : body?.error?.message ?? "عملیات انجام نشد", tone: "danger" });
        return false;
      }
      if (receipt && body?.data?.credentials) setCredentialReceipt(body.data.credentials);
      setToast({ text: success, tone: "success" });
      await load();
      return true;
    } catch {
      setToast({ text: "ارتباط با سرور هنگام ذخیره تغییرات قطع شد", tone: "danger" });
      return false;
    } finally {
      setBusy("");
    }
  }

  function resetFilters() {
    setQuery(""); setDebouncedQuery(""); setStatus("ALL"); setVip("ALL"); setExpiring(false); setAttention(false); setPage(1);
  }

  const hasFilters = Boolean(query || status !== "ALL" || vip !== "ALL" || expiring || attention);

  return (
    <div className="v2-users-page">
      <div className="v2-users-toolbar">
        <div className="v2-users-search">
          <AdminIcon name="search" size={17} />
          <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="نام، ایمیل، مجوز یا Remote Username…" aria-label="جست‌وجوی کاربران" />
        </div>
        <div className="v2-users-filters">
          <select value={status} onChange={(event) => { setStatus(event.target.value); setPage(1); }} aria-label="وضعیت حساب">
            <option value="ALL">همه وضعیت‌ها</option><option value="ACTIVE">فعال</option><option value="SUSPENDED">معلق</option>
          </select>
          <select value={vip} onChange={(event) => { setVip(event.target.value); setPage(1); }} aria-label="نوع دسترسی">
            <option value="ALL">VIP + Standard</option><option value="VIP">فقط VIP</option><option value="STANDARD">فقط Standard</option>
          </select>
          <button type="button" className={`v2-filter-chip${expiring ? " is-active" : ""}`} onClick={() => { setExpiring((value) => !value); setPage(1); }}>نزدیک انقضا</button>
          <button type="button" className={`v2-filter-chip${attention ? " is-active" : ""}`} onClick={() => { setAttention((value) => !value); setPage(1); }}>نیازمند بررسی</button>
          {hasFilters ? <button type="button" className="v2-filter-reset" onClick={resetFilters}>پاک کردن</button> : null}
        </div>
        <div className="v2-users-count"><strong>{formatNumber(pagination.total)}</strong><span>کاربر</span></div>
      </div>

      {error ? <div className="v2-users-error"><span className="v2-status-dot is-danger" /><span>{error}</span><button type="button" onClick={() => void load()}>تلاش دوباره</button></div> : null}

      <div className="v2-users-table-wrap" aria-busy={loading}>
        <table className="v2-users-table">
          <thead><tr><th>کاربر</th><th>وضعیت</th><th>مصرف / مانده</th><th>انقضا</th><th>دستگاه</th><th>دسترسی</th><th>گروه سرور</th><th>هشدار</th><th aria-label="عملیات" /></tr></thead>
          <tbody>
            {loading ? Array.from({ length: 7 }, (_, index) => <SkeletonRow key={index} />) : null}
            {!loading && items.map((user) => <UserRow user={user} key={user.id} onOpen={() => openUser(user.id)} />)}
          </tbody>
        </table>
        {!loading && !items.length ? <div className="v2-users-empty"><AdminIcon name="users" size={23} /><strong>کاربری پیدا نشد</strong><span>فیلترها یا عبارت جست‌وجو را تغییر بده.</span></div> : null}
      </div>

      <div className="v2-users-mobile-list">
        {loading ? Array.from({ length: 5 }, (_, index) => <div className="v2-mobile-user-card is-skeleton" key={index} />) : null}
        {!loading && items.map((user) => <MobileUserCard user={user} key={user.id} onOpen={() => openUser(user.id)} />)}
      </div>

      <div className="v2-pagination">
        <span>صفحه {formatNumber(pagination.page)} از {formatNumber(pagination.pages)}</span>
        <div><button type="button" disabled={page <= 1 || loading} onClick={() => setPage((value) => Math.max(1, value - 1))}>قبلی</button><button type="button" disabled={page >= pagination.pages || loading} onClick={() => setPage((value) => value + 1)}>بعدی</button></div>
      </div>

      {selected ? (
        <UserDrawer
          user={selected}
          providerName={provider?.name ?? null}
          tab={drawerTab}
          setTab={setDrawerTab}
          busy={busy}
          credentialReceipt={credentialReceipt}
          onClose={closeDrawer}
          onMutate={mutate}
          onToast={setToast}
        />
      ) : null}

      {toast ? <div className={`v2-toast is-${toast.tone}`} role="status"><span className={`v2-status-dot is-${toast.tone}`} />{toast.text}</div> : null}
    </div>
  );
}

function UserRow({ user, onOpen }: { user: UserItem; onOpen: () => void }) {
  const service = user.primaryService;
  const percent = usagePercent(service);
  return (
    <tr className="v2-user-row" onDoubleClick={onOpen}>
      <td><div className="v2-user-identity"><span className="v2-user-avatar">{user.name.slice(0, 1).toUpperCase()}</span><span><strong>{user.name}</strong><small dir="ltr">{user.email}</small></span></div></td>
      <td><div className="v2-badge-stack"><StatusBadge tone={user.accountStatus === "ACTIVE" ? "success" : "danger"}>{user.accountStatus === "ACTIVE" ? "حساب فعال" : "حساب معلق"}</StatusBadge>{service && service.status !== "ACTIVE" ? <StatusBadge tone="danger">اشتراک متوقف</StatusBadge> : null}</div></td>
      <td>{service ? <div className="v2-usage-cell"><div><span>{formatBytes(service.usedBytes)}</span><small>از {formatBytes(service.quotaBytes)}</small></div><div className="v2-mini-progress"><i style={{ width: `${percent}%` }} /></div><small>مانده {formatBytes(service.remainingBytes)}</small></div> : <span className="v2-muted">بدون اشتراک</span>}</td>
      <td>{service ? <span className="v2-date-cell"><strong>{formatDate(service.expiresAt)}</strong><small>{new Date(service.expiresAt).getTime() <= Date.now() ? "منقضی" : "فعال"}</small></span> : "—"}</td>
      <td><span className="v2-device-count"><strong>{formatNumber(user.activeDevices)}</strong><small> / {service?.maxDevices ?? "—"}</small></span></td>
      <td>{service?.vipAccess ? <StatusBadge tone="vip">VIP</StatusBadge> : <StatusBadge tone="neutral">Standard</StatusBadge>}</td>
      <td><span className="v2-group-cell" title={service?.serverGroup ?? ""}>{service?.serverGroup ?? "—"}</span></td>
      <td>{user.warning ? <StatusBadge tone={user.warning.tone}>{user.warning.label}</StatusBadge> : <span className="v2-ok-state"><span className="v2-status-dot is-success" />سالم</span>}</td>
      <td><button type="button" className="v2-row-action" onClick={onOpen} aria-label={`مدیریت ${user.name}`}><AdminIcon name="sliders" size={16} /></button></td>
    </tr>
  );
}

function MobileUserCard({ user, onOpen }: { user: UserItem; onOpen: () => void }) {
  const service = user.primaryService;
  return (
    <button type="button" className="v2-mobile-user-card" onClick={onOpen}>
      <div className="v2-mobile-user-head"><span className="v2-user-avatar">{user.name.slice(0, 1).toUpperCase()}</span><span><strong>{user.name}</strong><small dir="ltr">{user.email}</small></span><AdminIcon name="chevron-left" size={17} /></div>
      <div className="v2-mobile-user-facts"><span><small>مانده</small><strong>{service ? formatBytes(service.remainingBytes) : "—"}</strong></span><span><small>انقضا</small><strong>{service ? formatDate(service.expiresAt) : "—"}</strong></span><span><small>دستگاه</small><strong>{user.activeDevices}/{service?.maxDevices ?? "—"}</strong></span></div>
      <div className="v2-mobile-user-badges"><StatusBadge tone={user.accountStatus === "ACTIVE" ? "success" : "danger"}>{user.accountStatus === "ACTIVE" ? "فعال" : "معلق"}</StatusBadge>{service?.vipAccess ? <StatusBadge tone="vip">VIP</StatusBadge> : <StatusBadge tone="neutral">Standard</StatusBadge>}{user.warning ? <StatusBadge tone={user.warning.tone}>{user.warning.label}</StatusBadge> : null}</div>
    </button>
  );
}

function SkeletonRow() {
  return <tr className="v2-skeleton-row"><td><i /></td><td><i /></td><td><i /></td><td><i /></td><td><i /></td><td><i /></td><td><i /></td><td><i /></td><td><i /></td></tr>;
}

function UserDrawer({
  user, providerName, tab, setTab, busy, credentialReceipt, onClose, onMutate, onToast,
}: {
  user: UserItem;
  providerName: string | null;
  tab: DrawerTab;
  setTab: (tab: DrawerTab) => void;
  busy: string;
  credentialReceipt: CredentialReceipt | null;
  onClose: () => void;
  onMutate: (payload: Record<string, unknown>, success: string, receipt?: boolean) => Promise<boolean>;
  onToast: (toast: { text: string; tone: "success" | "danger" }) => void;
}) {
  const service = user.primaryService;
  const [customTraffic, setCustomTraffic] = useState("");
  const [customDays, setCustomDays] = useState("");
  const [deviceLimit, setDeviceLimit] = useState(String(service?.maxDevices ?? 1));

  useEffect(() => setDeviceLimit(String(service?.maxDevices ?? 1)), [service?.maxDevices, user.id]);

  async function addTraffic(gb: number) {
    if (!service || !Number.isFinite(gb) || gb <= 0) return;
    await onMutate({ action: "add_traffic", serviceId: service.id, gb }, `${gb} GB به حجم کل اضافه شد.`);
  }

  async function extend(days: number) {
    if (!service || !Number.isFinite(days) || days <= 0) return;
    await onMutate({ action: "extend", serviceId: service.id, days }, `${days} روز به اعتبار اشتراک اضافه شد.`);
  }

  return (
    <div className="v2-drawer-layer" role="presentation" onMouseDown={(event) => { if (event.currentTarget === event.target) onClose(); }}>
      <aside className="v2-user-drawer" role="dialog" aria-modal="true" aria-label={`مدیریت ${user.name}`}>
        <header className="v2-drawer-head">
          <div className="v2-user-identity"><span className="v2-user-avatar is-large">{user.name.slice(0, 1).toUpperCase()}</span><span><strong>{user.name}</strong><small dir="ltr">{user.email}</small><div className="v2-drawer-badges"><StatusBadge tone={user.accountStatus === "ACTIVE" ? "success" : "danger"}>{user.accountStatus === "ACTIVE" ? "حساب فعال" : "حساب معلق"}</StatusBadge>{service?.vipAccess ? <StatusBadge tone="vip">VIP</StatusBadge> : null}{service ? <StatusBadge tone={providerTone(service.providerState)}>{providerLabel(service.providerState)}</StatusBadge> : null}</div></span></div>
          <button type="button" className="v2-drawer-close" onClick={onClose} aria-label="بستن"><AdminIcon name="x" size={18} /></button>
        </header>

        <nav className="v2-drawer-tabs" aria-label="بخش‌های کاربر">{tabLabels.map((item) => <button type="button" className={tab === item.id ? "is-active" : ""} onClick={() => setTab(item.id)} key={item.id}>{item.label}</button>)}</nav>

        <div className="v2-drawer-body">
          {tab === "overview" ? <OverviewTab user={user} busy={busy} onMutate={onMutate} /> : null}
          {tab === "subscription" ? (
            <section className="v2-drawer-section">
              <SectionHead title="مدیریت اشتراک" note="حجم و اعتبار به صورت افزایشی اعمال می‌شوند و مصرف صفر نمی‌شود." />
              {service ? <>
                <UsageBlock service={service} />
                <div className="v2-action-block"><label>افزودن حجم</label><div className="v2-preset-actions">{[10, 30, 50].map((value) => <button type="button" disabled={Boolean(busy)} onClick={() => void addTraffic(value)} key={value}>+{value} GB</button>)}</div><div className="v2-custom-action"><input type="number" min="0.1" step="0.1" value={customTraffic} onChange={(event) => setCustomTraffic(event.target.value)} placeholder="حجم دلخواه" /><button type="button" disabled={Boolean(busy) || Number(customTraffic) <= 0} onClick={() => void addTraffic(Number(customTraffic))}>اعمال</button></div></div>
                <div className="v2-action-block"><label>تمدید اعتبار</label><div className="v2-preset-actions">{[30, 90, 180].map((value) => <button type="button" disabled={Boolean(busy)} onClick={() => void extend(value)} key={value}>+{value} روز</button>)}</div><div className="v2-custom-action"><input type="number" min="1" step="1" value={customDays} onChange={(event) => setCustomDays(event.target.value)} placeholder="روز دلخواه" /><button type="button" disabled={Boolean(busy) || Number(customDays) <= 0} onClick={() => void extend(Number(customDays))}>اعمال</button></div></div>
                <div className="v2-action-block"><label>دسترسی VIP</label><button type="button" className={`v2-toggle-row${service.vipAccess ? " is-on" : ""}`} disabled={Boolean(busy)} onClick={() => void onMutate({ action: "set_vip", serviceId: service.id, enabled: !service.vipAccess }, `VIP ${service.vipAccess ? "غیرفعال" : "فعال"} شد.`)}><span><strong>{service.vipAccess ? "VIP فعال" : "Standard"}</strong><small>فقط مجوز سرورهای VIP تغییر می‌کند.</small></span><i /></button></div>
                <div className="v2-danger-zone"><div><strong>{service.status === "ACTIVE" ? "توقف اشتراک" : "فعال‌سازی اشتراک"}</strong><small>وضعیت Account مستقل باقی می‌ماند.</small></div><button type="button" disabled={Boolean(busy)} className={service.status === "ACTIVE" ? "is-danger" : "is-success"} onClick={() => { const next = service.status === "ACTIVE" ? "SUSPENDED" : "ACTIVE"; if (next === "SUSPENDED" && !window.confirm("اشتراک این کاربر متوقف شود؟ اتصال سرویس قطع می‌شود اما Account حذف یا معلق نمی‌شود.")) return; void onMutate({ action: "set_service_status", serviceId: service.id, status: next }, next === "ACTIVE" ? "اشتراک فعال شد." : "اشتراک متوقف شد."); }}>{service.status === "ACTIVE" ? "توقف اشتراک" : "فعال‌سازی"}</button></div>
              </> : <EmptyDrawer text="این کاربر اشتراک قابل مدیریت ندارد." />}
            </section>
          ) : null}
          {tab === "devices" ? (
            <section className="v2-drawer-section">
              <SectionHead title="دستگاه‌ها" note="تغییر سقف دستگاه Sessionهای سالم را لغو نمی‌کند. لغو دستگاه فقط با دستور جداگانه انجام می‌شود." />
              {service ? <div className="v2-device-limit"><label>حداکثر دستگاه</label><div><input type="number" min="1" max="1000" value={deviceLimit} onChange={(event) => setDeviceLimit(event.target.value)} /><button type="button" disabled={Boolean(busy) || Number(deviceLimit) < 1} onClick={() => void onMutate({ action: "set_device_limit", serviceId: service.id, maxDevices: Number(deviceLimit) }, "محدودیت دستگاه ذخیره شد؛ Sessionهای سالم حفظ شدند.")}>ذخیره</button></div></div> : null}
              <div className="v2-device-list">{user.devices.length ? user.devices.map((device) => <div className="v2-device-row" key={device.id}><span className={`v2-status-dot is-${device.status === "ACTIVE" ? "success" : "neutral"}`} /><span><strong>{device.name}</strong><small dir="ltr">{device.platform}{device.appVersion ? ` · ${device.appVersion}` : ""} · ${formatDateTime(device.lastSeenAt)}</small></span>{device.status === "ACTIVE" ? <button type="button" disabled={Boolean(busy)} onClick={() => { if (window.confirm(`دسترسی دستگاه «${device.name}» لغو شود؟`)) void onMutate({ action: "revoke_device", userId: user.id, deviceId: device.id }, "دستگاه انتخاب‌شده لغو شد."); }}>لغو</button> : <StatusBadge tone="neutral">لغوشده</StatusBadge>}</div>) : <EmptyDrawer text="دستگاهی ثبت نشده است." />}</div>
              {user.activeDevices > 0 ? <div className="v2-danger-zone"><div><strong>لغو همه دستگاه‌ها</strong><small>تمام Refresh Tokenهای فعال این حساب باطل می‌شوند.</small></div><button type="button" className="is-danger" disabled={Boolean(busy)} onClick={() => { if (window.confirm("همه دستگاه‌های فعال این کاربر لغو شوند؟ کاربر باید دوباره وارد شود.")) void onMutate({ action: "revoke_all_devices", userId: user.id }, "همه دستگاه‌های فعال لغو شدند."); }}>لغو همه</button></div> : null}
            </section>
          ) : null}
          {tab === "traffic" ? <TrafficTab service={service} /> : null}
          {tab === "credentials" ? (
            <section className="v2-drawer-section">
              <SectionHead title="ورود و مجوز" note="رمز جدید فقط یک‌بار پس از بازنشانی نمایش داده می‌شود." />
              <div className="v2-credential-row"><span><small>ایمیل</small><code dir="ltr">{user.email}</code></span><button type="button" onClick={() => void copyText(user.email).then((done) => onToast({ text: done ? "ایمیل کپی شد." : "کپی ایمیل ممکن نبود.", tone: done ? "success" : "danger" }))}>کپی</button></div>
              {service ? <div className="v2-credential-row"><span><small>مجوز</small><code dir="ltr">{service.license}</code></span><button type="button" onClick={() => void copyText(service.license).then((done) => onToast({ text: done ? "مجوز کپی شد." : "کپی مجوز ممکن نبود.", tone: done ? "success" : "danger" }))}>کپی</button></div> : null}
              <button type="button" className="v2-primary-wide" disabled={Boolean(busy)} onClick={() => { if (window.confirm("رمز ورود جدید ساخته شود؟ نشست‌های ورود فعلی باطل می‌شوند و رمز جدید فقط همین بار نمایش داده می‌شود.")) void onMutate({ action: "reset_credentials", userId: user.id }, "رمز جدید ساخته شد.", true); }}>بازنشانی رمز ورود</button>
              {credentialReceipt ? <div className="v2-one-time-secret"><StatusBadge tone="warning">فقط همین بار</StatusBadge><span><small>ایمیل</small><code dir="ltr">{credentialReceipt.email}</code></span><span><small>رمز جدید</small><code dir="ltr">{credentialReceipt.initialPassword}</code></span><button type="button" onClick={() => void copyText(`ایمیل: ${credentialReceipt.email}\nرمز: ${credentialReceipt.initialPassword}${service ? `\nمجوز: ${service.license}` : ""}`).then((done) => onToast({ text: done ? "اطلاعات ورود کپی شد." : "کپی ممکن نبود.", tone: done ? "success" : "danger" }))}>کپی همه اطلاعات</button></div> : null}
            </section>
          ) : null}
          {tab === "activity" ? <ActivityTab items={user.activity} /> : null}
          {tab === "advanced" ? <AdvancedTab user={user} providerName={providerName} /> : null}
        </div>
      </aside>
    </div>
  );
}

function SectionHead({ title, note }: { title: string; note?: string }) {
  return <div className="v2-drawer-section-head"><h3>{title}</h3>{note ? <p>{note}</p> : null}</div>;
}

function OverviewTab({
  user, busy, onMutate,
}: {
  user: UserItem;
  busy: string;
  onMutate: (payload: Record<string, unknown>, success: string, receipt?: boolean) => Promise<boolean>;
}) {
  const service = user.primaryService;
  const nextAccountStatus = user.accountStatus === "ACTIVE" ? "SUSPENDED" : "ACTIVE";
  const suspending = nextAccountStatus === "SUSPENDED";

  return (
    <section className="v2-drawer-section">
      <SectionHead title="نمای کلی" />
      <div className="v2-overview-grid">
        <Fact label="وضعیت Account" value={user.accountStatus === "ACTIVE" ? "فعال" : "معلق"} />
        <Fact label="وضعیت اشتراک" value={service?.status ?? "—"} />
        <Fact label="دسترسی" value={service?.vipAccess ? "VIP" : "Standard"} />
        <Fact label="دستگاه فعال" value={`${user.activeDevices} / ${service?.maxDevices ?? "—"}`} />
        <Fact label="مانده حجم" value={service ? formatBytes(service.remainingBytes) : "—"} />
        <Fact label="انقضا" value={service ? formatDate(service.expiresAt) : "—"} />
        <Fact label="گروه سرورها" value={service?.serverGroup ?? "—"} />
        <Fact label="تعداد سرور" value={service ? formatNumber(service.serverCount) : "—"} />
      </div>

      <div className="v2-danger-zone">
        <div>
          <strong>{suspending ? "تعلیق حساب کاربر" : "فعال‌سازی حساب کاربر"}</strong>
          <small>{suspending ? "ورود و Sessionهای فعال بسته می‌شوند؛ وضعیت اشتراک‌ها تغییر نمی‌کند." : "فقط Account فعال می‌شود؛ اشتراک‌های متوقف یا منقضی خودکار تغییر نمی‌کنند."}</small>
        </div>
        <button
          type="button"
          disabled={Boolean(busy)}
          className={suspending ? "is-danger" : "is-success"}
          onClick={() => {
            if (suspending && !window.confirm("حساب این کاربر معلق شود؟ همه Sessionها و دستگاه‌های فعال لغو می‌شوند، اما وضعیت Serviceها دست‌نخورده می‌ماند.")) return;
            void onMutate(
              { action: "set_account_status", userId: user.id, status: nextAccountStatus },
              suspending ? "حساب کاربر معلق شد؛ Serviceها تغییر نکردند." : "حساب کاربر فعال شد.",
            );
          }}
        >
          {suspending ? "تعلیق حساب" : "فعال‌سازی حساب"}
        </button>
      </div>

      {user.services.length > 1 ? (
        <div className="v2-service-list">
          <SectionHead title="اشتراک‌های این حساب" />
          {user.services.map((item) => <div key={item.id}><span className={`v2-status-dot is-${item.status === "ACTIVE" ? "success" : "danger"}`} /><span><strong>{item.name}</strong><small>{formatBytes(item.remainingBytes)} مانده · {formatDate(item.expiresAt)}</small></span>{item.vipAccess ? <StatusBadge tone="vip">VIP</StatusBadge> : null}</div>)}
        </div>
      ) : null}
    </section>
  );
}

function UsageBlock({ service }: { service: ServiceItem }) {
  const percent = usagePercent(service);
  return <div className="v2-drawer-usage"><div><span><small>مصرف</small><strong>{formatBytes(service.usedBytes)}</strong></span><span><small>حجم کل</small><strong>{formatBytes(service.quotaBytes)}</strong></span><span><small>مانده</small><strong>{formatBytes(service.remainingBytes)}</strong></span></div><div className="v2-drawer-progress"><i style={{ width: `${percent}%` }} /></div><small>{formatNumber(percent)}٪ مصرف شده</small></div>;
}

function TrafficTab({ service }: { service: ServiceItem | null }) {
  return <section className="v2-drawer-section"><SectionHead title="ترافیک" note="این تب فقط داده واقعی accounting را نمایش می‌دهد؛ داده تاریخی ساختگی تولید نمی‌شود." />{service ? <><UsageBlock service={service} /><div className="v2-data-note compact"><AdminIcon name="activity" size={16} /><div><strong>تفکیک تاریخی آماده نیست</strong><p>تا زمانی که UsageSample معتبر برای این سرویس ثبت نشود، نمودار روز/هفته/ماه نمایش داده نمی‌شود.</p></div></div></> : <EmptyDrawer text="اشتراکی برای نمایش ترافیک وجود ندارد." />}</section>;
}

function ActivityTab({ items }: { items: ActivityItem[] }) {
  return <section className="v2-drawer-section"><SectionHead title="فعالیت کاربر" />{items.length ? <div className="v2-user-activity">{items.map((item) => <div key={item.id}><span className="v2-activity-icon"><AdminIcon name="activity" size={14} /></span><span><strong>{activityLabels[item.action] ?? item.action}</strong><small>{item.actor} · {formatDateTime(item.createdAt)}</small></span><code dir="ltr">{item.entityType}</code></div>)}</div> : <EmptyDrawer text="فعالیتی برای این کاربر ثبت نشده است." />}</section>;
}

function AdvancedTab({ user, providerName }: { user: UserItem; providerName: string | null }) {
  const service = user.primaryService;
  return <section className="v2-drawer-section"><SectionHead title="پیشرفته" note="اطلاعات فنی Provider و شناسه‌ها برای عیب‌یابی؛ در جریان روزمره لازم نیست." /><div className="v2-advanced-list"><Fact label="User ID" value={user.id} technical /><Fact label="Service ID" value={service?.id ?? "—"} technical /><Fact label="Provider فعال" value={providerName ?? "—"} /><Fact label="Provider اشتراک" value={service?.providerName ?? "—"} /><Fact label="Provider State" value={service ? providerLabel(service.providerState) : "—"} /><Fact label="Remote Username" value={service?.remoteUsername ?? "—"} technical /><Fact label="آخرین Sync" value={formatDateTime(service?.lastSyncAt ?? null)} /><Fact label="Group Key" value={service?.serverGroupKey ?? "—"} technical /></div>{service?.providerError ? <div className="v2-technical-error"><strong>آخرین خطای Provider</strong><code dir="ltr">{service.providerError}</code></div> : null}</section>;
}

function Fact({ label, value, technical = false }: { label: string; value: string; technical?: boolean }) {
  return <div className="v2-fact"><small>{label}</small>{technical ? <code dir="ltr">{value}</code> : <strong>{value}</strong>}</div>;
}

function EmptyDrawer({ text }: { text: string }) {
  return <div className="v2-drawer-empty">{text}</div>;
}
