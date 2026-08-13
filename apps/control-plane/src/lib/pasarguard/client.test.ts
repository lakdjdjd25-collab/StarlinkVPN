import { describe, expect, it, vi } from "vitest";
import { normalizePasarGuardBaseUrl, PasarGuardClient } from "./client";

describe("PasarGuard API client", () => {
  it("normalizes a dashboard URL to the API base without weakening HTTPS", () => {
    const url = normalizePasarGuardBaseUrl("https://panel.example.com:2096/dashboard/#/login");
    expect(url.toString()).toBe("https://panel.example.com:2096/");
    expect(() => normalizePasarGuardBaseUrl("http://panel.example.com/dashboard")).toThrow(/HTTPS/);
  });

  it("authenticates with the official OAuth form and returns sanitized users", async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ access_token: "test-token", token_type: "bearer" }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        total: 1,
        users: [{
          id: 17,
          username: "vpn-user",
          status: "active",
          used_traffic: 1024,
          lifetime_used_traffic: 2048,
          data_limit: 4096,
          expire: "2026-09-01T00:00:00Z",
          hwid_limit: 2,
        }],
      }), { status: 200 }));
    const client = new PasarGuardClient({
      baseUrl: normalizePasarGuardBaseUrl("https://panel.example.com/dashboard"),
      username: "admin",
      password: "test-password",
      fetch: fetcher as unknown as typeof fetch,
    });

    const users = await client.listUsers();

    expect(users).toEqual([expect.objectContaining({
      id: 17,
      username: "vpn-user",
      status: "active",
      usedTraffic: 1024n,
      dataLimit: 4096n,
      maxDevices: 2,
    })]);
    const [loginUrl, loginInit] = fetcher.mock.calls[0] as [URL, RequestInit];
    expect(loginUrl.pathname).toBe("/api/admin/token");
    expect(loginInit.method).toBe("POST");
    expect(String(loginInit.body)).toContain("grant_type=password");
    const [usersUrl, usersInit] = fetcher.mock.calls[1] as [URL, RequestInit];
    expect(usersUrl.pathname).toBe("/api/users");
    expect(usersInit.headers).toMatchObject({ authorization: "Bearer test-token" });
  });

  it("reads user templates with quota and one-time reset metadata", async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ access_token: "template-token" }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify([{
        id: 9,
        name: "Google Free 10GB",
        data_limit: 10737418240,
        expire_duration: 0,
        group_ids: [1, 2],
        status: "active",
        is_disabled: false,
        data_limit_reset_strategy: "no_reset",
      }]), { status: 200 }));
    const client = new PasarGuardClient({
      baseUrl: normalizePasarGuardBaseUrl("https://panel.example.com/dashboard"),
      username: "admin",
      password: "test-password",
      fetch: fetcher as unknown as typeof fetch,
    });

    const templates = await client.listUserTemplates();

    expect(templates).toEqual([{
      id: 9,
      name: "Google Free 10GB",
      dataLimit: 10737418240n,
      expireDurationSeconds: 0,
      groupIds: [1, 2],
      status: "active",
      isDisabled: false,
      resetStrategy: "no_reset",
    }]);
    const [templatesUrl] = fetcher.mock.calls[1] as [URL, RequestInit];
    expect(templatesUrl.pathname).toBe("/api/user_templates");
  });
});
