type LoginCodeMessage = {
  to: string;
  code: string;
};

export async function sendLoginCode({ to, code }: LoginCodeMessage): Promise<boolean> {
  const apiKey = process.env.RESEND_API_KEY;
  const from = process.env.AUTH_FROM_EMAIL;
  if (!apiKey || !from) {
    if (process.env.NODE_ENV === "production") {
      throw new Error("RESEND_API_KEY and AUTH_FROM_EMAIL are required in production");
    }
    return false;
  }

  const response = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      authorization: `Bearer ${apiKey}`,
      "content-type": "application/json",
    },
    body: JSON.stringify({
      from,
      to: [to],
      subject: "کد ورود QuickPing",
      text: `کد ورود شما ${code} است. این کد تا ۱۰ دقیقه اعتبار دارد.`,
      html: `<div dir="rtl" style="font-family:Arial,sans-serif"><h2>ورود به QuickPing</h2><p>کد ورود شما:</p><p style="font-size:32px;letter-spacing:8px;font-weight:700">${code}</p><p>این کد تا ۱۰ دقیقه اعتبار دارد.</p></div>`,
    }),
  });
  if (!response.ok) {
    throw new Error(`Email provider rejected the request (${response.status})`);
  }
  return true;
}
