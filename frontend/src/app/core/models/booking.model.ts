export interface BookingDto {
  id: number;
  eventId: number;
  eventTitle: string;
  organizerId: number;
  organizerUsername: string;
  ticketTypeId: number;
  ticketTypeName: string;
  unitPrice: number;
  numberOfTickets: number;
  totalCost: number;
  status: string;  // 'CONFIRMED' | 'CANCELLED'
  bookedAt: string; // ISO-8601 LocalDateTime
}
