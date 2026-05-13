package four_tential.potential.infra.sse;

import four_tential.potential.application.order.WaitingListService;
import four_tential.potential.presentation.order.dto.WaitingRoomEventResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SseWaitingEventPublisherTest {

    @Mock
    private SseWaitingRoomRepository sseWaitingRoomRepository;

    @Mock
    private WaitingListService waitingListService;

    @Mock
    private SseEmitter sseEmitter;

    @InjectMocks
    private SseWaitingEventPublisher sseWaitingEventPublisher;

    private final UUID courseId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        given(sseWaitingRoomRepository.find(courseId, memberId)).willReturn(Optional.of(sseEmitter));
    }

    @Test
    @DisplayName("대기열 상태를 성공적으로 전송한다")
    void publish_success() throws IOException {
        // given
        WaitingRoomEventResponse response = WaitingRoomEventResponse.waiting(courseId, memberId, 1L, 10);

        // when
        sseWaitingEventPublisher.publish(courseId, memberId, response);

        // then
        verify(sseEmitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(sseWaitingRoomRepository, never()).delete(any(), any(), any());
    }

    @Test
    @DisplayName("승격 상태 전송 시 연결을 종료하고 저장소에서 삭제한다")
    void publish_promoted_success() throws IOException {
        // given
        WaitingRoomEventResponse response = WaitingRoomEventResponse.promoted(courseId, memberId);

        // when
        sseWaitingEventPublisher.publish(courseId, memberId, response);

        // then
        verify(sseEmitter).complete();
        verify(sseWaitingRoomRepository).delete(courseId, memberId, sseEmitter);
    }

    @Test
    @DisplayName("전송 실패(IOException) 시 자동 이탈 처리한다")
    void publish_fail_handleDisconnect() throws IOException {
        // given
        WaitingRoomEventResponse response = WaitingRoomEventResponse.waiting(courseId, memberId, 1L, 10);
        doThrow(IOException.class).when(sseEmitter).send(any(SseEmitter.SseEventBuilder.class));

        // when
        sseWaitingEventPublisher.publish(courseId, memberId, response);

        // then
        verify(sseWaitingRoomRepository).delete(courseId, memberId, sseEmitter);
        verify(waitingListService).removeFromWaitingList(courseId, memberId);
        verify(sseEmitter).complete();
    }

    @Test
    @DisplayName("하트비트를 성공적으로 전송한다")
    void sendHeartbeat_success() throws IOException {
        // when
        sseWaitingEventPublisher.sendHeartbeat(courseId, memberId);

        // then
        verify(sseEmitter).send(any(SseEmitter.SseEventBuilder.class));
    }
}
