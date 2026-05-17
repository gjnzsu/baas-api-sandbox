package com.example.baas.sandbox.payment;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryPaymentRepository implements PaymentRepository {

    private final ConcurrentMap<String, StoredPayment> payments = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, IdempotencyRecord> idempotencyRecords = new ConcurrentHashMap<>();

    @Override
    public void save(StoredPayment payment) {
        payments.put(payment.response().paymentId(), payment);
    }

    @Override
    public Optional<StoredPayment> findById(String paymentId) {
        return Optional.ofNullable(payments.get(paymentId));
    }

    @Override
    public Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(idempotencyRecords.get(idempotencyKey));
    }

    @Override
    public void saveIdempotencyRecord(String idempotencyKey, IdempotencyRecord record) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyRecords.put(idempotencyKey, record);
        }
    }
}
