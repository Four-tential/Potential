package four_tential.potential.application.order;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.OrderExceptionEnum;
import four_tential.potential.domain.order.WaitingListRepository;
import four_tential.potential.infra.redis.RedisConstants;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class
WaitingListService {

    private final WaitingListRepository waitingListRepository;
    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;

    public boolean tryOccupyingStock(UUID courseId, UUID memberId) {
        String lockKey = RedisConstants.ORDER_LOCK_PREFIX + courseId + ":" + memberId;
        RLock lock = redissonClient.getLock(lockKey);

        try{
            if (lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                String occupancyKey = RedisConstants.USER_COURSE_OCCUPANCY_PREFIX + courseId + ":" + memberId;
                String stockKey = RedisConstants.COURSE_STOCK_PREFIX + courseId;

                // 이미 해당 강의의 재고를 점유하고 있는지 확인
                if (Boolean.TRUE.equals(redisTemplate.hasKey(occupancyKey))) {
                    throw new ServiceErrorException(OrderExceptionEnum.ERR_DUPLICATE_ORDER);
                }

                // Redis에서 재고 차감
                Long stock = redisTemplate.opsForValue().decrement(stockKey);

                if (stock != null && stock >= 0) {
                    // 재고 점유 성공 시 10분 유지
                    redisTemplate.opsForValue().set(occupancyKey, "OCCUPIED", Duration.ofMinutes(10));
                    return true;
                }

                // 재고 부족
                redisTemplate.opsForValue().increment(stockKey);
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceErrorException(OrderExceptionEnum.ERR_LOCK_INTERRUPTED);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        return false;
    }

    public void rollbackOccupiedStock(UUID courseId, UUID memberId) {
        String lockKey = RedisConstants.ORDER_LOCK_PREFIX + courseId + ":" + memberId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                String occupancyKey = RedisConstants.USER_COURSE_OCCUPANCY_PREFIX + courseId + ":" + memberId;
                String stockKey = RedisConstants.COURSE_STOCK_PREFIX + courseId;

                // Lua 스크립트를 사용하여 점유 확인, 삭제, 재고 복구를 원자적으로 처리
                String luaScript =
                        "if redis.call('exists', KEYS[1]) == 1 then " +
                                "  redis.call('del', KEYS[1]) " +
                                "  return redis.call('incr', KEYS[2]) " +
                                "else " +
                                "  return nil " +
                                "end";

                redisTemplate.execute(
                        new org.springframework.data.redis.core.script.DefaultRedisScript<>(luaScript, Long.class),
                        java.util.List.of(occupancyKey, stockKey)
                );
            } else {
                throw new ServiceErrorException(OrderExceptionEnum.ERR_LOCK_ACQUISITION_FAILED);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceErrorException(OrderExceptionEnum.ERR_LOCK_INTERRUPTED);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public void addToWaitingList(UUID courseId, UUID memberId) {
        String waitingKey = RedisConstants.WAITING_LIST_PREFIX + courseId;

        // Lua 스크립트를 사용하여 이미 대기열에 있는지, 정원이 찼는지 확인 후 추가
        // 리턴 코드: 1 (성공), -1 (중복), -2 (정원 초과)
        String luaScript = """
            local score = redis.call('ZSCORE', KEYS[1], ARGV[1])
            if score then
                return -1
            end
            local size = redis.call('ZCARD', KEYS[1])
            if size >= tonumber(ARGV[2]) then
                return -2
            end
            redis.call('ZADD', KEYS[1], ARGV[3], ARGV[1])
            return 1
            """;

        Long result = redisTemplate.execute(
                new org.springframework.data.redis.core.script.DefaultRedisScript<>(luaScript, Long.class),
                java.util.List.of(waitingKey),
                memberId.toString(),
                String.valueOf(OrderConstants.MAX_WAITING_SIZE),
                String.valueOf(System.currentTimeMillis())
        );

        if (result == null) {
            throw new ServiceErrorException(OrderExceptionEnum.ERR_LOCK_INTERRUPTED);
        }

        if (result == -1) {
            throw new ServiceErrorException(OrderExceptionEnum.ERR_DUPLICATE_ORDER);
        }

        if (result == -2) {
            throw new ServiceErrorException(OrderExceptionEnum.ERR_QUEUE_FULL);
        }
    }
}
