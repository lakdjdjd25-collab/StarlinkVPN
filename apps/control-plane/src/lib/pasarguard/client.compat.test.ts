import { describe, expect, it, vi } from "vitest";
import { normalizePasarGuardBaseUrl, PasarGuardClient } from "./client";

function clientWith(fetcher: ReturnType<typeof vi.fn>) {
  return new PasarGuardClient({
    baseUrl: normalizePasarGuardBaseUrl("https://panel.example.com/dashboard/#/users"),
    username: "admin",
    password: "test-password",
    fetch: fetcher as unknown as typeof fetch,
  });
}

describe("PasarGuard API compatibility", () => {
  it("accepts users with zero HWID limit, null groups, null status and string totals", async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ access_token: "token" }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        total: "1",
        users: [{
          id: "12",
          username: "legacy-user",
          status: null,
          used_traffic: "1024",
          data_limit: "2048",
          expire: null,
          hwid_limit: 0,
          group_ids: null,
        }],
      }), { status: 200 }));

    const users = await clientWith(fetcher).listUsers();

    expect(users).toEqual([{
      id: 12,
      username: "legacy-user",
      status: "active",
      usedTraffic: 1024n,
      dataLimit: 2048n,
      expiresAt: null,
      maxDevices: null,
      groupIds: [],
    }]);
  });

  it("accepts direct-array group responses used by newer simple endpoints", async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ access_token: "token" }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify([
        { id: 1, name: "Premium" },
        { id: "2", name: null },
      ]), { status: 200 }));

    const groups = await clientWith(fetcher).listGroups();

    expect(groups).toEqual([
      { id: 1, name: "Premium" },
      { id: 2, name: "Group 2" },
    ]);
  });

  it("falls back to the permission-safe simple user endpoint when the full list shape differs", async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ access_token: "token" }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ unexpected: true }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        total: 2,
        users: [
          { id: 7, username: "user-seven" },
          { id: 8, username: "user-eight" },
        ],
      }), { status: 200 }));

    const users = await clientWith(fetcher).listUsers();

    expect(users.map((user) => user.username)).toEqual(["user-seven", "user-eight"]);
    expect(users.every((user) => user.status === "active")).toBe(true);
    const [simpleUrl] = fetcher.mock.calls[2] as [URL, RequestInit];
    expect(simpleUrl.pathname).toBe("/api/users/simple");
  });

  it("accepts nested data envelopes from compatible PasarGuard deployments", async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ access_token: "token" }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        data: {
          total: 1,
          users: [{
            id: 21,
            username: "nested-user",
            status: "active",
            used_traffic: 0,
            data_limit: 0,
            expire: 0,
            hwid_limit: null,
            group_ids: [3],
          }],
        },
      }), { status: 200 }));

    const users = await clientWith(fetcher).listUsers();

    expect(users).toHaveLength(1);
    expect(users[0]).toMatchObject({ id: 21, username: "nested-user", groupIds: [3] });
  });
});
