export type AdminAccountStatus = "ACTIVE" | "SUSPENDED";

export type AdminAccountTransition = {
  changed: boolean;
  revokeSessions: boolean;
  serviceStatusesChanged: false;
};

export function adminAccountTransition(
  current: AdminAccountStatus,
  next: AdminAccountStatus,
): AdminAccountTransition {
  const changed = current !== next;
  return {
    changed,
    revokeSessions: changed && next === "SUSPENDED",
    serviceStatusesChanged: false,
  };
}
