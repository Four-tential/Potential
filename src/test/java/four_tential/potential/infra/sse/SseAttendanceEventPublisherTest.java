package four_tential.potential.infra.sse;

import four_tential.potential.domain.attendance.Attendance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SseAttendanceEventPublisherTest {

    @Mock
    private SseEmitterRepository sseEmitterRepository;

    @InjectMocks
    private SseAttendanceEventPublisher sseAttendanceEventPublisher;

    private static final UUID COURSE_ID = UUID.randomUUID();
    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID ORDER_ID  = UUID.randomUUID();
    private static final String QR_TOKEN = "test-qr-token";

    @Nested
    @DisplayName("publish() - SSE 이벤트 전송")
    class PublishTest {

        @Test
        @DisplayName("SSE 연결이 없으면 이벤트를 전송하지 않는다")
        void publish_noConnection() {
            // given
            Attendance attendance = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID);
            attendance.attend(QR_TOKEN);
            when(sseEmitterRepository.findByCourseId(COURSE_ID)).thenReturn(Optional.empty());

            // when
            sseAttendanceEventPublisher.publish(COURSE_ID, attendance);

            // then
            verify(sseEmitterRepository).findByCourseId(COURSE_ID);
            verify(sseEmitterRepository, never()).delete(any());
        }

        @Test
        @DisplayName("SSE 연결이 있으면 attendance 이벤트를 전송한다")
        void publish_withConnection() throws Exception {
            // given
            Attendance attendance = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID);
            attendance.attend(QR_TOKEN);
            SseEmitter mockEmitter = mock(SseEmitter.class);
            when(sseEmitterRepository.findByCourseId(COURSE_ID)).thenReturn(Optional.of(mockEmitter));

            // when
            sseAttendanceEventPublisher.publish(COURSE_ID, attendance);

            // then
            verify(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));
        }

        @Test
        @DisplayName("IOException 발생 시 emitter 를 정리한다")
        void publish_IOException_cleansUpEmitter() throws Exception {
            // given
            Attendance attendance = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID);
            attendance.attend(QR_TOKEN);
            SseEmitter mockEmitter = mock(SseEmitter.class);
            when(sseEmitterRepository.findByCourseId(COURSE_ID)).thenReturn(Optional.of(mockEmitter));
            doThrow(new IOException("연결 끊김"))
                    .when(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));

            // when
            sseAttendanceEventPublisher.publish(COURSE_ID, attendance);

            // then
            verify(sseEmitterRepository).delete(COURSE_ID);
            verify(mockEmitter).complete();
        }

        @Test
        @DisplayName("Exception 발생 시 emitter 를 에러 종료한다")
        void publish_Exception_completesWithError() throws Exception {
            // given
            Attendance attendance = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID);
            attendance.attend(QR_TOKEN);
            SseEmitter mockEmitter = mock(SseEmitter.class);
            when(sseEmitterRepository.findByCourseId(COURSE_ID)).thenReturn(Optional.of(mockEmitter));
            doThrow(new RuntimeException("알 수 없는 에러"))
                    .when(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));

            // when
            sseAttendanceEventPublisher.publish(COURSE_ID, attendance);

            // then
            verify(sseEmitterRepository).delete(COURSE_ID);
            verify(mockEmitter).completeWithError(any(RuntimeException.class));
        }
    }
}