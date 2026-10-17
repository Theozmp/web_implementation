package gr.uoa.di.ted.events.repository;

import gr.uoa.di.ted.events.entity.User;
import gr.uoa.di.ted.events.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>,
                                        JpaSpecificationExecutor<User> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    // admin user-management screen
    List<User> findByStatus(UserStatus status);

    // message compose autocomplete
    List<User> findTop10ByUsernameContainingIgnoreCaseAndStatusOrderByUsernameAsc(
            String fragment, UserStatus status);
}
