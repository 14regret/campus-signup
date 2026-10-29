package com.campus.signup.audit;

import com.campus.signup.security.JwtPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * 操作日志切面。
 *
 * <p>三个刻意的设计：
 * <ol>
 *   <li><b>先执行再记日志</b>：{@code joinPoint.proceed()} 抛异常时直接冒泡，不写日志。
 *       日志记的是「做成了什么」，不是「尝试过什么」；</li>
 *   <li><b>最高优先级</b>：切面在事务代理外层，proceed() 返回意味着事务已经提交，
 *       不会出现「日志说发布了，其实事务回滚了」；</li>
 *   <li><b>整段 try-catch</b>：日志失败只打 warn。审计是旁路，不能因为写日志把业务搞挂。</li>
 * </ol>
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogRecorder recorder;
    private final ObjectMapper objectMapper;

    private final ExpressionParser expressionParser = new SpelExpressionParser();

    @Around("@annotation(com.campus.signup.audit.OperationLog)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();

        // 从方法签名上取注解，而不是用 @annotation(operationLog) 绑定参数：
        // 在 CGLIB 代理链（方法上有 @PreAuthorize）下参数绑定会失效，
        // 报 "Required to bind 2 arguments, but only bound 1"。
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        OperationLog annotation = signature.getMethod().getAnnotation(OperationLog.class);
        if (annotation == null) {
            return result;
        }

        try {
            recorder.record(
                    currentOperator(),
                    annotation.action(),
                    annotation.targetType(),
                    resolveTargetId(joinPoint, annotation),
                    buildDetail(joinPoint),
                    currentIp());
        } catch (Exception e) {
            log.warn("操作日志写入失败，已忽略（不影响业务）：action={} reason={}",
                    annotation.action(), e.getMessage());
        }

        return result;
    }

    /** 当前操作人。系统任务（无 SecurityContext）时返回 null，日志里记成 system。 */
    private static JwtPayload currentOperator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof JwtPayload payload) {
            return payload;
        }
        return null;
    }

    /**
     * 把 {@code targetId = "#id"} 求值成真正的 ID。
     * 参数名依赖编译期的 {@code -parameters}，pom 里已经打开。
     */
    private Long resolveTargetId(ProceedingJoinPoint joinPoint, OperationLog operationLog) {
        String expression = operationLog.targetId();
        if (!StringUtils.hasText(expression)) {
            return null;
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] names = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        StandardEvaluationContext context = new StandardEvaluationContext();
        if (names != null) {
            for (int i = 0; i < names.length && i < args.length; i++) {
                context.setVariable(names[i], args[i]);
            }
        }

        Object value = expressionParser.parseExpression(expression).getValue(context);
        return value instanceof Number number ? number.longValue() : null;
    }

    /** 记录「调了哪个方法、带了什么参数」，够回答审计问题，又不至于把日志表撑爆。 */
    private String buildDetail(ProceedingJoinPoint joinPoint) {
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                    "method", joinPoint.getSignature().toShortString(),
                    "args", joinPoint.getArgs()));
            return json.length() > 500 ? json.substring(0, 500) : json;
        } catch (Exception e) {
            // 参数里有不可序列化的对象时放弃 detail，不能因此影响主流程
            return null;
        }
    }

    private static String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }
}
