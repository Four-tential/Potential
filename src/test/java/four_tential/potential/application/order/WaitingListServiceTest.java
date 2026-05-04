package four_tential.potential.application.order;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.OrderExceptionEnum;
import four_tential.potential.infra.redis.RedisConstants;
import four_tential.potential.infra.sse.SseWaitingEventPublisher;
import four_tential.potential.infra.sse.SseWaitingRoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.*;
import org.redisson.client.codec.StringCodec;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WaitingListServiceTest {

    @Mock private RedissonClient redissonClient;
    @Mock private RScript rScript;
    @Mock private RBucket<String> occupancyBucket;
    @Mock private RBucket<String> countBucket;
    @Mock private RScoredSortedSet<String> waitingListSet;
    @Mock private RAtomicLong capacityAtomic;
    @Mock private SseWaitingEventPublisher sseWaitingEventPublisher;
    @Mock private SseWaitingRoomRepository sseWaitingRoomRepository;

    @InjectMocks private WaitingListService waitingListService;

    private final UUID courseId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // StringCodec을 사용하는 메서드 오버로딩에 대응
        lenient().doReturn(waitingListSet).when(redissonClient).getScoredSortedSet(anyString(), eq(StringCodec.INSTANCE));
        lenient().when(redissonClient.getAtomicLong(anyString())).thenReturn(capacityAtomic);
        lenient().when(redissonClient.getScript(StringCodec.INSTANCE)).thenReturn(rScript);
        
        // 키 패턴에 따라 다른 버킷 반환
        lenient().doReturn(occupancyBucket).when(redissonClient).getBucket(startsWith(RedisConstants.USER_COURSE_OCCUPANCY_PREFIX), eq(StringCodec.INSTANCE));
        lenient().doReturn(countBucket).when(redissonClient).getBucket(startsWith(RedisConstants.WAITING_ORDER_COUNT_PREFIX), eq(StringCodec.INSTANCE));
    }

    @Test
    @DisplayName("잔여석이 요청 수량보다 많고 대기열이 비어있으면 점유에 성공한다")
    void tryOccupyingSeat_success() {
        // given
        int orderCount = 2;
        lenient().doReturn(1L).when(rScript).eval(any(), anyString(), any(), anyList(), any(), any(), any(), any());

        // when
        boolean result = waitingListService.tryOccupyingSeat(courseId, memberId, orderCount);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("승격된 유저(PROMOTED)가 주문을 시도하면 재고 차감 후 점유에 성공한다")
    void tryOccupyingSeat_promoted_success() {
        // given
        lenient().doReturn(1L).when(rScript).eval(any(), anyString(), any(), anyList(), any(), any(), any(), any());

        // when
        boolean result = waitingListService.tryOccupyingSeat(courseId, memberId, 1);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("점유 시 수량이 0 이하이면 예외가 발생한다")
    void tryOccupyingSeat_fail_invalidCount() {
        // when & then
        assertThatThrownBy(() -> waitingListService.tryOccupyingSeat(courseId, memberId, 0))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(OrderExceptionEnum.ERR_INVALID_ORDER_COUNT.getMessage());

        assertThatThrownBy(() -> waitingListService.tryOccupyingSeat(courseId, memberId, -1))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(OrderExceptionEnum.ERR_INVALID_ORDER_COUNT.getMessage());
    }

    @Test
    @DisplayName("대기열에 사람이 있으면 잔여석이 있어도 점유에 실패하고 대기열로 유도한다")
    void tryOccupyingSeat_fail_due_to_existing_waiting_list() {
        // given
        lenient().doReturn(0L).when(rScript).eval(any(), anyString(), any(), anyList(), any(), any(), any(), any());

        // when
        boolean result = waitingListService.tryOccupyingSeat(courseId, memberId, 1);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("잔여석이 없으면 점유에 실패한다")
    void tryOccupyingSeat_fail_no_capacity() {
        // given
        lenient().doReturn(0L).when(rScript).eval(any(), anyString(), any(), anyList(), any(), any(), any(), any());

        // when
        boolean result = waitingListService.tryOccupyingSeat(courseId, memberId, 1);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("이미 점유 중인 유저가 재요청하면 true를 반환한다")
    void tryOccupyingSeat_already_occupied_returns_true() {
        // given
        lenient().doReturn(1L).when(rScript).eval(any(), anyString(), any(), anyList(), any(), any(), any(), any());

        // when
        boolean result = waitingListService.tryOccupyingSeat(courseId, memberId, 1);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("점유된 잔여석을 수량에 맞춰 성공적으로 롤백한다")
    void rollbackOccupiedSeat_success() {
        // given
        given(occupancyBucket.get()).willReturn("3");
        // 승격 로직을 위한 설정
        given(waitingListSet.isEmpty()).willReturn(false);
        given(capacityAtomic.get()).willReturn(1L);
        UUID nextId = UUID.randomUUID();
        given(waitingListSet.first()).willReturn(nextId.toString());
        given(waitingListSet.pollFirst()).willReturn(nextId.toString());
        given(sseWaitingRoomRepository.find(courseId, nextId)).willReturn(Optional.of(mock(SseEmitter.class)));
        // 대기자 수량은 1로 설정 (현재 잔여석 1이므로 승격 가능해야 함)
        given(countBucket.get()).willReturn("1");

        // when
        waitingListService.rollbackOccupiedSeat(courseId, memberId);

        // then
        verify(occupancyBucket).delete();
        verify(capacityAtomic).addAndGet(3);
        verify(sseWaitingEventPublisher).publish(eq(courseId), eq(nextId), any());
    }

    @Test
    @DisplayName("승격된 유저(PROMOTED)가 주문을 시도했으나 재고가 부족하면 점유 삭제 후 다음 대기자를 승격시킨다")
    void tryOccupyingSeat_promoted_fail_and_promote_next() {
        // given
        int orderCount = 2;
        lenient().doReturn(-1L).when(rScript).eval(any(), anyString(), any(), anyList(), any(), any(), any(), any());

        // 승격 로직을 위한 설정
        given(waitingListSet.isEmpty()).willReturn(false);
        given(capacityAtomic.get()).willReturn(1L);
        UUID nextId = UUID.randomUUID();
        given(waitingListSet.first()).willReturn(nextId.toString());
        given(waitingListSet.pollFirst()).willReturn(nextId.toString());
        given(sseWaitingRoomRepository.find(courseId, nextId)).willReturn(Optional.of(mock(SseEmitter.class)));
        given(countBucket.get()).willReturn("1");

        // when
        boolean result = waitingListService.tryOccupyingSeat(courseId, memberId, orderCount);

        // then
        assertThat(result).isFalse();
        verify(occupancyBucket).delete();
        verify(sseWaitingEventPublisher).publish(eq(courseId), eq(nextId), any());
    }


    @Test
    @DisplayName("승격 시 대상자의 SSE 연결이 없으면 고아 토큰을 생성하지 않고 다음 대기자를 승격시킨다")
    void promoteNextInWaitingList_skip_when_sse_disconnected() {
        // given
        given(waitingListSet.isEmpty()).willReturn(false);
        given(capacityAtomic.get()).willReturn(1L);
        
        UUID disconnectedMemberId = UUID.randomUUID();
        UUID nextMemberId = UUID.randomUUID();
        
        // 첫 번째 유저는 SSE 연결 없음, 두 번째 유저는 연결 있음
        given(waitingListSet.first()).willReturn(disconnectedMemberId.toString(), nextMemberId.toString());
        given(waitingListSet.pollFirst()).willReturn(disconnectedMemberId.toString(), nextMemberId.toString());
        given(sseWaitingRoomRepository.find(courseId, disconnectedMemberId)).willReturn(Optional.empty());
        given(sseWaitingRoomRepository.find(courseId, nextMemberId)).willReturn(Optional.of(mock(SseEmitter.class)));
        given(countBucket.get()).willReturn("1");

        // 유저별로 다른 버킷 반환하도록 설정 (연결된 유저만 필요)
        RBucket<String> nextOccupancyBucket = mock(RBucket.class);
        String nextKey = RedisConstants.USER_COURSE_OCCUPANCY_PREFIX + courseId + ":" + nextMemberId;
        doReturn(nextOccupancyBucket).when(redissonClient).getBucket(eq(nextKey), eq(StringCodec.INSTANCE));

        // when
        // private 메서드이므로 public 메서드를 통해 간접 호출 (예: recoverCapacity)
        waitingListService.recoverCapacity(courseId, memberId, 1);

        // then
        // 두 번째 유저(SSE 있음)가 승격되어야 함 (set() 호출됨)
        verify(nextOccupancyBucket).set(eq(OrderConstants.TOKEN_PROMOTED), any());
        verify(sseWaitingEventPublisher).publish(eq(courseId), eq(nextMemberId), any());
    }

    @Test
    @DisplayName("대기열에 성공적으로 추가된다")
    void addToWaitingList_success() {
        // given
        lenient().doReturn(1L).when(rScript).eval(any(), anyString(), any(), anyList(), any(), any(), any(), any(), any());

        // when
        waitingListService.addToWaitingList(courseId, memberId, 1);

        // then
        // Lua 스크립트 실행 확인
        verify(rScript).eval(eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.LONG), anyList(), 
                eq(memberId.toString()), eq("1"), eq(String.valueOf(OrderConstants.MAX_WAITING_SIZE)), anyString(), anyString());
    }

    @Test
    @DisplayName("취소 시 잔여석 수량을 복구하고 점유 정보를 삭제한다")
    void recoverCapacity_success() {
        // given
        int orderCount = 2;
        given(waitingListSet.isEmpty()).willReturn(false);
        given(capacityAtomic.get()).willReturn(1L);
        UUID nextId = UUID.randomUUID();
        given(waitingListSet.first()).willReturn(nextId.toString());
        given(waitingListSet.pollFirst()).willReturn(nextId.toString());
        given(sseWaitingRoomRepository.find(courseId, nextId)).willReturn(Optional.of(mock(SseEmitter.class)));
        given(countBucket.get()).willReturn("1");

        // when
        waitingListService.recoverCapacity(courseId, memberId, orderCount);

        // then
        verify(capacityAtomic).addAndGet(orderCount);
        verify(occupancyBucket).delete();
        verify(sseWaitingEventPublisher).publish(eq(courseId), eq(nextId), any());
    }

    @Test
    @DisplayName("대기열 순번을 조회하면 1부터 시작하는 순번을 반환한다")
    void getWaitingRank_success() {
        // given
        given(waitingListSet.rank(memberId.toString())).willReturn(0); // Redis rank는 0부터 시작

        // when
        Long rank = waitingListService.getWaitingRank(courseId, memberId);

        // then
        assertThat(rank).isEqualTo(1L);
    }

    @Test
    @DisplayName("대기열에 없으면 순번 조회 시 null을 반환한다")
    void getWaitingRank_null() {
        // given
        given(waitingListSet.rank(memberId.toString())).willReturn(null);

        // when
        Long rank = waitingListService.getWaitingRank(courseId, memberId);

        // then
        assertThat(rank).isNull();
    }

    @Test
    @DisplayName("대기열 총 인원을 성공적으로 조회한다")
    void getWaitingListSize_success() {
        // given
        given(waitingListSet.size()).willReturn(10);

        // when
        int size = waitingListService.getWaitingListSize(courseId);

        // then
        assertThat(size).isEqualTo(10);
    }

    @Test
    @DisplayName("대기열에서 성공적으로 이탈한다")
    void removeFromWaitingList_success() {
        // given
        given(waitingListSet.remove(memberId.toString())).willReturn(true);

        // when
        waitingListService.removeFromWaitingList(courseId, memberId);

        // then
        verify(waitingListSet).remove(memberId.toString());
    }

    @Test
    @DisplayName("잔여석 수치를 강제로 업데이트하고 수치가 증가하면 대기열 승격을 시도한다")
    void updateCapacity_increase_triggers_promotion() {
        // given
        long previousCapacity = 5L;
        long newCapacity = 10L;
        given(capacityAtomic.get()).willReturn(previousCapacity);
        
        // 승격 로직을 위한 설정
        given(waitingListSet.isEmpty()).willReturn(false);
        when(capacityAtomic.get()).thenReturn(previousCapacity, newCapacity);
        UUID nextId = UUID.randomUUID();
        given(waitingListSet.first()).willReturn(nextId.toString());
        given(waitingListSet.pollFirst()).willReturn(nextId.toString());
        given(sseWaitingRoomRepository.find(courseId, nextId)).willReturn(Optional.of(mock(SseEmitter.class)));
        given(countBucket.get()).willReturn("1");

        // when
        waitingListService.updateCapacity(courseId, newCapacity);

        // then
        verify(capacityAtomic).set(newCapacity);
        verify(sseWaitingEventPublisher).publish(eq(courseId), eq(nextId), any());
    }

    @Test
    @DisplayName("잔여석 수치가 감소하면 대기열 승격을 시도하지 않는다")
    void updateCapacity_decrease_no_promotion() {
        // given
        long previousCapacity = 10L;
        long newCapacity = 5L;
        given(capacityAtomic.get()).willReturn(previousCapacity);

        // when
        waitingListService.updateCapacity(courseId, newCapacity);

        // then
        verify(capacityAtomic).set(newCapacity);
        verify(sseWaitingEventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    @DisplayName("잔여석 수치가 이전과 같으면 대기열 승격을 시도하지 않는다")
    void updateCapacity_equal_no_promotion() {
        // given
        long previousCapacity = 10L;
        long newCapacity = 10L;
        given(capacityAtomic.get()).willReturn(previousCapacity);

        // when
        waitingListService.updateCapacity(courseId, newCapacity);

        // then
        verify(capacityAtomic).set(newCapacity);
        verify(sseWaitingEventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    @DisplayName("잔여석 점유 확정 시 점유 정보가 삭제된다")
    void completeOccupyingSeat_success() {
        // given
        given(occupancyBucket.isExists()).willReturn(true);

        // when
        waitingListService.completeOccupyingSeat(courseId, memberId);

        // then
        verify(occupancyBucket).delete();
    }

    @Test
    @DisplayName("잔여석 정보 초기화 여부를 확인한다")
    void isCapacityInitialized_success() {
        // given
        given(capacityAtomic.isExists()).willReturn(true);

        // when
        boolean result = waitingListService.isCapacityInitialized(courseId);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("현재 활성화된 코스 ID 목록을 조회한다")
    void getActiveCourseIds_success() {
        // given
        RKeys rKeys = mock(RKeys.class);
        given(redissonClient.getKeys()).willReturn(rKeys);
        String courseKey = RedisConstants.COURSE_CAPACITY_PREFIX + courseId;
        given(rKeys.getKeysByPattern(anyString())).willReturn(List.of(courseKey));

        // when
        var result = waitingListService.getActiveCourseIds();

        // then
        assertThat(result).containsExactly(courseId);
    }

    @Test
    @DisplayName("대기열 수량이 숫자가 아니면 해당 대기자를 제거하고 다음 대기자를 승격시킨다")
    void promoteNextInWaitingList_invalid_count_parsing_fail() {
        // given
        given(waitingListSet.isEmpty()).willReturn(false);
        given(capacityAtomic.get()).willReturn(1L);

        UUID invalidMemberId = UUID.randomUUID();
        UUID nextMemberId = UUID.randomUUID();

        // 첫 번째 시도: invalidMemberId (Parsing Fail)
        // 두 번째 시도: nextMemberId (Success)
        given(waitingListSet.first()).willReturn(invalidMemberId.toString(), nextMemberId.toString());
        given(waitingListSet.pollFirst()).willReturn(invalidMemberId.toString(), nextMemberId.toString());
        
        // 첫 번째 유저의 버킷은 "INVALID" 반환
        RBucket<String> invalidCountBucket = mock(RBucket.class);
        given(invalidCountBucket.get()).willReturn("INVALID");
        String invalidCountKey = RedisConstants.WAITING_ORDER_COUNT_PREFIX + courseId + ":" + invalidMemberId;
        doReturn(invalidCountBucket).when(redissonClient).getBucket(eq(invalidCountKey), eq(StringCodec.INSTANCE));

        // 두 번째 유저의 버킷은 "1" 반환
        RBucket<String> nextCountBucket = mock(RBucket.class);
        given(nextCountBucket.get()).willReturn("1");
        String nextCountKey = RedisConstants.WAITING_ORDER_COUNT_PREFIX + courseId + ":" + nextMemberId;
        doReturn(nextCountBucket).when(redissonClient).getBucket(eq(nextCountKey), eq(StringCodec.INSTANCE));
        
        given(sseWaitingRoomRepository.find(courseId, nextMemberId)).willReturn(Optional.of(mock(SseEmitter.class)));

        // when
        waitingListService.recoverCapacity(courseId, memberId, 1);

        // then
        verify(waitingListSet, times(2)).pollFirst(); // 잘못된 유저 제거 + 성공 유저 제거
        verify(sseWaitingEventPublisher).publish(eq(courseId), eq(nextMemberId), any());
    }

    @Test
    @DisplayName("대기열 수량이 0 이하이면 해당 대기자를 제거하고 다음 대기자를 승격시킨다")
    void promoteNextInWaitingList_invalid_count_zero_or_less() {
        // given
        given(waitingListSet.isEmpty()).willReturn(false);
        given(capacityAtomic.get()).willReturn(1L);

        UUID invalidMemberId = UUID.randomUUID();
        given(waitingListSet.first()).willReturn(invalidMemberId.toString());
        given(waitingListSet.pollFirst()).willReturn(invalidMemberId.toString());
        
        // countStr이 "0"
        RBucket<String> invalidCountBucket = mock(RBucket.class);
        given(invalidCountBucket.get()).willReturn("0");
        String countKey = RedisConstants.WAITING_ORDER_COUNT_PREFIX + courseId + ":" + invalidMemberId;
        doReturn(invalidCountBucket).when(redissonClient).getBucket(eq(countKey), eq(StringCodec.INSTANCE));
        
        // 재귀 호출 시 다음 대기자가 없음을 알리기 위해
        when(waitingListSet.isEmpty()).thenReturn(false, true);

        // when
        waitingListService.recoverCapacity(courseId, memberId, 1);

        // then
        verify(waitingListSet).pollFirst();
        verify(invalidCountBucket).delete();
        verify(sseWaitingEventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    @DisplayName("잔여석이 대기자의 요구 수량보다 적으면 승격을 보류한다")
    void promoteNextInWaitingList_insufficient_capacity_for_required_count() {
        // given
        given(waitingListSet.isEmpty()).willReturn(false);
        given(capacityAtomic.get()).willReturn(2L); // 잔여석 2

        UUID nextMemberId = UUID.randomUUID();
        given(waitingListSet.first()).willReturn(nextMemberId.toString());
        
        // 요구 수량 3
        RBucket<String> requiredCountBucket = mock(RBucket.class);
        given(requiredCountBucket.get()).willReturn("3");
        String countKey = RedisConstants.WAITING_ORDER_COUNT_PREFIX + courseId + ":" + nextMemberId;
        doReturn(requiredCountBucket).when(redissonClient).getBucket(eq(countKey), eq(StringCodec.INSTANCE));

        // when
        waitingListService.recoverCapacity(courseId, memberId, 1);

        // then
        verify(waitingListSet, never()).pollFirst(); // 승격되지 않음
        verify(sseWaitingEventPublisher, never()).publish(any(), any(), any());
    }
}
