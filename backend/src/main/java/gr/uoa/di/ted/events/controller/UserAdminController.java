package gr.uoa.di.ted.events.controller;

import gr.uoa.di.ted.events.dto.admin.RejectRequest;
import gr.uoa.di.ted.events.dto.admin.UserDetailDto;
import gr.uoa.di.ted.events.dto.admin.UserListItemDto;
import gr.uoa.di.ted.events.enums.UserStatus;
import gr.uoa.di.ted.events.security.UserDetailsImpl;
import gr.uoa.di.ted.events.service.UserAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Admin-only REST endpoints for user management.
 *
 * The class-level @PreAuthorize requires ROLE_ADMIN on every method (individual
 * methods can tighten further). The JWT validity check from
 * .anyRequest().authenticated() is the first layer; @PreAuthorize checks the role.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService service;

    /**
     * Paged user list with optional status filter and free-text search. Sorted by
     * newest registration first; the client can override via ?sort=username,asc.
     */
    @GetMapping
    public Page<UserListItemDto> listUsers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UserStatus status,
            @PageableDefault(size = 20)
            @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return service.findUsers(q, status, pageable);
    }

    @GetMapping("/{id}")
    public UserDetailDto getUser(@PathVariable Long id) {
        return service.getUserDetail(id);
    }

    @PostMapping("/{id}/approve")
    public UserDetailDto approveUser(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        return service.approveUser(id, principal.getUsername());
    }

    @PostMapping("/{id}/reject")
    public UserDetailDto rejectUser(
            @PathVariable Long id,
            @RequestBody(required = false) RejectRequest req,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        return service.rejectUser(id, req, principal.getUsername());
    }
}
