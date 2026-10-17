package gr.uoa.di.ted.events.security;

import gr.uoa.di.ted.events.entity.User;
import gr.uoa.di.ted.events.enums.Role;
import gr.uoa.di.ted.events.enums.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Adapter between our {@link User} entity and Spring Security's {@link UserDetails}.
 *
 * Authority mapping (role is chosen at registration): ADMIN gets ROLE_ADMIN;
 * ORGANIZER gets both ROLE_ORGANIZER and ROLE_PARTICIPANT (an organizer can create
 * events and also book tickets); PARTICIPANT gets only ROLE_PARTICIPANT (browse and
 * book, but cannot create events).
 *
 * isEnabled() is false while PENDING and isAccountNonLocked() is false when
 * REJECTED, but we don't go through Spring's auth manager: AuthService.login()
 * checks status itself to give friendlier messages than the defaults.
 */
public class UserDetailsImpl implements UserDetails {

    private final User                     user;
    private final List<GrantedAuthority>   authorities;

    public UserDetailsImpl(User user) {
        this.user        = user;
        this.authorities = buildAuthorities(user.getRole());
    }

    private static List<GrantedAuthority> buildAuthorities(Role role) {
        return switch (role) {
            case ADMIN       -> List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
            case ORGANIZER   -> List.of(
                    new SimpleGrantedAuthority("ROLE_ORGANIZER"),
                    new SimpleGrantedAuthority("ROLE_PARTICIPANT")
            );
            case PARTICIPANT -> List.of(new SimpleGrantedAuthority("ROLE_PARTICIPANT"));
        };
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String  getPassword() { return user.getPasswordHash(); }
    @Override public String  getUsername() { return user.getUsername(); }
    @Override public boolean isEnabled()          { return user.getStatus() == UserStatus.ACTIVE; }
    @Override public boolean isAccountNonLocked() { return user.getStatus() != UserStatus.REJECTED; }

    // exposed for the JWT filter and AuthService
    public User    getUser()               { return user; }
    public Long    getUserId()             { return user.getId(); }
    public boolean isMustChangePassword()  { return user.isMustChangePassword(); }
}
