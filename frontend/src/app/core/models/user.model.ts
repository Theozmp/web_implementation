/** Profile data carried inside the JWT and returned by /auth/login */
export interface UserSummary {
  id: number;
  username: string;
  firstName: string;
  lastName: string;
  email: string;
  /** 'ADMIN' | 'ORGANIZER' | 'PARTICIPANT' */
  role: string;
  mustChangePassword: boolean;
}

export interface AuthResponse {
  accessToken: string;
  /** ISO-8601 string, e.g. "2026-05-18T13:00:00Z" */
  expiresAt: string;
  user: UserSummary;
}
