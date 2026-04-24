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
     * 잔여석 점유 시도
     */
    @DistributedLock(key = "'order:course:' + #courseId")
    public boolean tryOccupyingSeat(UUID courseId, UUID memberId, int orderCount) {
        if (orderCount <= 0) {
            throw new ServiceErrorException(OrderExceptionEnum.ERR_INVALID_ORDER_COUNT);
        }

        String occupancyKey = RedisConstants.USER_COURSE_OCCUPANCY_PREFIX + courseId + ":" + memberId;
        String capacityKey = RedisConstants.COURSE_CAPACITY_PREFIX + courseId;
        String waitingKey = RedisConstants.WAITING_LIST_PREFIX + courseId;

        RBucket<String> occupancy = redissonClient.getBucket(occupancyKey, StringCodec.INSTANCE);
        RScoredSortedSet<String> waitingList = redissonClient.getScoredSortedSet(waitingKey, StringCodec.INSTANCE);
        RAtomicLong capacity = redissonClient.getAtomicLong(capacityKey);

        // 이미 점유 중인 경우 (승격된 유저나 기존 점유자)
        if (occupancy.isExists()) {
            String val = occupancy.get();
            if (OrderConstants.TOKEN_PROMOTED.equals(val)) {
                // 승격된 유저가 실제 주문을 시도하는 시점 -> 수량 차감 및 점유 확정
                long currentCapacity = capacity.get();
                if (currentCapacity >= orderCount) {
                    capacity.addAndGet(-orderCount);
                    occupancy.set(String.valueOf(orderCount), Duration.ofMinutes(OrderConstants.PENDING_ORDER_EXPIRATION_MINUTES));
                    log.info("승격 유저의 실제 점유 성공: courseId={}, memberId={}, 수량={}", courseId, memberId, orderCount);
                    return true;
                } else {
                    // 승격되었으나 그사이 재고가 부족해진 경우 (동시성 방어)
                    log.warn("승격 유저 점유 실패: 재고 부족. courseId={}, memberId={}", courseId, memberId);
                    occupancy.delete();
                    
                    // 자리가 부족하여 실패했지만, 혹시라도 1개라도 남은 자리가 있다면 다음 대기자에게 기회를 줌
                    promoteNextInWaitingList(courseId);
                    return false;
                }
            }
            // 이미 수량이 세팅된 일반 점유 상태
            return true;
        }

        // 대기열에 본인이 들어있는 경우 (아직 대기 중)
        if (waitingList.contains(memberId.toString())) {
            throw new ServiceErrorException(OrderExceptionEnum.ERR_DUPLICATE_ORDER);
        }

        // 대기열에 다른 사람이 있는 경우 (무조건 대기열로)
        if (!waitingList.isEmpty()) {
            log.info("대기열 존재로 인해 대기열 진입 유도: courseId={}, memberId={}", courseId, memberId);
            return false;
        }

        // 대기열이 비어있으면 즉시 점유 시도
        long currentCapacity = capacity.get();
        if (currentCapacity >= orderCount) {
            capacity.addAndGet(-orderCount);
            occupancy.set(String.valueOf(orderCount), Duration.ofMinutes(OrderConstants.PENDING_ORDER_EXPIRATION_MINUTES));
            return true;
        }
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
            int requiredCount = (countStr != null) ? Integer.parseInt(countStr) : 1;

            if (capacity.get() < requiredCount) {
                log.info("승격 보류: 다음 대기자 요구 수량({})이 잔여석({})보다 커서 승격을 보류합니다. courseId={}, memberId={}", 
                        requiredCount, capacity.get(), courseId, nextMemberId);
                return;
            }

            // 조건이 맞으므로 대기열에서 완전히 꺼냄
            waitingList.pollFirst();
            countBucket.delete();

            // SSE 연결 확인 (연결이 없으면 다음 대기자로 넘어감)
            if (sseWaitingRoomRepository.find(courseId, nextMemberId).isEmpty()) {
                log.warn("승격 대상 SSE 연결 없음, 다음 대기자 시도: courseId={}, memberId={}", courseId, nextMemberId);
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
    @DistributedLock(key = "'order:course:' + #courseId")
    public void addToWaitingList(UUID courseId, UUID memberId, int orderCount) {
        String waitingKey = RedisConstants.WAITING_LIST_PREFIX + courseId;
        RScoredSortedSet<String> waitingList = redissonClient.getScoredSortedSet(waitingKey, StringCodec.INSTANCE);

        if (waitingList.contains(memberId.toString())) {
            throw new ServiceErrorException(OrderExceptionEnum.ERR_DUPLICATE_ORDER);
        }

        if (waitingList.size() >= OrderConstants.MAX_WAITING_SIZE) {
            throw new ServiceErrorException(OrderExceptionEnum.ERR_QUEUE_FULL);
        }

        // 수량 정보 저장
        String countKey = RedisConstants.WAITING_ORDER_COUNT_PREFIX + courseId + ":" + memberId;
        redissonClient.getBucket(countKey, StringCodec.INSTANCE).set(String.valueOf(orderCount));

        waitingList.add(System.currentTimeMillis(), memberId.toString());
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
