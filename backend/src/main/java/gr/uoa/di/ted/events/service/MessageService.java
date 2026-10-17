package gr.uoa.di.ted.events.service;

import gr.uoa.di.ted.events.dto.message.*;
import gr.uoa.di.ted.events.entity.Event;
import gr.uoa.di.ted.events.entity.Message;
import gr.uoa.di.ted.events.entity.User;
import gr.uoa.di.ted.events.enums.UserStatus;
import gr.uoa.di.ted.events.exception.ForbiddenOperationException;
import gr.uoa.di.ted.events.exception.MessageNotFoundException;
import gr.uoa.di.ted.events.exception.UserNotFoundException;
import gr.uoa.di.ted.events.repository.EventRepository;
import gr.uoa.di.ted.events.repository.MessageRepository;
import gr.uoa.di.ted.events.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final UserRepository    userRepository;
    private final EventRepository   eventRepository;

    // send

    @Transactional
    public MessageDto send(MessageCreateRequest req, String senderUsername) {
        User sender    = requireByUsername(senderUsername);
        User recipient = userRepository.findById(req.recipientId())
                .orElseThrow(() -> new UserNotFoundException(req.recipientId()));

        Event event = null;
        if (req.eventId() != null) {
            // ignore an invalid event id; the message still goes through
            event = eventRepository.findById(req.eventId()).orElse(null);
        }

        Message m = new Message();
        m.setSender(sender);
        m.setRecipient(recipient);
        m.setEvent(event);
        m.setSubject(req.subject());
        m.setBody(req.body());
        return toDto(messageRepository.save(m));
    }

    // inbox / sent

    @Transactional(readOnly = true)
    public Page<MessageSummaryDto> getInbox(String username, Pageable pageable) {
        User user = requireByUsername(username);
        return messageRepository.findInboxForUser(user.getId(), pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public Page<MessageSummaryDto> getSent(String username, Pageable pageable) {
        User user = requireByUsername(username);
        return messageRepository.findSentByUser(user.getId(), pageable).map(this::toSummary);
    }

    // get (marks read)

    @Transactional
    public MessageDto getMessage(Long id, String username) {
        Message m    = requireMessage(id);
        User    user = requireByUsername(username);

        boolean isRecipient = m.getRecipient().getId().equals(user.getId());
        boolean isSender    = m.getSender().getId().equals(user.getId());

        if (!isRecipient && !isSender) {
            throw new ForbiddenOperationException("You do not have access to this message");
        }
        if (isRecipient && m.isDeletedByRecipient()) throw new MessageNotFoundException(id);
        if (isSender    && m.isDeletedBySender())    throw new MessageNotFoundException(id);

        if (isRecipient && m.getReadAt() == null) {
            m.setReadAt(LocalDateTime.now());
            messageRepository.save(m);
        }
        return toDto(m);
    }

    // soft delete

    @Transactional
    public void deleteMessage(Long id, String username, String box) {
        Message m    = requireMessage(id);
        User    user = requireByUsername(username);

        if ("sent".equalsIgnoreCase(box)) {
            if (!m.getSender().getId().equals(user.getId())) {
                throw new ForbiddenOperationException("You are not the sender of this message");
            }
            m.setDeletedBySender(true);
        } else {
            if (!m.getRecipient().getId().equals(user.getId())) {
                throw new ForbiddenOperationException("You are not the recipient of this message");
            }
            m.setDeletedByRecipient(true);
        }
        messageRepository.save(m);
    }

    // unread count

    @Transactional(readOnly = true)
    public long getUnreadCount(String username) {
        User user = requireByUsername(username);
        return messageRepository.countUnreadForUser(user.getId());
    }

    // recipient autocomplete

    @Transactional(readOnly = true)
    public List<UserSearchDto> searchUsers(String fragment, String callerUsername) {
        return userRepository
                .findTop10ByUsernameContainingIgnoreCaseAndStatusOrderByUsernameAsc(
                        fragment, UserStatus.ACTIVE)
                .stream()
                .filter(u -> !u.getUsername().equals(callerUsername))
                .map(u -> new UserSearchDto(u.getId(), u.getUsername()))
                .toList();
    }

    // DTO mapping

    private MessageSummaryDto toSummary(Message m) {
        return new MessageSummaryDto(
                m.getId(),
                m.getSender().getId(),
                m.getSender().getUsername(),
                m.getRecipient().getId(),
                m.getRecipient().getUsername(),
                m.getEvent() != null ? m.getEvent().getId()    : null,
                m.getEvent() != null ? m.getEvent().getTitle() : null,
                m.getSubject(),
                m.getSentAt(),
                m.getReadAt() != null
        );
    }

    private MessageDto toDto(Message m) {
        return new MessageDto(
                m.getId(),
                m.getSender().getId(),
                m.getSender().getUsername(),
                m.getRecipient().getId(),
                m.getRecipient().getUsername(),
                m.getEvent() != null ? m.getEvent().getId()    : null,
                m.getEvent() != null ? m.getEvent().getTitle() : null,
                m.getSubject(),
                m.getBody(),
                m.getSentAt(),
                m.getReadAt()
        );
    }

    // guards

    private User requireByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    private Message requireMessage(Long id) {
        return messageRepository.findById(id)
                .orElseThrow(() -> new MessageNotFoundException(id));
    }
}
