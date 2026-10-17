export interface MessageSummaryDto {
  id: number;
  senderId: number;
  senderUsername: string;
  recipientId: number;
  recipientUsername: string;
  eventId: number | null;
  eventTitle: string | null;
  subject: string;
  sentAt: string;    // ISO-8601 LocalDateTime
  read: boolean;
}

export interface MessageDto extends MessageSummaryDto {
  body: string;
  readAt: string | null;
}

export interface MessageCreateRequest {
  recipientId: number;
  eventId?: number | null;
  subject: string;
  body: string;
}

export interface UserSearchResult {
  id: number;
  username: string;
}
