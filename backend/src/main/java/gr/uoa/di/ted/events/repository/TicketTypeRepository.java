package gr.uoa.di.ted.events.repository;

import gr.uoa.di.ted.events.entity.TicketType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TicketTypeRepository extends JpaRepository<TicketType, Long> {

    List<TicketType> findByEventId(Long eventId);

    /**
     * Sum of all ticket quantities for an event. The event service uses it to keep
     * SUM(quantity) <= event.capacity when adding a ticket type.
     */
    @Query("SELECT COALESCE(SUM(t.quantity), 0) FROM TicketType t WHERE t.event.id = :eventId")
    int sumQuantityByEventId(@Param("eventId") Long eventId);

    /**
     * Takes a PESSIMISTIC_WRITE (SELECT ... FOR UPDATE) row lock on the ticket type
     * before decrementing its quantity.
     *
     * Pessimistic rather than @Version because under high concurrency optimistic
     * locking makes every losing thread retry: with 50 threads racing for the last
     * seat, 49 hit OptimisticLockException and retry. SELECT ... FOR UPDATE
     * serialises read-then-write at the DB, so exactly the available number succeed
     * and the rest get an instant 409 with no retry logic here. The cost is one
     * writer at a time on that row, which is fine since the lock is held only for
     * the short INSERT + UPDATE.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT tt FROM TicketType tt WHERE tt.id = :id")
    Optional<TicketType> findByIdWithLock(@Param("id") Long id);
}
