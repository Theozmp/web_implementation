package gr.uoa.di.ted.events.service;

import gr.uoa.di.ted.events.dto.admin.RejectRequest;
import gr.uoa.di.ted.events.dto.admin.UserDetailDto;
import gr.uoa.di.ted.events.dto.admin.UserListItemDto;
import gr.uoa.di.ted.events.entity.AdminAuditLog;
import gr.uoa.di.ted.events.entity.User;
import gr.uoa.di.ted.events.enums.UserStatus;
import gr.uoa.di.ted.events.exception.UserNotFoundException;
import gr.uoa.di.ted.events.repository.AdminAuditLogRepository;
import gr.uoa.di.ted.events.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserRepository          userRepository;
    private final AdminAuditLogRepository auditLogRepository;

    // query

    /**
     * Paginated user list, optionally filtered by status and a free-text query
     * (matched against username, email, firstName, lastName). Uses a Specification
     * so the predicates compose dynamically instead of needing conditional @Query
     * strings.
     */
    public Page<UserListItemDto> findUsers(String q, UserStatus status, Pageable pageable) {
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (q != null && !q.isBlank()) {
                String pattern = "%" + q.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("username")),  pattern),
                        cb.like(cb.lower(root.get("email")),     pattern),
                        cb.like(cb.lower(root.get("firstName")), pattern),
                        cb.like(cb.lower(root.get("lastName")),  pattern)
                ));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };

        return userRepository.findAll(spec, pageable).map(this::toListItem);
    }

    public UserDetailDto getUserDetail(Long id) {
        return userRepository.findById(id)
                .map(this::toDetail)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    // status actions

    @Transactional
    public UserDetailDto approveUser(Long id, String adminUsername) {
        User target = requireUser(id);
        User admin  = requireUserByUsername(adminUsername);

        target.setStatus(UserStatus.ACTIVE);
        auditLogRepository.save(AdminAuditLog.of(admin, target, "APPROVE", null));

        return toDetail(target);
    }

    @Transactional
    public UserDetailDto rejectUser(Long id, RejectRequest req, String adminUsername) {
        User target = requireUser(id);
        User admin  = requireUserByUsername(adminUsername);

        target.setStatus(UserStatus.REJECTED);
        String reason = (req != null && req.reason() != null && !req.reason().isBlank())
                ? req.reason() : null;
        auditLogRepository.save(AdminAuditLog.of(admin, target, "REJECT", reason));

        return toDetail(target);
    }

    // helpers

    private User requireUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    private User requireUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Admin not found: " + username));
    }

    private UserListItemDto toListItem(User u) {
        return new UserListItemDto(
                u.getId(), u.getUsername(), u.getFirstName(), u.getLastName(),
                u.getEmail(), u.getRole().name(), u.getStatus().name(), u.getCreatedAt());
    }

    private UserDetailDto toDetail(User u) {
        return new UserDetailDto(
                u.getId(), u.getUsername(), u.getFirstName(), u.getLastName(),
                u.getEmail(), u.getRole().name(), u.getStatus().name(),
                u.isMustChangePassword(),
                u.getPhone(), u.getAddressLine(), u.getCity(), u.getCountry(),
                u.getLatitude(), u.getLongitude(), u.getTaxId(),
                u.getCreatedAt(), u.getUpdatedAt());
    }
}
