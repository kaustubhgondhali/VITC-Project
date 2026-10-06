package com.vitc.repository;

import com.vitc.entity.User;
import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByPhone(String phone);

    boolean existsByEmailIgnoreCase(String email);

    List<User> findByRole(UserRole role);

    List<User> findByStatus(UserStatus status);

    Page<User> findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String fullName, String email, Pageable pageable);

    /* ---------------- Student portal ---------------- */

    Optional<User> findByStudentLoginIdIgnoreCase(String studentLoginId);

    boolean existsByStudentLoginIdIgnoreCase(String studentLoginId);

    /** Highest numeric suffix already issued, used to mint the next VITCSTU id. */
    @Query("select max(cast(substring(u.studentLoginId, 8) as long)) from User u "
            + "where u.studentLoginId is not null")
    Long findMaxStudentLoginSequence();

    /**
     * Part 4 - looks up the account a "forgot password" reset code belongs to,
     * scoped to a single role so a code issued for a student can never be
     * replayed against a teacher account (or vice versa) even though both
     * share this table.
     */
    Optional<User> findByResetTokenAndRole(String resetToken, UserRole role);

    /* ---------------- Teacher admin ---------------- */

    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);
}
