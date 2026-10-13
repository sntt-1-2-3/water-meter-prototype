package com.zhishuiyun.water.repository;

import com.zhishuiyun.water.entity.UserFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserFeedbackRepository extends JpaRepository<UserFeedback, Long> {
    List<UserFeedback> findAllByOrderByIdDesc();
}
