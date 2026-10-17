import { PageResponse } from './admin-user.model';

export type { PageResponse };

export interface Category {
  id: number;
  name: string;
}

export interface TicketTypeItem {
  id?: number;
  name: string;
  price: number;
  quantity: number;
  sold?: number;
}

export interface EventSummary {
  id: number;
  title: string;
  status: string;         // 'DRAFT' | 'PUBLISHED' | 'CANCELLED' | 'COMPLETED'
  startDatetime: string;  // ISO-8601 LocalDateTime
  endDatetime: string;
  venueName: string | null;
  city: string | null;
  capacity: number;
  ticketsSold: number;
  createdAt: string;
}

export interface EventDetail {
  id: number;
  title: string;
  description: string | null;
  status: string;
  startDatetime: string;
  endDatetime: string;
  capacity: number;
  latitude: number | null;
  longitude: number | null;
  venueName: string | null;
  address: string | null;
  city: string | null;
  country: string | null;
  organizerId: number;
  organizerUsername: string;
  categories: Category[];
  ticketTypes: TicketTypeItem[];
  photoUrls: string[];
  ticketsSold: number;
  createdAt: string;
  updatedAt: string;
}

/** Payload sent to POST /events and PUT /events/:id */
export interface EventCreatePayload {
  title: string;
  description?: string;
  startDatetime: string;
  endDatetime: string;
  capacity: number;
  venueName?: string;
  address?: string;
  city?: string;
  country?: string;
  latitude?: number | null;
  longitude?: number | null;
  categoryIds: number[];
  ticketTypes: { id?: number; name: string; price: number; quantity: number }[];
}
