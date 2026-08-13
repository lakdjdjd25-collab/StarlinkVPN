type LoginCodeMessage = {
  to: string;
  code: string;
};

type CodePurpose = "login" | "password_change";

async function sendCode(
  { to, code }: LoginCodeMessage,
  purpose: CodePurpose,
): Promise<boolean> {
  const apiKey = process.env.RESEND_API_KEY;
  const from = process.env.AUTH_FROM_EMAIL;
  if (!apiKey || !from) {
    if (process.env.NODE_ENV === "production") {
      throw new Error("RESEND_API_KEY and AUTH_FROM_EMAIL are required in production");
    }
    return false;
  }

  const passwordChange = purpose === "password_change";
  const subject = passwordChange ? "کد تغییر گذرواژه QuickPing" : "کد ورود QuickPing";
  const heading = passwordChange ? "تغییر گذرواژه QuickPing" : "ورود به QuickPing";
  const description = passwordChange ? "کد تأیید تغییر گذرواژه شما:" : "کد ورود شما:";
  const response = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      authorization: `Bearer ${apiKey}`,
      "content-type": "application/json",
    },
    body: JSON.stringify({
      from,
      to: [to],
      subject,
      text: `${description} ${code} است. این کد تا ۱۰ دقیقه اعتبار دارد.`,
      html: `<div dir="rtl" style="font-family:Arial,sans-serif"><h2>${heading}</h2><p>${description}</p><p style="font-size:32px;letter-spacing:8px;font-weight:700">${code}</p><p>این کد تا ۱۰ دقیقه اعتبار دارد.</p></div>`,
    }),
  });
  if (!response.ok) {
    throw new Error(`Email provider rejected the request (${response.status})`);
  }
  return true;
}

export function sendLoginCode(message: LoginCodeMessage): Promise<boolean> {
  return sendCode(message, "login");
}

export function sendPasswordChangeCode(message: LoginCodeMessage): Promise<boolean> {
  return sendCode(message, "password_change");
}
