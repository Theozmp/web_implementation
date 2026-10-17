package gr.uoa.di.ted.events.controller;

import gr.uoa.di.ted.events.dto.message.*;
import gr.uoa.di.ted.events.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageSvc;

    /** POST /api/v1/messages: send a message. */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageDto sendMessage(@Valid @RequestBody MessageCreateRequest req,
                                  Principal principal) {
        return messageSvc.send(req, principal.getName());
    }

    /** GET /api/v1/messages/inbox: paginated inbox, newest first. */
    @GetMapping("/inbox")
    @PreAuthorize("isAuthenticated()")
    public Page<MessageSummaryDto> inbox(
            @PageableDefault(size = 20, sort = "sentAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            Principal principal) {
        return messageSvc.getInbox(principal.getName(), pageable);
    }

    /** GET /api/v1/messages/sent: paginated sent-box, newest first. */
    @GetMapping("/sent")
    @PreAuthorize("isAuthenticated()")
    public Page<MessageSummaryDto> sent(
            @PageableDefault(size = 20, sort = "sentAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            Principal principal) {
        return messageSvc.getSent(principal.getName(), pageable);
    }

    /**
     * GET /api/v1/messages/{id}: full message body. Sets readAt on the
     * recipient's first fetch.
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public MessageDto getMessage(@PathVariable Long id, Principal principal) {
        return messageSvc.getMessage(id, principal.getName());
    }

    /**
     * DELETE /api/v1/messages/{id}?box=inbox|sent: soft-delete on the caller's
     * side. The row stays until both parties delete it (a future cleanup job).
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMessage(@PathVariable Long id,
                               @RequestParam(defaultValue = "inbox") String box,
                               Principal principal) {
        messageSvc.deleteMessage(id, principal.getName(), box);
    }

    /**
     * GET /api/v1/messages/unread-count: badge counter the frontend polls every
     * 30s. Lightweight on purpose, just a single COUNT query.
     */
    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public UnreadCountDto unreadCount(Principal principal) {
        return new UnreadCountDto(messageSvc.getUnreadCount(principal.getName()));
    }

    /**
     * GET /api/v1/messages/recipients/search?q=: autocomplete for the compose
     * dialog. Returns up to 10 active users whose username contains the fragment
     * (case-insensitive), excluding the caller so you can't message yourself.
     */
    @GetMapping("/recipients/search")
    @PreAuthorize("isAuthenticated()")
    public List<UserSearchDto> searchRecipients(@RequestParam String q, Principal principal) {
        return messageSvc.searchUsers(q, principal.getName());
    }
}
