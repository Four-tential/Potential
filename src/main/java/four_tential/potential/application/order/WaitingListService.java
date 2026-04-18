package four_tential.potential.application.order;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.OrderExceptionEnum;
import four_tential.potential.infra.redis.RedisConstants;
import four_tential.potential.infra.redis.annotation.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.*;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaitingListService {

    private final RedissonClient redissonClient;

    /**
     * 잔여석 점유 시도
     */
    @DistributedLock(key = "'order:course:' + #courseId")
    public boolean tryOccupyingSeat(UUID courseId, UUID memberId, int orderCount) {
        String occupancyKey = RedisConstants.USER_COURSE_OCCUPANCY_PREFIX + courseId + ":" + memberId;
        String capacityKey = RedisConstants.COURSE_CAPACITY_PREFIX + courseId;
        String waitingKey = RedisConstants.WAITING_LIST_PREFIX + courseId;

        RBucket<String> occupancy = redissonClient.getBucket(occupancyKey);
        RScoredSortedSet<String> waitingList = redissonClient.getScoredSortedSet(waitingKey);
        RAtomicLong capacity = redissonClient.getAtomicLong(capacityKey);

        if (occupancy.isExists() || waitingList.contains(memberId.toString())) {
            throw new ServiceErrorException(OrderExceptionEnum.ERR_DUPLICATE_ORDER);
        }

        if (!waitingList.isEmpty()) {
            log.info("대기열 존재로 인해 대기열 진입 유도: courseId={}, memberId={}", courseId, memberId);
            return false;
        }

        long currentCapacity = capacity.get();
        if (currentCapacity >= orderCount) {
            capacity.addAndGet(-orderCount);
            occupancy.set(String.valueOf(orderCount), Duration.ofSeconds(600));
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

        RBucket<String> occupancy = redissonClient.getBucket(occupancyKey);
        RAtomicLong capacity = redissonClient.getAtomicLong(capacityKey);

        String reservedValue = occupancy.get();
        if (reservedValue != null) {
            try {
                int reservedCount = Integer.parseInt(reservedValue);
                occupancy.delete();
                capacity.addAndGet(reservedCount);
                log.info("잔여석 롤백 완료: courseId={}, memberId={}, 복구수량={}", courseId, memberId, reservedCount);
            } catch (NumberFormatException e) {
                log.error("잔여석 롤백 실패: 잘못된 점유 데이터 형식. courseId={}, memberId={}", courseId, memberId);
                occupancy.delete();
            }
        } else {
            log.warn("잔여석 롤백 건너뜀: 점유 정보가 이미 없거나 만료됨. courseId={}, memberId={}", courseId, memberId);
        }
    }

    /**
     * 잔여석 점유 확정 (결제 완료 시 호출)
     */
    @DistributedLock(key = "'order:course:' + #courseId")
    public void completeOccupyingSeat(UUID courseId, UUID memberId) {
        String occupancyKey = RedisConstants.USER_COURSE_OCCUPANCY_PREFIX + courseId + ":" + memberId;
        RBucket<String> occupancy = redissonClient.getBucket(occupancyKey);

        if (occupancy.isExists()) {
            occupancy.delete();
            log.info("잔여석 점유 확정 및 선점 정보 삭제 완료: courseId={}, memberId={}", courseId, memberId);
        } else {
            log.info("잔여석 점유 확정 건너뜀: 선점 정보가 이미 삭제되었거나 만료됨. courseId={}, memberId={}", courseId, memberId);
        }
    }

    /**
     * 대기열 추가
     */
    @DistributedLock(key = "'order:course:' + #courseId")
    public void addToWaitingList(UUID courseId, UUID memberId) {
        String waitingKey = RedisConstants.WAITING_LIST_PREFIX + courseId;
        RScoredSortedSet<String> waitingList = redissonClient.getScoredSortedSet(waitingKey);

        if (waitingList.contains(memberId.toString())) {
            throw new ServiceErrorException(OrderExceptionEnum.ERR_DUPLICATE_ORDER);
        }

        if (waitingList.size() >= OrderConstants.MAX_WAITING_SIZE) {
            throw new ServiceErrorException(OrderExceptionEnum.ERR_QUEUE_FULL);
        }

        waitingList.add(System.currentTimeMillis(), memberId.toString());
        log.info("대기열 진입 완료: courseId={}, memberId={}", courseId, memberId);
    }
}
