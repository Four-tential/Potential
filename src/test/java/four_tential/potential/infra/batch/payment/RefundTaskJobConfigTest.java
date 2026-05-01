package four_tential.potential.infra.batch.payment;

import four_tential.potential.application.payment.RefundFacade;
import four_tential.potential.domain.payment.entity.RefundTask;
import four_tential.potential.domain.payment.enums.RefundTaskStatus;
import four_tential.potential.domain.payment.repository.RefundTaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.infrastructure.item.Chunk;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefundTaskJobConfigTest {

    @InjectMocks
    private RefundTaskJobConfig refundTaskJobConfig;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private RefundTaskRepository refundTaskRepository;

    @Mock
    private RefundFacade refundFacade;

    @Test
    @DisplayName("refundTaskJob bean을 생성한다")
    void refundTaskJob_creates_job() {
        Job job = refundTaskJobConfig.refundTaskJob();

        assertThat(job.getName()).isEqualTo("refundTaskJob");
    }

    @Test
    @DisplayName("refundTaskStep bean을 생성한다")
    void refundTaskStep_creates_step() {
        Step step = refundTaskJobConfig.refundTaskStep();

        assertThat(step.getName()).isEqualTo("refundTaskStep");
    }

    @Test
    @DisplayName("reader는 PENDING refund_task만 읽는다")
    void refundTaskReader_reads_pending_tasks() throws Exception {
        RefundTask first = RefundTask.pending(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        RefundTask second = RefundTask.pending(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        given(refundTaskRepository.findByStatus(RefundTaskStatus.PENDING)).willReturn(List.of(first, second));

        var reader = refundTaskJobConfig.refundTaskReader();

        assertThat(reader.read()).isEqualTo(first);
        assertThat(reader.read()).isEqualTo(second);
        assertThat(reader.read()).isNull();
    }

    @Test
    @DisplayName("processor는 환불 성공 시 task를 DONE 처리한다")
    void refundTaskProcessor_marks_done_when_refund_succeeds() throws Exception {
        UUID orderId = UUID.randomUUID();
        RefundTask task = RefundTask.pending(UUID.randomUUID(), orderId, UUID.randomUUID(), UUID.randomUUID());

        var result = refundTaskJobConfig.refundTaskProcessor().process(task);

        verify(refundFacade).processInstructorRefundTask(orderId);
        assertThat(result.getStatus()).isEqualTo(RefundTaskStatus.DONE);
        assertThat(result.getFailReason()).isNull();
    }

    @Test
    @DisplayName("processor는 환불 실패 시 task를 FAILED 처리한다")
    void refundTaskProcessor_marks_failed_when_refund_fails() throws Exception {
        UUID orderId = UUID.randomUUID();
        RefundTask task = RefundTask.pending(UUID.randomUUID(), orderId, UUID.randomUUID(), UUID.randomUUID());
        doThrow(new RuntimeException("refund fail"))
                .when(refundFacade).processInstructorRefundTask(orderId);

        var result = refundTaskJobConfig.refundTaskProcessor().process(task);

        verify(refundFacade).processInstructorRefundTask(orderId);
        assertThat(result.getStatus()).isEqualTo(RefundTaskStatus.FAILED);
        assertThat(result.getFailReason()).contains("refund fail");
    }

    @Test
    @DisplayName("writer는 처리된 refund_task를 저장한다")
    void refundTaskWriter_saves_tasks() throws Exception {
        RefundTask first = RefundTask.pending(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        RefundTask second = RefundTask.pending(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        refundTaskJobConfig.refundTaskWriter().write(new Chunk<>(List.of(first, second)));

        verify(refundTaskRepository).saveAll(eq(List.of(first, second)));
    }
}
