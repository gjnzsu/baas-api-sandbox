package com.example.baas.sandbox.payment;

import java.util.Optional;

public interface PaymentRepository {

    void save(StoredPayment payment);

    Optional<StoredPayment> findById(String paymentId);

    Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);

    void saveIdempotencyRecord(String idempotencyKey, IdempotencyRecord record);
}
