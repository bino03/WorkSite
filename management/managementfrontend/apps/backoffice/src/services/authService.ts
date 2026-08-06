// src/services/authService.ts
import api from "@/api";

export interface UserInfo {
  role: "ADMIN" | "EMPLOYEE";
  accountStatus: string;
  authUserId: string;
  profileId: string;
  name: string;
  photoUrl: string | null;
}

export interface LoginResponse {
  user: {
    id: string;
    email: string;
    name: string;
    role: string;
    photoUrl: string | null;
    profileId: string | null;
  };
}

const USER_KEY = "session_user";

export function saveUser(user: UserInfo): void {
  sessionStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function loadUser(): UserInfo | null {
  try {
    const raw = sessionStorage.getItem(USER_KEY);
    return raw ? (JSON.parse(raw) as UserInfo) : null;
  } catch {
    return null;
  }
}

export function clearUser(): void {
  sessionStorage.removeItem(USER_KEY);
}

/**
 * Login with email and password.
 * Backend returns user data and sets HttpOnly cookies automatically.
 */
export async function login(email: string, password: string): Promise<UserInfo> {
  const response = await api.post<LoginResponse>(
    "/auth/login",
    { email, password },
    { noRefreshRetry: true }
  );

  const raw = response.data.user;
  const userInfo: UserInfo = {
    authUserId: raw.id,
    name: raw.name,
    role: raw.role as UserInfo["role"],
    accountStatus: "unlocked",
    profileId: raw.profileId ?? "",
    photoUrl: raw.photoUrl ?? null,
  };

  saveUser(userInfo);
  return userInfo;
}

interface MeResponse {
  user: {
    id: string;
    email: string;
    name: string;
    role: string;
    photoUrl: string | null;
    profileId: string | null;
  };
}

/**
 * Fetch fresh user info (including signed photoUrl) from /auth/me.
 * Call this after token refresh to avoid stale signed URLs.
 */
export async function fetchMe(): Promise<UserInfo> {
  const { data } = await api.get<MeResponse>('/auth/me');
  const raw = data.user;
  const userInfo: UserInfo = {
    authUserId: raw.id,
    name: raw.name,
    role: raw.role as UserInfo['role'],
    accountStatus: 'unlocked',
    profileId: raw.profileId ?? '',
    photoUrl: raw.photoUrl ?? null,
  };
  saveUser(userInfo);
  return userInfo;
}

/**
 * Logout: clear session and remove cookies.
 */
export async function logout(): Promise<void> {
  clearUser();
  try {
    await api.post("/auth/logout");
  } catch {
    // Even if logout fails, continue
  }
}
