package com.vitc.repository;

import com.vitc.entity.Assignment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    Optional<Assignment> findByCode(String code);

    boolean existsByCode(String code);

    List<Assignment> findByActiveTrue();

    List<Assignment> findByCategoryIgnoreCase(String category);

    boolean existsByProjectFilePath(String projectFilePath);
}
