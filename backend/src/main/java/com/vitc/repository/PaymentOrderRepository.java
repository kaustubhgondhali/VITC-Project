package com.vitc.repository;

import com.vitc.entity.PaymentOrder;
import com.vitc.entity.enums.PaymentOrderStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    Optional<PaymentOrder> findByOrderCode(String orderCode);

    List<PaymentOrder> findByEmailIgnoreCaseOrderByIdDesc(String email);

    List<PaymentOrder> findByStatus(PaymentOrderStatus status);
}
