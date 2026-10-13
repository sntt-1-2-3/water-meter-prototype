package com.zhishuiyun.water.service;

import com.zhishuiyun.water.common.*;
import com.zhishuiyun.water.dto.ReadingTaskDto;
import com.zhishuiyun.water.entity.*;
import com.zhishuiyun.water.repository.*;
import com.zhishuiyun.water.security.AuthUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReadingService {
    private final ReadingTaskRepository taskRepository;
    private final MeterRepository meterRepository;
    private final AccountRepository accountRepository;
    private final ReportService reportService;
    private final AnomalyService anomalyService;
    private final AuditService auditService;
    private final AccessPolicy accessPolicy;
    private final NotificationService notifications;
    @Value("${simulator.enabled:false}")
    private volatile boolean schedulerEnabled;
    @Value("${simulator.interval-seconds:60}")
    private int intervalSeconds;
    private volatile LocalDateTime lastAutomatic;

    @Transactional
    public ReadingTaskDto startReading(AuthUser user) { return startReading(user, false); }

    @Transactional
    public ReadingTaskDto startReading(AuthUser user, boolean simulateFailure) {
        return createTask(user, simulateFailure, false);
    }

    private ReadingTaskDto createTask(AuthUser user, boolean simulateFailure, boolean systemTask) {
        accessPolicy.requireReadings(user);
        Optional<ReadingTask> active = taskRepository.findAllByOrderByIdDesc().stream()
                .filter(t -> user.getUsername().equals(t.getCreatedBy()) && isActive(t)).findFirst();
        if (active.isPresent()) return ReadingTaskDto.from(active.get());
        List<Meter> meters = accessPolicy.visibleMeters(user);
        if (meters.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "当前账号没有可抄表设备");
        LocalDateTime now = LocalDateTime.now();
        ReadingTask task = ReadingTask.builder().name(YearMonth.now() + " 自动抄表")
                .scope((user.isAdmin() ? "全部区域" : user.getAreaScope()) + " · " + meters.size() + " 台")
                .period(YearMonth.now().toString()).total(meters.size()).success(0).missed(0).failed(0).retryCount(0)
                .status(DomainEnums.TaskStatus.执行中).progress(0).planTime(now).startTime(now)
                .createdBy(user.getUsername()).createdRole(user.getRole().name()).areaScope(user.getAreaScope())
                .executionBy(user.getUsername()).systemTask(systemTask)
                .targetMeterIds(join(meters.stream().map(Meter::getMeterId).toList()))
                .completedMeterIds("").failedMeterIds("").retryAttemptedMeterIds("")
                .simulateFailure(simulateFailure).failureInjected(false).build();
        taskRepository.save(task);
        auditService.log(user.getUsername(), user.getRole().name(), "START_READING", String.valueOf(task.getId()),
                RequestContext.getRequestId(), "SUCCESS", null, "total=" + meters.size());
        return ReadingTaskDto.from(task);
    }

    @Transactional(readOnly = true)
    public List<ReadingTaskDto> listTasks(AuthUser user) {
        if (user.isUser()) throw new BusinessException(ErrorCode.FORBIDDEN);
        return taskRepository.findAllByOrderByIdDesc().stream()
                .filter(t -> user.getRole() != DomainEnums.AccountRole.OPERATOR || user.getUsername().equals(t.getCreatedBy()))
                .map(ReadingTaskDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ReadingTaskDto> listTasks() { return listTasks(com.zhishuiyun.water.security.CurrentUser.required()); }

    @Transactional
    public ReadingTaskDto retry(Long id, AuthUser user) {
        accessPolicy.requireReadings(user);
        ReadingTask task = taskRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!user.isAdmin() && !user.getUsername().equals(task.getCreatedBy())) throw new BusinessException(ErrorCode.FORBIDDEN);
        for (String meterId : split(task.getFailedMeterIds()))
            if (!accessPolicy.canAccessMeterId(user, meterId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (task.getStatus() != DomainEnums.TaskStatus.失败待重试) return ReadingTaskDto.from(task);
        task.setExecutionBy(user.getUsername()); task.setSystemTask(false);
        task.setRetryAttemptedMeterIds("");
        task.setRetryCount(task.getRetryCount() + 1); task.setStatus(DomainEnums.TaskStatus.补抄中); task.setEndTime(null);
        taskRepository.save(task);
        return ReadingTaskDto.from(task);
    }

    /** Persisted state advances in bounded batches; tasks survive application restarts. */
    @Scheduled(fixedDelay = 1000, initialDelay = 1000)
    @Transactional
    public void processTasks() {
        for (ReadingTask task : taskRepository.findAllByOrderByIdDesc()) {
            if (!isActive(task) || task.getTargetMeterIds() == null) continue;
            progress(task);
        }
        if (schedulerEnabled && (lastAutomatic == null
                || lastAutomatic.plusSeconds(Math.max(10, intervalSeconds)).isBefore(LocalDateTime.now()))) {
            lastAutomatic = LocalDateTime.now();
            for (Meter meter : meterRepository.findAll()) {
                if (meter.getUpdatedAt() == null || meter.getUpdatedAt().isBefore(LocalDateTime.now().minusHours(12)))
                    anomalyService.offline(meter, LocalDateTime.now());
            }
            createTask(systemUser(), false, true);
        }
    }

    private void progress(ReadingTask task) {
        Set<String> completed = new LinkedHashSet<>(split(task.getCompletedMeterIds()));
        Set<String> failed = new LinkedHashSet<>(split(task.getFailedMeterIds()));
        Set<String> retried = new LinkedHashSet<>(split(task.getRetryAttemptedMeterIds()));
        boolean retry = task.getStatus() == DomainEnums.TaskStatus.补抄中;
        List<String> pending = retry ? failed.stream().filter(id -> !retried.contains(id)).toList()
                : split(task.getTargetMeterIds()).stream().filter(id -> !completed.contains(id) && !failed.contains(id)).toList();
        AuthUser actor = executionActor(task);
        if (actor == null) {
            List<String> remaining = split(task.getTargetMeterIds()).stream().filter(id -> !completed.contains(id)).toList();
            task.setFailedMeterIds(join(remaining)); task.setFailed(remaining.size()); task.setMissed(remaining.size());
            task.setStatus(DomainEnums.TaskStatus.失败待重试); task.setEndTime(LocalDateTime.now());
            taskRepository.save(task);
            auditService.log("simulator", "SYSTEM", "READING_PERMISSION_REVOKED", String.valueOf(task.getId()),
                    RequestContext.getRequestId(), "FORBIDDEN", null, "执行账号已停用或无抄表权限");
            return;
        }
        for (String id : pending.stream().limit(10).toList()) {
            if (retry) retried.add(id);
            if (!retry && Boolean.TRUE.equals(task.getSimulateFailure()) && !Boolean.TRUE.equals(task.getFailureInjected())) {
                task.setFailureInjected(true); failed.add(id);
                auditService.log(actor.getUsername(), actor.getRole().name(), "SIMULATED_MISSED_READING", id,
                        RequestContext.getRequestId(), "RETRY_REQUIRED", null, "首次采集超时，自动补抄");
                continue;
            }
            try { reportService.collectSimulated(id, "normal", actor); completed.add(id); failed.remove(id); }
            catch (BusinessException e) { failed.add(id); log.warn("Task {} meter {} rejected: {}", task.getId(), id, e.getErrorCode()); }
        }
        task.setCompletedMeterIds(join(completed)); task.setFailedMeterIds(join(failed));
        task.setRetryAttemptedMeterIds(join(retried));
        task.setSuccess(completed.size()); task.setFailed(failed.size()); task.setMissed(failed.size());
        task.setProgress(task.getTotal() == 0 ? 100 : completed.size() * 100 / task.getTotal());
        boolean allAttempted = completed.size() + failed.size() >= task.getTotal();
        if (allAttempted && failed.isEmpty()) {
            task.setStatus(DomainEnums.TaskStatus.已完成); task.setProgress(100); task.setEndTime(LocalDateTime.now());
            notifications.notifyUser(task.getCreatedBy(), "抄表任务已完成", task.getSuccess() + "/" + task.getTotal()
                    + " 台已入库；补抄 " + task.getRetryCount() + " 次", "/admin/reading");
        } else if (allAttempted && !failed.isEmpty() && !retry && task.getRetryCount() == 0) {
            task.setStatus(DomainEnums.TaskStatus.补抄中); task.setRetryCount(1);
            task.setRetryAttemptedMeterIds("");
        } else if (allAttempted && !failed.isEmpty() && retry && pending.size() <= 10) {
            task.setStatus(DomainEnums.TaskStatus.失败待重试); task.setEndTime(LocalDateTime.now());
            notifications.notifyUser(task.getCreatedBy(), "抄表任务需要重试", failed.size() + " 台仍未完成，请检查设备后补抄", "/admin/reading");
        }
        taskRepository.save(task);
    }

    @Transactional
    public void simulate(String meterId, String scenario, AuthUser user) {
        accessPolicy.requireAdmin(user);
        if (!Set.of("normal", "high_flow", "alarm", "offline").contains(scenario == null ? "" : scenario))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "模拟场景无效");
        Meter meter = meterRepository.findByMeterId(meterId).orElseThrow(() -> new BusinessException(ErrorCode.METER_NOT_FOUND));
        accessPolicy.requireAccessMeter(user, meter);
        if ("offline".equals(scenario)) anomalyService.offline(meter, LocalDateTime.now());
        else for (int n = 0; n < ("high_flow".equals(scenario) ? 3 : 1); n++)
            reportService.collectSimulated(meterId, scenario, user);
    }

    public SchedulerState scheduler(AuthUser user) { accessPolicy.requireAdmin(user); return new SchedulerState(schedulerEnabled, Math.max(10, intervalSeconds)); }
    public SchedulerState setScheduler(boolean enabled, AuthUser user) {
        accessPolicy.requireAdmin(user); boolean previous = schedulerEnabled; schedulerEnabled = enabled;
        auditService.log(user.getUsername(), user.getRole().name(), "SET_READING_SCHEDULER", "simulator",
                RequestContext.getRequestId(), "SUCCESS", String.valueOf(previous), String.valueOf(enabled));
        return scheduler(user);
    }
    public record SchedulerState(boolean enabled, int intervalSeconds) {}

    private boolean isActive(ReadingTask t) { return t.getStatus() == DomainEnums.TaskStatus.执行中 || t.getStatus() == DomainEnums.TaskStatus.补抄中; }
    private List<String> split(String ids) { return ids == null || ids.isBlank() ? List.of() : Arrays.asList(ids.split(",")); }
    private String join(Collection<String> ids) { return String.join(",", ids); }
    private AuthUser systemUser() { return AuthUser.builder().username("simulator").name("定时模拟采集").role(DomainEnums.AccountRole.ADMIN).build(); }
    private AuthUser executionActor(ReadingTask task) {
        if (Boolean.TRUE.equals(task.getSystemTask())) return systemUser();
        String username = task.getExecutionBy() == null ? task.getCreatedBy() : task.getExecutionBy();
        if (username == null) return null;
        Account account = accountRepository.findByUsername(username).orElse(null);
        if (account == null || !Boolean.TRUE.equals(account.getEnabled())
                || (account.getRole() != DomainEnums.AccountRole.ADMIN && account.getRole() != DomainEnums.AccountRole.OPERATOR)) return null;
        return AuthUser.builder().username(account.getUsername()).name(account.getName()).role(account.getRole())
                .areaScope(account.getAreaScope()).boundMeterId(account.getBoundMeterId()).build();
    }
}
