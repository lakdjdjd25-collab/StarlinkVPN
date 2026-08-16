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
    expect(usersUrl.searchParams.get("limit")).toBe("100");
    expect(usersUrl.searchParams.get("offset")).toBe("0");
    expect(usersInit.headers).toMatchObject({ authorization: "Bearer test-token" });
  });

  it("loads every page before returning PasarGuard users", async () => {
    const rawUser = (id: number) => ({
      id,
      username: `user-${id}`,
      status: "active",
      used_traffic: 0,
      data_limit: 1024,
      expire: null,
      hwid_limit: 1,
      group_ids: [1],
    });
    const fetcher = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ access_token: "paged-token" }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ total: 3, users: [rawUser(1), rawUser(2)] }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ total: 3, users: [rawUser(3)] }), { status: 200 }));
    const client = new PasarGuardClient({
      baseUrl: normalizePasarGuardBaseUrl("https://panel.example.com/dashboard"),
      username: "admin",
      password: "test-password",
      fetch: fetcher as unknown as typeof fetch,
    });

    const users = await client.listUsers();

    expect(users.map((user) => user.id)).toEqual([1, 2, 3]);
    const [secondPageUrl] = fetcher.mock.calls[2] as [URL, RequestInit];
    expect(secondPageUrl.searchParams.get("offset")).toBe("2");
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

  it("creates a finite service with quota, expiry, groups and HWID limit", async () => {
    const expire = new Date("2026-09-14T08:30:00.000Z");
    const remote = {
      id: 31,
      username: "nh_123",
      status: "active",
      used_traffic: 0,
      data_limit: 107374182400,
      expire: expire.toISOString(),
      hwid_limit: 3,
      group_ids: [2, 5],
    };
    const fetcher = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ access_token: "create-token" }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(remote), { status: 201 }));
    const client = new PasarGuardClient({
      baseUrl: normalizePasarGuardBaseUrl("https://panel.example.com/dashboard"),
      username: "admin",
      password: "test-password",
      fetch: fetcher as unknown as typeof fetch,
    });

    const created = await client.createUser("nh_123", 107374182400n, [5, 2, 5], "NimHUB service", 3, expire);

    expect(created).toEqual(expect.objectContaining({ id: 31, dataLimit: 107374182400n, maxDevices: 3 }));
    const [url, init] = fetcher.mock.calls[1] as [URL, RequestInit];
    expect(url.pathname).toBe("/api/user");
    expect(init.method).toBe("POST");
    expect(JSON.parse(String(init.body))).toMatchObject({
      username: "nh_123",
      status: "active",
      expire: expire.toISOString(),
      data_limit: 107374182400,
      data_limit_reset_strategy: "no_reset",
      group_ids: [2, 5],
      hwid_limit: 3,
    });
  });

  it("updates and deletes a service through the official username route", async () => {
    const expire = new Date("2026-10-01T00:00:00.000Z");
    const updatedRemote = {
      id: 44,
      username: "nh_user/unsafe",
      status: "disabled",
      used_traffic: 1024,
      data_limit: 53687091200,
      expire: expire.toISOString(),
      hwid_limit: 2,
      group_ids: [7],
    };
    const fetcher = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ access_token: "update-token" }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(updatedRemote), { status: 200 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    const client = new PasarGuardClient({
      baseUrl: normalizePasarGuardBaseUrl("https://panel.example.com/dashboard"),
      username: "admin",
      password: "test-password",
      fetch: fetcher as unknown as typeof fetch,
    });

    await client.updateUser("nh_user/unsafe", {
      dataLimit: 53687091200n,
      expiresAt: expire,
      maxDevices: 2,
      status: "disabled",
    });
    await client.deleteUser("nh_user/unsafe");

    const [updateUrl, updateInit] = fetcher.mock.calls[1] as [URL, RequestInit];
    expect(updateUrl.pathname).toBe("/api/user/nh_user%2Funsafe");
    expect(updateInit.method).toBe("PUT");
    expect(JSON.parse(String(updateInit.body))).toMatchObject({
      data_limit: 53687091200,
      expire: expire.toISOString(),
      hwid_limit: 2,
      status: "disabled",
    });
    const [deleteUrl, deleteInit] = fetcher.mock.calls[2] as [URL, RequestInit];
    expect(deleteUrl.pathname).toBe("/api/user/nh_user%2Funsafe");
    expect(deleteInit.method).toBe("DELETE");
  });
});
