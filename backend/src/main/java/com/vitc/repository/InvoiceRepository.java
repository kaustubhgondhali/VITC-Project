package com.vitc.repository;

import com.vitc.entity.Invoice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    Optional<Invoice> findByOrderId(Long orderId);

    Optional<Invoice> findByOrderOrderCode(String orderCode);

    List<Invoice> findByBillingEmailIgnoreCaseOrderByIdDesc(String email);
}
