package com.vitc.repository;

import com.vitc.entity.AssignmentOrder;
import com.vitc.entity.enums.OrderStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AssignmentOrderRepository extends JpaRepository<AssignmentOrder, Long> {

    Optional<AssignmentOrder> findByOrderCode(String orderCode);

    List<AssignmentOrder> findByEmailIgnoreCase(String email);

    List<AssignmentOrder> findByStatus(OrderStatus status);

    boolean existsByOrderCode(String orderCode);

    Optional<AssignmentOrder> findByDownloadTokenHash(String downloadTokenHash);

    boolean existsByDeliveryFilePath(String deliveryFilePath);

    List<AssignmentOrder> findByAssignment_IdAndStatusIn(Long assignmentId, Collection<OrderStatus> statuses);

    long countByAssignment_IdAndStatusIn(Long assignmentId, Collection<OrderStatus> statuses);

    /** Assignment orders with no checkout order behind them - a paid purchase has both, under one code. */
    @Query("select count(a) from AssignmentOrder a "
            + "where not exists (select p.id from PaymentOrder p where p.orderCode = a.orderCode)")
    long countWithoutCheckoutOrder();
}
