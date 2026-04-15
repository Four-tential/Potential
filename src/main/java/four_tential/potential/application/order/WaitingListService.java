package four_tential.potential.application.order;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.OrderExceptionEnum;
import four_tential.potential.infra.redis.RedisConstants;
import four_tential.potential.infra.redis.annotation.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.*;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaitingListService {

    private final RedissonClient redissonClient;

    /**
     * 잔여석 점유 시도
     */
    @DistributedLock(key = "'order:course:' + #p0")
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
            occupancy.set(String.valueOf(orderCount), 600, TimeUnit.SECONDS);
            return true;
        }
        return false;
    }

    /**
     * 점유된 잔여석 롤백
     */
    @DistributedLock(key = "'order:course:' + #p0")
    public void rollbackOccupiedSeat(UUID courseId, UUID memberId) {
        String occupancyKey = RedisConstants.USER_COURSE_OCCUPANCY_PREFIX + courseId + ":" + memberId;
        String capacityKey = RedisConstants.COURSE_CAPACITY_PREFIX + courseId;

        RBucket<String> occupancy = redissonClient.getBucket(occupancyKey);
        RAtomicLong capacity = redissonClient.getAtomicLong(capacityKey);

        if (occupancy.isExists()) {
            try {
                int reservedCount = Integer.parseInt(occupancy.get());
                occupancy.delete();
                capacity.addAndGet(reservedCount);
                log.info("잔여석 롤백 완료: courseId={}, memberId={}, 복구수량={}", courseId, memberId, reservedCount);
            } catch (NumberFormatException e) {
                log.error("잔여석 롤백 실패: 잘못된 점유 데이터 형식. courseId={}, memberId={}", courseId, memberId);
                occupancy.delete();
            }
        }
    }

    /**
     * 대기열 추가
     */
    @DistributedLock(key = "'order:course:' + #p0")
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
