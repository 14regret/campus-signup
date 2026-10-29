package com.campus.signup.audit;

import com.campus.signup.entity.OperationLog;
import com.campus.signup.repository.OperationLogRepository;
import com.campus.signup.security.JwtPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 操作日志落库。
 *
 * <p>用 {@code REQUIRES_NEW} 单独开一个事务：日志表和业务表互不影响，
 * 就算日志写入失败回滚，也只是丢一条审计记录，业务数据该提交的照常提交。
 *
 * <p>异常不在这一层吞：故意让它冒到切面，由切面统一记 warn。
 * 如果在这里 catch 了，事务会被标记 rollback-only，最后在 commit 时炸出来，
 * 反而更难排查。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogRecorder {

    private final OperationLogRepository operationLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(JwtPayload operator, String action, String targetType,
                       Long targetId, String detail, String ip) {
        OperationLog entity = new OperationLog();
        entity.setOperatorId(operator == null ? null : operator.userId());
        entity.setOperatorName(operator == null ? null : operator.username());
        entity.setAction(action);
        entity.setTargetType(targetType);
        entity.setTargetId(targetId);
        entity.setDetail(detail);
        entity.setIp(ip);

        OperationLog saved = operationLogRepository.save(entity);
        log.info("操作日志 #{} operator={} action={} target={}:{}",
                saved.getId(),
                operator == null ? "system" : operator.username(),
                action, targetType, targetId);
    }
}
