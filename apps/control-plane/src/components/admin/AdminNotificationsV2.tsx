"use client";

import { FormEvent, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { AdminIcon } from "./AdminIcon";

type Audience = "ALL" | "FREE" | "PAID" | "SELECTED";
type Category = "ACCOUNT" | "SERVICE" | "SYSTEM" | "SUPPORT";

type UserOption = { id: string; email: string };
type NotificationItem = {
  id: string;
  title: string;
  body: string;
  category: string;
  audience: string;
  createdAt: string;
  publishedAt: string | null;
  recipientCount: number;
  readCount: number;
  recipients: string[];
};

const categoryLabels: Record<string, string> = {
  ACCOUNT: "حساب",
  SERVICE: "اشتراک",
  SYSTEM: "سیستم",
  SUPPORT: "پشتیبانی",
};

const audienceLabels: Record<string, string> = {
  ALL: "همه کاربران",
  FREE: "کاربران رایگان",
  PAID: "کاربران پولی",
  SELECTED: "انتخابی",
};

function formatNumber(value: number): string {
  return new Intl.NumberFormat("fa-IR").format(value);
}

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat("fa-IR", {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

function readPercent(item: NotificationItem): number {
  if (!item.recipientCount) return 0;
  return Math.round(item.readCount / item.recipientCount * 100);
}

export function AdminNotificationsV2({ users, notifications }: { users: UserOption[]; notifications: NotificationItem[] }) {
  const router = useRouter();
  const [audience, setAudience] = useState<Audience>("ALL");
  const [category, setCategory] = useState<Category>("SYSTEM");
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [userQuery, setUserQuery] = useState("");
  const [historyQuery, setHistoryQuery] = useState("");
  const [historyCategory, setHistoryCategory] = useState("ALL");
  const [historyAudience, setHistoryAudience] = useState("ALL");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const visibleUsers = useMemo(() => {
    const query = userQuery.trim().toLowerCase();
    if (!query) return users;
    return users.filter((user) => user.email.toLowerCase().includes(query));
  }, [users, userQuery]);

  const filteredHistory = useMemo(() => {
    const query = historyQuery.trim().toLowerCase();
    return notifications.filter((item) => {
      if (historyCategory !== "ALL" && item.category !== historyCategory) return false;
      if (historyAudience !== "ALL" && item.audience !== historyAudience) return false;
      if (!query) return true;
      return `${item.title} ${item.body} ${item.recipients.join(" ")}`.toLowerCase().includes(query);
    });
  }, [notifications, historyQuery, historyCategory, historyAudience]);

  const summary = useMemo(() => {
    const recipients = notifications.reduce((sum, item) => sum + item.recipientCount, 0);
    const reads = notifications.reduce((sum, item) => sum + item.readCount, 0);
    return {
      recipients,
      reads,
      unread: Math.max(0, recipients - reads),
    };
  }, [notifications]);

  function toggleUser(id: string) {
    setSelectedIds((current) => current.includes(id) ? current.filter((item) => item !== id) : [...current, id]);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (audience === "SELECTED" && selectedIds.length === 0) {
      setError("برای ارسال انتخابی حداقل یک کاربر را انتخاب کنید");
      return;
    }
    if (audience !== "SELECTED") {
      const label = audienceLabels[audience];
      if (!window.confirm(`اعلان برای «${label}» ارسال شود؟ گیرندگان در همین لحظه Snapshot می‌شوند.`)) return;
    }
    const form = event.currentTarget;
    const data = new FormData(form);
    setBusy(true);
    setError("");
    setSuccess("");
    try {
      const actionUrl = String(data.get("actionUrl") ?? "").trim();
      const response = await fetch("/api/v1/admin/notifications", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          title: String(data.get("title") ?? ""),
          body: String(data.get("body") ?? ""),
          category,
          audience,
          publishNow: true,
          selectedUserIds: audience === "SELECTED" ? selectedIds : [],
          ...(actionUrl ? { actionUrl } : {}),
        }),
      });
      const body = await response.json().catch(() => null) as { data?: { recipientCount?: number }; error?: { message?: string } } | null;
      if (!response.ok) {
        setError(body?.error?.message ?? "ارسال اعلان انجام نشد");
        return;
      }
      const count = body?.data?.recipientCount ?? 0;
      setSuccess(`اعلان ثبت شد و برای ${formatNumber(count)} گیرنده Snapshot شد.`);
      form.reset();
      setAudience("ALL");
      setCategory("SYSTEM");
      setSelectedIds([]);
      setUserQuery("");
      router.refresh();
    } catch {
      setError("ارتباط با Control Plane هنگام ارسال اعلان قطع شد");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="v2-notifications-page">
      <div className="v2-notification-summary">
        <div><small>اعلان‌های اخیر</small><strong>{formatNumber(notifications.length)}</strong></div>
        <div><small>گیرنده Snapshot</small><strong>{formatNumber(summary.recipients)}</strong></div>
        <div><small>خوانده‌شده</small><strong>{formatNumber(summary.reads)}</strong></div>
        <div><small>خوانده‌نشده</small><strong>{formatNumber(summary.unread)}</strong></div>
        <div><small>کاربر قابل انتخاب</small><strong>{formatNumber(users.length)}</strong></div>
      </div>

      <section className="v2-notification-composer">
        <div className="v2-notification-section-head"><div><span className="v2-eyebrow">COMPOSER</span><h2>ارسال اعلان</h2><p>گیرندگان هنگام ارسال Snapshot می‌شوند؛ کاربران آینده اعلان قدیمی را دریافت نمی‌کنند.</p></div></div>
        <form onSubmit={submit}>
          <div className="v2-notification-compose-grid">
            <div className="v2-notification-message-fields">
              <div className="field"><label>عنوان</label><input className="input" name="title" maxLength={160} required placeholder="عنوان کوتاه و واضح" /></div>
              <div className="field"><label>متن اعلان</label><textarea className="textarea" name="body" rows={6} maxLength={4000} required placeholder="پیام قابل فهم برای کاربر…" /></div>
              <details className="v2-notification-advanced"><summary>گزینه‌های تکمیلی</summary><div className="field"><label>Action URL اختیاری</label><input className="input" name="actionUrl" type="url" dir="ltr" placeholder="https://..." /></div></details>
            </div>
            <div className="v2-notification-targeting">
              <div className="field"><label>دسته اعلان</label><div className="v2-segmented-control">{(["SYSTEM", "ACCOUNT", "SERVICE", "SUPPORT"] as Category[]).map((item) => <button type="button" className={category === item ? "is-active" : ""} onClick={() => setCategory(item)} key={item}>{categoryLabels[item]}</button>)}</div></div>
              <div className="field"><label>مخاطب</label><div className="v2-audience-grid">{(["ALL", "PAID", "FREE", "SELECTED"] as Audience[]).map((item) => <button type="button" className={audience === item ? "is-active" : ""} onClick={() => setAudience(item)} key={item}><strong>{audienceLabels[item]}</strong><small>{item === "ALL" ? "تمام مشتریان فعلی" : item === "PAID" ? "اشتراک پولی فعال" : item === "FREE" ? "سرویس رایگان فعال" : "یک یا چند کاربر مشخص"}</small></button>)}</div></div>
              {audience === "SELECTED" ? <div className="v2-recipient-picker"><div className="v2-recipient-search"><AdminIcon name="search" size={15} /><input value={userQuery} onChange={(event) => setUserQuery(event.target.value)} placeholder="جست‌وجوی ایمیل…" /></div><div className="v2-recipient-meta"><span>{formatNumber(selectedIds.length)} انتخاب</span>{selectedIds.length ? <button type="button" onClick={() => setSelectedIds([])}>پاک کردن</button> : null}</div><div className="v2-recipient-list">{visibleUsers.slice(0, 100).map((user) => <label className={selectedIds.includes(user.id) ? "is-selected" : ""} key={user.id}><input type="checkbox" checked={selectedIds.includes(user.id)} onChange={() => toggleUser(user.id)} /><span dir="ltr">{user.email}</span></label>)}</div>{visibleUsers.length > 100 ? <small className="v2-recipient-limit">۱۰۰ نتیجه اول نمایش داده شده؛ جست‌وجو را دقیق‌تر کن.</small> : null}</div> : null}
            </div>
          </div>
          {error ? <div className="v2-notification-feedback is-danger"><span className="v2-status-dot is-danger" />{error}</div> : null}
          {success ? <div className="v2-notification-feedback is-success"><span className="v2-status-dot is-success" />{success}</div> : null}
          <div className="v2-notification-submit"><span>ارسال، NotificationDelivery را برای گیرندگان فعلی ثبت می‌کند.</span><button className="button" disabled={busy}>{busy ? "در حال ارسال…" : "ارسال اعلان"}</button></div>
        </form>
      </section>

      <section className="v2-notification-history">
        <div className="v2-notification-section-head"><div><span className="v2-eyebrow">HISTORY</span><h2>تاریخچه ارسال</h2></div></div>
        <div className="v2-notification-history-toolbar"><div className="v2-server-search"><AdminIcon name="search" size={16} /><input value={historyQuery} onChange={(event) => setHistoryQuery(event.target.value)} placeholder="عنوان، متن یا گیرنده…" /></div><select value={historyCategory} onChange={(event) => setHistoryCategory(event.target.value)}><option value="ALL">همه دسته‌ها</option>{Object.entries(categoryLabels).map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select><select value={historyAudience} onChange={(event) => setHistoryAudience(event.target.value)}><option value="ALL">همه مخاطب‌ها</option>{Object.entries(audienceLabels).map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select><span>{formatNumber(filteredHistory.length)} مورد</span></div>
        <div className="v2-notification-table-wrap"><table className="v2-notification-table"><thead><tr><th>اعلان</th><th>دسته</th><th>مخاطب</th><th>گیرنده</th><th>Read Rate</th><th>زمان</th></tr></thead><tbody>{filteredHistory.map((item) => { const percent = readPercent(item); return <tr key={item.id}><td><div className="v2-notification-copy"><strong>{item.title}</strong><small>{item.body}</small></div></td><td><span className="v2-notification-badge">{categoryLabels[item.category] ?? item.category}</span></td><td>{audienceLabels[item.audience] ?? item.audience}</td><td><span className="v2-notification-recipients"><strong>{formatNumber(item.recipientCount)}</strong>{item.recipients.length ? <small dir="ltr">{item.recipients.slice(0, 2).join(" · ")}{item.recipientCount > 2 ? ` +${item.recipientCount - 2}` : ""}</small> : null}</span></td><td><div className="v2-notification-read"><span><strong>{formatNumber(item.readCount)}</strong><small> / {formatNumber(item.recipientCount)}</small></span><div><i style={{ width: `${percent}%` }} /></div><small>{formatNumber(percent)}٪</small></div></td><td><span className="v2-server-last-seen">{formatDateTime(item.publishedAt ?? item.createdAt)}</span></td></tr>; })}</tbody></table>{!filteredHistory.length ? <div className="v2-server-empty"><AdminIcon name="bell" size={22} /><strong>اعلانی پیدا نشد</strong><span>فیلترها یا جست‌وجو را تغییر بده.</span></div> : null}</div>
        <div className="v2-notification-mobile-list">{filteredHistory.map((item) => <article key={item.id}><div><span className="v2-notification-badge">{categoryLabels[item.category] ?? item.category}</span><small>{formatDateTime(item.publishedAt ?? item.createdAt)}</small></div><strong>{item.title}</strong><p>{item.body}</p><footer><span>{audienceLabels[item.audience] ?? item.audience}</span><span>{formatNumber(item.readCount)} / {formatNumber(item.recipientCount)} خوانده</span></footer></article>)}</div>
      </section>
    </div>
  );
}
