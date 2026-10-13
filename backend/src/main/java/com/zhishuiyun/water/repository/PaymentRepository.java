package com.zhishuiyun.water.repository;
import com.zhishuiyun.water.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByPayerAndIdempotencyKey(String payer, String idempotencyKey);
    Optional<Payment> findByBillItemId(Long billItemId);
    List<Payment> findAllByOrderByIdDesc();
}
