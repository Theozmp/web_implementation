package gr.uoa.di.ted.events.repository;

import gr.uoa.di.ted.events.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {

    // inbox: messages the recipient hasn't soft-deleted, paged newest-first.
    // explicit countQuery so we don't depend on Spring's derived count query.
    @Query(value = "SELECT m FROM Message m WHERE m.recipient.id = :userId AND m.deletedByRecipient = false",
           countQuery = "SELECT COUNT(m) FROM Message m WHERE m.recipient.id = :userId AND m.deletedByRecipient = false")
    Page<Message> findInboxForUser(@Param("userId") Long userId, Pageable pageable);

    // sent box: messages the sender hasn't soft-deleted.
    @Query(value = "SELECT m FROM Message m WHERE m.sender.id = :userId AND m.deletedBySender = false",
           countQuery = "SELECT COUNT(m) FROM Message m WHERE m.sender.id = :userId AND m.deletedBySender = false")
    Page<Message> findSentByUser(@Param("userId") Long userId, Pageable pageable);

    // badge counter: unread messages not deleted by the recipient.
    @Query("SELECT COUNT(m) FROM Message m WHERE m.recipient.id = :userId AND m.readAt IS NULL AND m.deletedByRecipient = false")
    long countUnreadForUser(@Param("userId") Long userId);
}
