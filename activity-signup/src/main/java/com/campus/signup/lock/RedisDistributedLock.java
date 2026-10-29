package com.campus.signup.lock;

import com.campus.signup.config.LockProperties;
import com.campus.signup.exception.BusinessException;
import com.campus.signup.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * 基于 Redis 的分布式锁（SET NX PX + Lua 校验后删除）。
 *
 * <p>为什么不用 Redisson：本项目的临界区只有几条 SQL（毫秒级），不需要看门狗续期、
 * 可重入、公平锁这些能力，手写实现更容易讲清楚每个细节，也少一个强依赖。
 * 一旦临界区可能超过锁 TTL（比如要调外部接口），就应该换成 Redisson 的 watchdog。
 *
 * <p>三个必须做对的点：
 * <ol>
 *   <li><b>值是随机 token，不是固定字符串</b>：释放时要校验「这把锁是不是我加的」，
 *       否则 A 的锁过期、B 拿到锁，A 执行完把 B 的锁删了；</li>
 *   <li><b>释放用 Lua</b>：GET + 比较 + DEL 必须是原子操作，分开写就有中间态；</li>
 *   <li><b>必须设 TTL</b>：否则持锁进程崩了，这个活动就永远报不了名。</li>
 * </ol>
 */
@Slf4j
@Component
public class RedisDistributedLock {

    /**
     * 只有值匹配才删除。KEYS[1] = 锁 key，ARGV[1] = 本线程持有的 token。
     */
    private static final String RELEASE_SCRIPT = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """;

    private static final long RETRY_INTERVAL_MILLIS = 30L;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> releaseScript;
    private final LockProperties lockProperties;

    public RedisDistributedLock(StringRedisTemplate redisTemplate, LockProperties lockProperties) {
        this.redisTemplate = redisTemplate;
        this.lockProperties = lockProperties;
        this.releaseScript = new DefaultRedisScript<>(RELEASE_SCRIPT, Long.class);
    }

    /**
     * 加锁，带自旋等待。拿不到锁时抛 {@link ErrorCode#ACTIVITY_BUSY}，不无限阻塞。
     *
     * @param key 锁名，建议 {@code lock:activity:{id}}
     */
    public LockHandle lock(String key) {
        return lock(key, lockProperties.waitTime(), lockProperties.leaseTime());
    }

    public LockHandle lock(String key, Duration waitTime, Duration leaseTime) {
        String token = UUID.randomUUID().toString();
        long deadline = System.nanoTime() + waitTime.toNanos();
        int attempts = 0;

        while (true) {
            attempts++;
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, leaseTime);
            if (Boolean.TRUE.equals(acquired)) {
                log.debug("获取锁成功 key={} attempts={}", key, attempts);
                return new LockHandle(key, token);
            }
            if (System.nanoTime() >= deadline) {
                log.warn("获取锁超时 key={} waitTime={} attempts={}", key, waitTime, attempts);
                throw new BusinessException(ErrorCode.ACTIVITY_BUSY);
            }
            try {
                Thread.sleep(RETRY_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ErrorCode.ACTIVITY_BUSY);
            }
        }
    }

    /**
     * 释放锁。即使释放失败也只记日志：锁有 TTL 兜底，不该因为这个把业务结果改成失败。
     */
    public void unlock(LockHandle handle) {
        if (handle == null) {
            return;
        }
        try {
            Long deleted = redisTemplate.execute(releaseScript,
                    Collections.singletonList(handle.key()), handle.token());
            if (deleted == null || deleted == 0L) {
                // 说明锁已经过期并被别人拿走了，本次释放是空操作——这属于正常现象，不是错误
                log.warn("释放锁时 token 不匹配，锁可能已过期 key={}", handle.key());
            }
        } catch (Exception e) {
            log.error("释放锁失败 key={}，将等待 TTL 自动过期", handle.key(), e);
        }
    }

    /**
     * 一次加锁的凭证。用完必须归还，建议写成 {@code try (LockHandle h = lock.lock(k)) { ... }}。
     */
    public record LockHandle(String key, String token) {
    }
}
