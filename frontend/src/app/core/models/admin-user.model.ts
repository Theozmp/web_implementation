export interface UserListItem {
  id: number;
  username: string;
  firstName: string;
  lastName: string;
  email: string;
  role: string;
  status: string;       // 'PENDING' | 'ACTIVE' | 'REJECTED'
  createdAt: string;    // ISO-8601 LocalDateTime from backend
}

export interface UserDetail extends UserListItem {
  mustChangePassword: boolean;
  phone: string | null;
  addressLine: string | null;
  city: string | null;
  country: string | null;
  latitude: number | null;
  longitude: number | null;
  taxId: string | null;
  updatedAt: string;
}

/** Matches Spring Data's Page<T> JSON serialisation shape. */
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;   // 0-indexed current page
}
