import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "مدیریت QuickPing",
  description: "کنترل‌پلین خصوصی سرویس QuickPing",
  robots: { index: false, follow: false },
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="fa" dir="rtl">
      <body>{children}</body>
    </html>
  );
}
