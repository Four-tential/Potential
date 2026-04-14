package four_tential.potential.application.order;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.OrderExceptionEnum;
import four_tential.potential.domain.order.WaitingListRepository;
import four_tential.potential.infra.redis.RedisConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class
WaitingListService {

    private final WaitingListRepository waitingListRepository;
    private final StringRedisTemplate redisTemplate;

    public boolean tryOccupyingStock(UUID courseId, UUID memberId) {
        String occupancyKey = RedisConstants.USER_COURSE_OCCUPANCY_PREFIX + courseId + ":" + memberId;
        String stockKey = RedisConstants.COURSE_STOCK_PREFIX + courseId;

        // Lua 스크립트를 사용하여 중복 점유 확인 및 재고 차감을 원자적으로 처리
        // 리턴 코드: 1 (성공), -1 (중복), 0 (재고 부족)
        String luaScript =
                "if redis.call('exists', KEYS[1]) == 1 then " +
                "  return -1 " +
                "end " +
                "local stock = tonumber(redis.call('get', KEYS[2])) " +
                "if stock and stock > 0 then " +
                "  redis.call('decr', KEYS[2]) " +
                "  redis.call('setex', KEYS[1], 600, 'OCCUPIED') " +
                "  return 1 " +
                "else " +
                "  return 0 " +
                "end";

        Long result = redisTemplate.execute(
                new org.springframework.data.redis.core.script.DefaultRedisScript<>(luaScript, Long.class),
                java.util.List.of(occupancyKey, stockKey)
        );

        if (result == null) {
            throw new ServiceErrorException(OrderExceptionEnum.ERR_SYSTEM_ERROR);
        }

        if (result == -1) {
            throw new ServiceErrorException(OrderExceptionEnum.ERR_DUPLICATE_ORDER);
        }

        return result == 1;
    }

    public void rollbackOccupiedStock(UUID courseId, UUID memberId) {
        String occupancyKey = RedisConstants.USER_COURSE_OCCUPANCY_PREFIX + courseId + ":" + memberId;
        String stockKey = RedisConstants.COURSE_STOCK_PREFIX + courseId;

        // Lua 스크립트를 사용하여 점유 확인, 삭제, 재고 복구를 원자적으로 처리
        // 리턴 코드: 변경된 재고 수량 (성공), -1 (점유 데이터 없음)
        String luaScript =
                "if redis.call('exists', KEYS[1]) == 1 then " +
                "  redis.call('del', KEYS[1]) " +
                "  return redis.call('incr', KEYS[2]) " +
                "else " +
                "  return -1 " +
                "end";

        Long result = redisTemplate.execute(
                new org.springframework.data.redis.core.script.DefaultRedisScript<>(luaScript, Long.class),
                java.util.List.of(occupancyKey, stockKey)
        );

        if (result == null || result == -1) {
            log.warn("재고 롤백 실패: 점유 데이터를 찾을 수 없습니다. courseId={}, memberId={}", courseId, memberId);
        } else {
            log.info("재고 롤백 성공: courseId={}, memberId={}, 현재 재고={}", courseId, memberId, result);
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
            throw new ServiceErrorException(OrderExceptionEnum.ERR_SYSTEM_ERROR);
        }

        if (result == -1) {
            throw new ServiceErrorException(OrderExceptionEnum.ERR_DUPLICATE_ORDER);
        }

        if (result == -2) {
            throw new ServiceErrorException(OrderExceptionEnum.ERR_QUEUE_FULL);
        }
    }
}
