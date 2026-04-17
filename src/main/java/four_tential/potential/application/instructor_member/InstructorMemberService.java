package four_tential.potential.application.instructor_member;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.course.course_category.CourseCategoryRepository;
import four_tential.potential.domain.member.instructor_member.InstructorMember;
import four_tential.potential.domain.member.instructor_member.InstructorMemberRepository;
import four_tential.potential.domain.member.instructor_member.InstructorMemberStatus;
import four_tential.potential.domain.member.member.Member;
import four_tential.potential.domain.member.member.MemberRepository;
import four_tential.potential.domain.member.member.MemberRole;
import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.presentation.instructor_member.model.request.ApplyInstructorRequest;
import four_tential.potential.presentation.instructor_member.model.request.InstructorAction;
import four_tential.potential.presentation.instructor_member.model.request.InstructorActionRequest;
import four_tential.potential.presentation.instructor_member.model.response.ApplyInstructorResponse;
import four_tential.potential.presentation.instructor_member.model.response.InstructorActionResponse;
import four_tential.potential.presentation.instructor_member.model.response.InstructorApplicationDetail;
import four_tential.potential.presentation.instructor_member.model.response.InstructorApplicationItem;
import four_tential.potential.presentation.instructor_member.model.response.MyInstructorApplicationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static four_tential.potential.common.exception.domain.CourseExceptionEnum.ERR_NOT_FOUND_CATEGORY;
import static four_tential.potential.common.exception.domain.MemberExceptionEnum.*;

@Service
@RequiredArgsConstructor
public class InstructorMemberService {

    private final MemberRepository memberRepository;
    private final InstructorMemberRepository instructorMemberRepository;
    private final CourseCategoryRepository courseCategoryRepository;

    @Transactional
    public ApplyInstructorResponse applyInstructor(UUID memberId, ApplyInstructorRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_MEMBER));

        // 이미 강사인 경우
        if (member.getRole() == MemberRole.ROLE_INSTRUCTOR) {
            throw new ServiceErrorException(ERR_ALREADY_INSTRUCTOR);
        }

        // 카테고리 유효성 검증
        if (!courseCategoryRepository.existsByCode(request.categoryCode())) {
            throw new ServiceErrorException(ERR_NOT_FOUND_CATEGORY);
        }

        Optional<InstructorMember> existingApplication = instructorMemberRepository.findByMemberId(memberId);

        InstructorMember instructorMember;
        if (existingApplication.isPresent()) {
            InstructorMember existing = existingApplication.get();

            // PENDING 상태는 재신청 불가
            if (existing.getStatus() == InstructorMemberStatus.PENDING) {
                throw new ServiceErrorException(ERR_ALREADY_IN_PROGRESS_APPLICATION);
            }

            // REJECTED 상태면 기존 신청 정보 업데이트 (재신청)
            existing.reapply(request.categoryCode(), request.content(), request.imageUrl());
            instructorMember = existing;
        } else {
            instructorMember = InstructorMember.register(memberId, request.categoryCode(), request.content(), request.imageUrl());
            instructorMemberRepository.save(instructorMember);
        }

        return ApplyInstructorResponse.register(instructorMember);
    }

    @Transactional
    public InstructorActionResponse processInstructorApplication(UUID memberId, InstructorActionRequest request) {
        InstructorMember instructorMember = instructorMemberRepository.findByMemberId(memberId)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_INSTRUCTOR_APPLICATION));

        // PENDING 이 아닌 경우 이미 처리된 신청
        if (instructorMember.getStatus() != InstructorMemberStatus.PENDING) {
            throw new ServiceErrorException(ERR_ALREADY_PROCESSED_APPLICATION);
        }

        if (request.action() == InstructorAction.APPROVE) {
            instructorMember.approve();

            Member member = memberRepository.findById(memberId)
                    .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_MEMBER));
            member.promoteToInstructor();
        } else {
            // 반려 사유 검증은 도메인 메서드 내부에서 처리 (ERR_BLANK_REJECT_REASON → 400)
            instructorMember.reject(request.rejectReason());
        }

        return InstructorActionResponse.register(instructorMember);
    }

    @Transactional(readOnly = true)
    public InstructorApplicationDetail getInstructorApplicationDetail(UUID memberId) {
        return instructorMemberRepository.findInstructorApplicationDetail(memberId)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_INSTRUCTOR_APPLICATION));
    }

    @Transactional(readOnly = true)
    public MyInstructorApplicationResponse getMyInstructorApplication(UUID memberId) {
        return instructorMemberRepository.findMyInstructorApplication(memberId)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_INSTRUCTOR_APPLICATION));
    }

    @Transactional(readOnly = true)
    public PageResponse<InstructorApplicationItem> getInstructorApplications(
            InstructorMemberStatus status,
            Pageable pageable
    ) {
        return PageResponse.register(instructorMemberRepository.findInstructorApplications(status, pageable));
    }
}
