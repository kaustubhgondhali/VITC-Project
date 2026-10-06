package com.vitc.repository;

import com.vitc.entity.Payment;
import com.vitc.entity.enums.PaymentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByTransactionId(String transactionId);

    Optional<Payment> findByProviderOrderId(String providerOrderId);

    Optional<Payment> findFirstByOrderIdOrderByIdDesc(Long orderId);

    Optional<Payment> findFirstByOrderIdAndStatusOrderByIdDesc(Long orderId, PaymentStatus status);

    List<Payment> findByOrderIdOrderByIdDesc(Long orderId);

    List<Payment> findByEmailIgnoreCase(String email);

    List<Payment> findByStatus(PaymentStatus status);

    List<Payment> findByReferenceTypeAndReferenceId(String referenceType, Long referenceId);
}
