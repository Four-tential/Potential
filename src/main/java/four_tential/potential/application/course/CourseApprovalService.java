package four_tential.potential.application.course;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.course.course.CourseStatus;
import four_tential.potential.domain.course.course_approval_history.CourseApprovalAction;
import four_tential.potential.domain.course.course_approval_history.CourseApprovalHistory;
import four_tential.potential.domain.course.course_approval_history.CourseApprovalHistoryRepository;
import four_tential.potential.presentation.course.model.request.CourseRequestActionRequest;
import four_tential.potential.presentation.course.model.response.CourseRequestActionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static four_tential.potential.common.exception.domain.CourseExceptionEnum.*;

@Service
@RequiredArgsConstructor
public class CourseApprovalService {

    private final CourseRepository courseRepository;
    private final CourseApprovalHistoryRepository courseApprovalHistoryRepository;

    @Transactional
    public CourseRequestActionResponse courseRequest(UUID courseId, CourseRequestActionRequest request) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_COURSE));

        if (course.getStatus() != CourseStatus.PREPARATION) {
            throw new ServiceErrorException(ERR_COURSE_NOT_IN_PREPARATION);
        }

        if (request.action() == CourseApprovalAction.REJECT) {
            if (request.rejectReason() == null || request.rejectReason().isBlank()) {
                throw new ServiceErrorException(ERR_REJECT_REASON_REQUIRED);
            }
            course.reject(request.rejectReason());
            courseApprovalHistoryRepository.save(
                    CourseApprovalHistory.register(courseId, CourseApprovalAction.REJECT, request.rejectReason())
            );
        } else if (request.action() == CourseApprovalAction.APPROVE) {
            course.confirm();
            courseApprovalHistoryRepository.save(
                    CourseApprovalHistory.register(courseId, CourseApprovalAction.APPROVE, null)
            );
        } else {
            throw new ServiceErrorException(ERR_INVALID_COURSE_APPROVAL_ACTION);
        }

        return CourseRequestActionResponse.from(course);
    }
}
