package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.response.InvoiceResponse;
import com.vitc.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Invoices", description = "Invoices generated on successful payment")
@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService service;

    @Operation(summary = "List invoices (optionally by billing email)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<InvoiceResponse>>> getAll(@RequestParam(required = false) String email) {
        return ResponseEntity.ok(ApiResponse.ok(email == null ? service.getAll() : service.getByEmail(email)));
    }

    @Operation(summary = "Get an invoice by id")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InvoiceResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
    }

    @Operation(summary = "Get an invoice by invoice number")
    @GetMapping("/number/{invoiceNumber}")
    public ResponseEntity<ApiResponse<InvoiceResponse>> getByNumber(@PathVariable String invoiceNumber) {
        return ResponseEntity.ok(ApiResponse.ok(service.getByNumber(invoiceNumber)));
    }

    @Operation(summary = "Get the invoice of an order")
    @GetMapping("/order/{orderCode}")
    public ResponseEntity<ApiResponse<InvoiceResponse>> getByOrder(@PathVariable String orderCode) {
        return ResponseEntity.ok(ApiResponse.ok(service.getByOrderCode(orderCode)));
    }
}
