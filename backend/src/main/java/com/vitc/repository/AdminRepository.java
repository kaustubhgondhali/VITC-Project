package com.vitc.repository;

import com.vitc.entity.Admin;
import com.vitc.entity.enums.AdminRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminRepository extends JpaRepository<Admin, Long> {

    Optional<Admin> findByUsernameIgnoreCase(String username);

    Optional<Admin> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    List<Admin> findByActiveTrue();

    List<Admin> findByRole(AdminRole role);
}
