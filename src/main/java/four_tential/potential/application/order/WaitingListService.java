package four_tential.potential.application.order;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.OrderExceptionEnum;
import four_tential.potential.infra.redis.RedisConstants;
import four_tential.potential.infra.redis.annotation.DistributedLock;
import four_tential.potential.infra.sse.SseWaitingEventPublisher;
import four_tential.potential.infra.sse.SseWaitingRoomRepository;
import four_tential.potential.presentation.order.dto.WaitingRoomEventResponse;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.*;
import org.redisson.client.codec.StringCodec;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class WaitingListService {

    private final RedissonClient redissonClient;
    private final SseWaitingEventPublisher sseWaitingEventPublisher;
    private final SseWaitingRoomRepository sseWaitingRoomRepository;

    public WaitingListService(
            RedissonClient redissonClient,
            @Lazy SseWaitingEventPublisher sseWaitingEventPublisher,
            SseWaitingRoomRepository sseWaitingRoomRepository) {
        this.redissonClient = redissonClient;
        this.sseWaitingEventPublisher = sseWaitingEventPublisher;
        this.sseWaitingRoomRepository = sseWaitingRoomRepository;
    }

    /**
     * 잔여석 점유 시도 (Lua 스크립트 적용 버전)
     * 락 없이 Redis 내부에서 원자적으로 실행되어 성능을 극대화함
     */
    public boolean tryOccupyingSeat(UUID courseId, UUID memberId, int orderCount) {
        if (orderCount <= 0) {
            throw new ServiceErrorException(OrderExceptionEnum.ERR_INVALID_ORDER_COUNT);
        }

        String capacityKey = RedisConstants.COURSE_CAPACITY_PREFIX + courseId;
        String occupancyKey = RedisConstants.USER_COURSE_OCCUPANCY_PREFIX + courseId + ":" + memberId;
        String waitingKey = RedisConstants.WAITING_LIST_PREFIX + courseId;

        // Lua 스크립트: 
        // 1. 이미 점유 중인지 확인 (점유 중이면 일반 점유자의 경우 중복 에러 반환)
        // 2. 대기열에 있는지 확인 (있으면 실패/중복 에러를 위해 특정 값 반환)
        // 3. 대기열이 비어있고 잔여석이 충분하면 차감 후 점유 등록
        // 4. 그 외 상황(잔여석 부족 등)은 대기열 진입 필요를 알림
        String script = 
                "if redis.call('exists', KEYS[2]) == 1 then " +
                "  local val = redis.call('get', KEYS[2]) " +
                "  if val == ARGV[3] then " + // TOKEN_PROMOTED
                "    local cap = tonumber(redis.call('get', KEYS[1]) or '0') " +
                "    local req = tonumber(ARGV[2]) " +
                "    if cap >= req then " +
                "      redis.call('decrby', KEYS[1], req) " +
                "      redis.call('setex', KEYS[2], tonumber(ARGV[4]), ARGV[2]) " +
                "      return 1 " + // 승격자 점유 성공
                "    else " +
                "      return -1 " + // 승격자였으나 재고 부족 (삭제 필요)
                "    end " +
                "  end " +
                "  return -2 " + // 일반 점유자 이미 존재 -> 중복
                "end " +
                "if redis.call('zrank', KEYS[3], ARGV[1]) ~= false then return -2 end " + // 이미 대기 중
                "if redis.call('zcard', KEYS[3]) > 0 then return 0 end " + // 대기열 존재함
                "local cap = tonumber(redis.call('get', KEYS[1]) or '0') " +
                "local req = tonumber(ARGV[2]) " +
                "if cap >= req then " +
                "  redis.call('decrby', KEYS[1], req) " +
                "  redis.call('setex', KEYS[2], tonumber(ARGV[4]), ARGV[2]) " +
                "  return 1 " + // 즉시 점유 성공
                "end " +
                "return 0"; // 대기열 진입 필요

        RScript rScript = redissonClient.getScript(StringCodec.INSTANCE);
        Long result = rScript.eval(
                RScript.Mode.READ_WRITE,
                script,
                RScript.ReturnType.LONG,
                List.of(capacityKey, occupancyKey, waitingKey),
                memberId.toString(),
                String.valueOf(orderCount),
                OrderConstants.TOKEN_PROMOTED,
                String.valueOf(Duration.ofMinutes(OrderConstants.PENDING_ORDER_EXPIRATION_MINUTES).toSeconds())
        );

        if (result == 1) return true;
        if (result == -1) {
            redissonClient.getBucket(occupancyKey, StringCodec.INSTANCE).delete();
            promoteNextInWaitingList(courseId);
            return false;
        }
        if (result == -2) throw new ServiceErrorException(OrderExceptionEnum.ERR_DUPLICATE_ORDER);
        
        return false;
    }

    /**
     * 점유된 잔여석 롤백
     */
    @DistributedLock(key = "'order:course:' + #courseId")
    public void rollbackOccupiedSeat(UUID courseId, UUID memberId) {
        String occupancyKey = RedisConstants.USER_COURSE_OCCUPANCY_PREFIX + courseId + ":" + memberId;
        String capacityKey = RedisConstants.COURSE_CAPACITY_PREFIX + courseId;

        RBucket<String> occupancy = redissonClient.getBucket(occupancyKey, StringCodec.INSTANCE);
        RAtomicLong capacity = redissonClient.getAtomicLong(capacityKey);

        String reservedValue = occupancy.get();
        if (reservedValue != null) {
            try {
                if (!OrderConstants.TOKEN_PROMOTED.equals(reservedValue)) {
                    int reservedCount = Integer.parseInt(reservedValue);
                    capacity.addAndGet(reservedCount);
                }
                occupancy.delete();
                log.info("잔여석 롤백 완료: courseId={}, memberId={}", courseId, memberId);
                
                // 자리가 났으므로 승격 시도
                promoteNextInWaitingList(courseId);
            } catch (NumberFormatException e) {
                occupancy.delete();
            }
        }
    }

    /**
     * 대기열 다음 순번 승격 처리
     */
    private void promoteNextInWaitingList(UUID courseId) {
        String waitingKey = RedisConstants.WAITING_LIST_PREFIX + courseId;
        String capacityKey = RedisConstants.COURSE_CAPACITY_PREFIX + courseId;
        
        RScoredSortedSet<String> waitingList = redissonClient.getScoredSortedSet(waitingKey, StringCodec.INSTANCE);
        RAtomicLong capacity = redissonClient.getAtomicLong(capacityKey);

        // 대기자가 있고, 최소 1개 이상의 자리가 있을 때만 승격
        if (waitingList.isEmpty() || capacity.get() <= 0) {
            return;
        }

        String nextMemberIdStr = waitingList.first();
        if (nextMemberIdStr != null) {
            UUID nextMemberId = UUID.fromString(nextMemberIdStr);

            // 대기열 진입 시 저장했던 수량 정보 확인
            String countKey = RedisConstants.WAITING_ORDER_COUNT_PREFIX + courseId + ":" + nextMemberId;
            RBucket<String> countBucket = redissonClient.getBucket(countKey, StringCodec.INSTANCE);
            String countStr = countBucket.get();

            int requiredCount;
            try {
                requiredCount = (countStr != null) ? Integer.parseInt(countStr) : 1;
                if (requiredCount <= 0) {
                    throw new NumberFormatException(OrderExceptionEnum.ERR_INVALID_ORDER_COUNT.getMessage());
                }
            } catch (NumberFormatException e) {
                log.warn("유효하지 않은 대기 수량으로 대기자를 건너뜁니다. 이유: {}, courseId={}, memberId={}, raw={}", 
                        e.getMessage(), courseId, nextMemberId, countStr);
                waitingList.pollFirst();
                countBucket.delete();
                promoteNextInWaitingList(courseId);
                return;
            }

            if (capacity.get() < requiredCount) {
                log.info("다음 대기자 요구 수량({})이 잔여석({})보다 커서 승격을 보류합니다. courseId={}, memberId={}",
                        requiredCount, capacity.get(), courseId, nextMemberId);
                return;
            }

            // 조건이 맞으므로 대기열에서 완전히 꺼냄
            waitingList.pollFirst();
            countBucket.delete();

            // SSE 연결 확인 (연결이 없으면 다음 대기자로 넘어감)
            if (sseWaitingRoomRepository.find(courseId, nextMemberId).isEmpty()) {
                log.warn("승격 대상 SSE 연결 없음. 다음 대기자 시도: courseId={}, memberId={}", courseId, nextMemberId);
                promoteNextInWaitingList(courseId);
                return;
            }

            String occupancyKey = RedisConstants.USER_COURSE_OCCUPANCY_PREFIX + courseId + ":" + nextMemberId;
            RBucket<String> occupancy = redissonClient.getBucket(occupancyKey, StringCodec.INSTANCE);
            
            // 승격 우선권 부여
            occupancy.set(OrderConstants.TOKEN_PROMOTED, Duration.ofMinutes(OrderConstants.PROMOTION_EXPIRATION_MINUTES));
            
            log.info("대기열 유저 승격: courseId={}, memberId={}", courseId, nextMemberId);
            
            // SSE 전송
            sseWaitingEventPublisher.publish(courseId, nextMemberId, 
                    WaitingRoomEventResponse.promoted(courseId, nextMemberId));
        }
    }

    /**
     * 잔여석 점유 확정 (결제 완료 시 호출)
     */
    @DistributedLock(key = "'order:course:' + #courseId")
    public void completeOccupyingSeat(UUID courseId, UUID memberId) {
        String occupancyKey = RedisConstants.USER_COURSE_OCCUPANCY_PREFIX + courseId + ":" + memberId;
        RBucket<String> occupancy = redissonClient.getBucket(occupancyKey, StringCodec.INSTANCE);

        if (occupancy.isExists()) {
            occupancy.delete();
            log.info("잔여석 점유 확정 완료: courseId={}, memberId={}", courseId, memberId);
        }
    }

    /**
     * 대기열 진입 완료
     */
    public void addToWaitingList(UUID courseId, UUID memberId, int orderCount) {
        String waitingKey = RedisConstants.WAITING_LIST_PREFIX + courseId;
        String countKey = RedisConstants.WAITING_ORDER_COUNT_PREFIX + courseId + ":" + memberId;

        // Lua 스크립트 로직:
        // 1. 이미 대기열에 있는지 확인
        // 2. 대기열 크기 확인
        // 3. 대기열 진입 및 수량 정보 저장
        String script = 
                "if redis.call('zrank', KEYS[1], ARGV[1]) ~= false then return -1 end " +
                "if redis.call('zcard', KEYS[1]) >= tonumber(ARGV[3]) then return -2 end " +
                "redis.call('setex', KEYS[2], tonumber(ARGV[4]), ARGV[2]) " +
                "redis.call('zadd', KEYS[1], tonumber(ARGV[5]), ARGV[1]) " +
                "return 1";

        RScript rScript = redissonClient.getScript(StringCodec.INSTANCE);
        Long result = rScript.eval(
                RScript.Mode.READ_WRITE,
                script,
                RScript.ReturnType.LONG,
                List.of(waitingKey, countKey),
                memberId.toString(),
                String.valueOf(orderCount),
                String.valueOf(OrderConstants.MAX_WAITING_SIZE),
                String.valueOf(Duration.ofHours(1).toSeconds()),
                String.valueOf(System.currentTimeMillis())
        );

        if (result == -1) {
            throw new ServiceErrorException(OrderExceptionEnum.ERR_DUPLICATE_ORDER);
        }
        if (result == -2) {
            throw new ServiceErrorException(OrderExceptionEnum.ERR_QUEUE_FULL);
        }

        log.info("대기열 진입 완료: courseId={}, memberId={}, 수량={}", courseId, memberId, orderCount);
    }

    /**
     * 대기열 순번 조회
     */
    public Long getWaitingRank(UUID courseId, UUID memberId) {
        String waitingKey = RedisConstants.WAITING_LIST_PREFIX + courseId;
        RScoredSortedSet<String> waitingList = redissonClient.getScoredSortedSet(waitingKey, StringCodec.INSTANCE);
        Integer rank = waitingList.rank(memberId.toString());
        log.debug("[DEBUG] 대기열 순번 조회: key={}, memberId={}, rank={}", waitingKey, memberId, rank);
        return (rank != null) ? rank + 1L : null;
    }

    /**
     * 대기열 총 인원 조회
     */
    public int getWaitingListSize(UUID courseId) {
        String waitingKey = RedisConstants.WAITING_LIST_PREFIX + courseId;
        return redissonClient.getScoredSortedSet(waitingKey, StringCodec.INSTANCE).size();
    }

    /**
     * 대기열에서 수동 이탈
     */
    @DistributedLock(key = "'order:course:' + #courseId")
    public void removeFromWaitingList(UUID courseId, UUID memberId) {
        String waitingKey = RedisConstants.WAITING_LIST_PREFIX + courseId;
        RScoredSortedSet<String> waitingList = redissonClient.getScoredSortedSet(waitingKey, StringCodec.INSTANCE);
        boolean removed = waitingList.remove(memberId.toString());
        if (removed) {
            // 수량 정보도 함께 삭제
            String countKey = RedisConstants.WAITING_ORDER_COUNT_PREFIX + courseId + ":" + memberId;
            redissonClient.getBucket(countKey, StringCodec.INSTANCE).delete();
            log.info("대기열 이탈 완료: courseId={}, memberId={}", courseId, memberId);
        }
    }

    /**
     * 주문 취소/만료 시 잔여석 수량 복구 및 점유 정보 정리
     */
    @DistributedLock(key = "'order:course:' + #courseId")
    public void recoverCapacity(UUID courseId, UUID memberId, int orderCount) {
        if (orderCount <= 0) {
            throw new ServiceErrorException(OrderExceptionEnum.ERR_INVALID_ORDER_COUNT);
        }

        String capacityKey = RedisConstants.COURSE_CAPACITY_PREFIX + courseId;
        String occupancyKey = RedisConstants.USER_COURSE_OCCUPANCY_PREFIX + courseId + ":" + memberId;

        RAtomicLong capacity = redissonClient.getAtomicLong(capacityKey);
        RBucket<String> occupancy = redissonClient.getBucket(occupancyKey, StringCodec.INSTANCE);

        capacity.addAndGet(orderCount);
        occupancy.delete();
        
        log.info("잔여석 복구 및 점유 정보 정리 완료: courseId={}, memberId={}, 복구수량={}", courseId, memberId, orderCount);
        
        // 자리가 났으므로 승격 시도
        promoteNextInWaitingList(courseId);
    }

    /**
     * 특정 코스의 잔여 좌석 수치를 강제로 업데이트하고 대기열 승격을 시도
     */
    @DistributedLock(key = "'order:course:' + #courseId")
    public void updateCapacity(UUID courseId, long newCapacity) {
        long sanitizedCapacity = Math.max(0L, newCapacity);
        String capacityKey = RedisConstants.COURSE_CAPACITY_PREFIX + courseId;
        RAtomicLong capacity = redissonClient.getAtomicLong(capacityKey);

        long previousValue = capacity.get();
        capacity.set(sanitizedCapacity);

        log.info("잔여석 강제 업데이트 완료: courseId={}, 이전={}, 변경={}",
                courseId, previousValue, sanitizedCapacity);

        // 자리가 늘어난 경우(기존보다 수치가 커진 경우) 대기열 승격 시도
        if (sanitizedCapacity > previousValue) {
            promoteNextInWaitingList(courseId);
        }
    }

    /**
     * 잔여석 정보 초기화 여부 확인
     */
    public boolean isCapacityInitialized(UUID courseId) {
        String capacityKey = RedisConstants.COURSE_CAPACITY_PREFIX + courseId;
        return redissonClient.getAtomicLong(capacityKey).isExists();
    }

    /**
     * 특정 코스와 관련된 모든 Redis 데이터를 삭제합니다. (테스트 데이터 정리용)
     */
    public void clearCourseRedisData(UUID courseId) {
        String capacityKey = RedisConstants.COURSE_CAPACITY_PREFIX + courseId;
        String waitingKey = RedisConstants.WAITING_LIST_PREFIX + courseId;

        redissonClient.getAtomicLong(capacityKey).delete();
        redissonClient.getScoredSortedSet(waitingKey, StringCodec.INSTANCE).delete();

        // 멤버별 점유 및 대기 수량 정보 일괄 삭제 (패턴 매칭 활용)
        String occupancyPattern = RedisConstants.USER_COURSE_OCCUPANCY_PREFIX + courseId + ":*";
        String countPattern = RedisConstants.WAITING_ORDER_COUNT_PREFIX + courseId + ":*";

        redissonClient.getKeys().deleteByPattern(occupancyPattern);
        redissonClient.getKeys().deleteByPattern(countPattern);

        log.info("Redis 데이터 정리 완료: courseId={}", courseId);
    }

    /**
     * 현재 재고 관리가 진행 중인(초기화된) 코스 ID 목록을 반환합니다.
     */
    public List<UUID> getActiveCourseIds() {
        Iterable<String> keys = redissonClient.getKeys().getKeysByPattern(RedisConstants.COURSE_CAPACITY_PREFIX + "*");
        List<UUID> courseIds = new ArrayList<>();
        for (String key : keys) {
            String idStr = key.substring(RedisConstants.COURSE_CAPACITY_PREFIX.length());
            try {
                courseIds.add(UUID.fromString(idStr));
            } catch (IllegalArgumentException e) {
                // 무시
            }
        }
        return courseIds;
    }
}
