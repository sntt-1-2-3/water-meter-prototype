package com.zhishuiyun.water.service;

import com.zhishuiyun.water.common.BusinessException;
import com.zhishuiyun.water.common.ErrorCode;
import com.zhishuiyun.water.dto.NotificationDto;
import com.zhishuiyun.water.entity.DomainEnums.AccountRole;
import com.zhishuiyun.water.entity.Notification;
import com.zhishuiyun.water.repository.AccountRepository;
import com.zhishuiyun.water.repository.NotificationRepository;
import com.zhishuiyun.water.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final AccountRepository accountRepository;

    public void notifyAdministrators(String title, String content, String route) {
        accountRepository.findAll().stream()
                .filter(account -> account.getRole() == AccountRole.ADMIN && Boolean.TRUE.equals(account.getEnabled()))
                .forEach(account -> notifyUser(account.getUsername(), title, content, route));
    }

    public void notifyMeterOwner(String meterId, String title, String content, String route) {
        if (meterId == null) return;
        accountRepository.findAll().stream()
                .filter(account -> account.getRole() == AccountRole.USER && Boolean.TRUE.equals(account.getEnabled())
                        && meterId.equals(account.getBoundMeterId()))
                .forEach(account -> notifyUser(account.getUsername(), title, content, route));
    }

    public void notifyUser(String username, String title, String content, String route) {
        if (username == null || accountRepository.findByUsername(username).isEmpty()) return;
        notificationRepository.save(Notification.builder().recipient(username).title(title)
                .content(content).route(route).createdAt(LocalDateTime.now()).build());
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> list(AuthUser user) {
        return notificationRepository.findTop200ByRecipientOrderByIdDesc(user.getUsername())
                .stream().map(NotificationDto::from).toList();
    }

    @Transactional(readOnly = true)
    public NotificationDto get(Long id, AuthUser user) {
        return NotificationDto.from(owned(id, user));
    }

    public NotificationDto markRead(Long id, AuthUser user) {
        Notification message = owned(id, user);
        if (message.getReadAt() == null) {
            message.setReadAt(LocalDateTime.now());
            notificationRepository.save(message);
        }
        return NotificationDto.from(message);
    }

    public int markAllRead(AuthUser user) {
        List<Notification> messages = notificationRepository.findByRecipientAndReadAtIsNull(user.getUsername());
        LocalDateTime now = LocalDateTime.now();
        messages.forEach(message -> message.setReadAt(now));
        notificationRepository.saveAll(messages);
        return messages.size();
    }

    private Notification owned(Long id, AuthUser user) {
        return notificationRepository.findByIdAndRecipient(id, user.getUsername())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
