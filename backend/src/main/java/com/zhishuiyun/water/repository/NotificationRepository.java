package com.zhishuiyun.water.repository;

import com.zhishuiyun.water.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop200ByRecipientOrderByIdDesc(String recipient);
    List<Notification> findByRecipientAndReadAtIsNull(String recipient);
    Optional<Notification> findByIdAndRecipient(Long id, String recipient);
}
