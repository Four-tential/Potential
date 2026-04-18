package four_tential.potential.application.member;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.course.course.CourseStatus;
import four_tential.potential.domain.course.course_category.CourseCategoryRepository;
import four_tential.potential.domain.member.instructor_member.InstructorMember;
import four_tential.potential.domain.member.instructor_member.InstructorMemberRepository;
import four_tential.potential.domain.member.member.Member;
import four_tential.potential.domain.member.member.MemberRepository;
import four_tential.potential.domain.member.member_onboard.MemberOnBoard;
import four_tential.potential.domain.member.member_onboard.MemberOnBoardRepository;
import four_tential.potential.domain.member.onboard_category.MemberOnBoardCategory;
import four_tential.potential.domain.member.onboard_category.OnBoardCategoryRepository;
import four_tential.potential.domain.order.OrderRepository;
import four_tential.potential.domain.order.OrderStatus;
import four_tential.potential.infra.jwt.JwtRepository;
import four_tential.potential.presentation.member.model.request.ChangePasswordRequest;
import four_tential.potential.presentation.member.model.request.ChangeMemberStatusRequest;
import four_tential.potential.presentation.member.model.request.OnBoardRequest;
import four_tential.potential.presentation.member.model.request.UpdateMyPageRequest;
import four_tential.potential.presentation.member.model.request.UpdateOnBoardRequest;
import four_tential.potential.presentation.member.model.request.WithdrawalRequest;
import four_tential.potential.presentation.member.model.response.ChangeMemberStatusResponse;
import four_tential.potential.presentation.member.model.response.MyPageResponse;
import four_tential.potential.presentation.member.model.response.OnBoardResponse;
import four_tential.potential.presentation.member.model.response.UpdateMyPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static four_tential.potential.common.exception.domain.CourseExceptionEnum.ERR_NOT_FOUND_CATEGORY;
import static four_tential.potential.common.exception.domain.MemberExceptionEnum.*;



@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final MemberOnBoardRepository memberOnBoardRepository;
    private final OnBoardCategoryRepository onBoardCategoryRepository;
    private final CourseCategoryRepository courseCategoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final OrderRepository orderRepository;
    private final CourseRepository courseRepository;
    private final InstructorMemberRepository instructorMemberRepository;
    private final JwtRepository jwtRepository;

    @Value("${member.default-profile-image-url}")
    private String defaultProfileImageUrl;

    @Transactional(readOnly = true)
    public MyPageResponse getMyPageInfo(UUID memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_MEMBER));

        return MyPageResponse.register(member, getProfileImageUrlOrDefault(member));
    }

    @Transactional
    public UpdateMyPageResponse updateMyPageInfo(UUID memberId, UpdateMyPageRequest request) {
        if (request.phone() == null && request.profileImageUrl() == null) {
            throw new ServiceErrorException(ERR_NO_UPDATE_FIELD);
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_MEMBER));

        member.updateInfo(request.phone(), request.profileImageUrl());

        return UpdateMyPageResponse.register(member, getProfileImageUrlOrDefault(member));
    }

    @Transactional
    public OnBoardResponse createOnBoarding(UUID memberId, OnBoardRequest request) {
        if (memberOnBoardRepository.existsByMemberId(memberId)) {
            throw new ServiceErrorException(ERR_ALREADY_ONBOARDED);
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_MEMBER));

        // 카테고리 코드 중복 검증
        List<String> categoryCodes = request.categoryCodes();
        if (categoryCodes.size() != new HashSet<>(categoryCodes).size()) {
            throw new ServiceErrorException(ERR_DUPLICATED_CATEGORY_IN_REQUEST);
        }

        // 카테고리 코드 유효성 검증
        Set<String> existingCodes = courseCategoryRepository.findExistingCodes(categoryCodes);
        if (existingCodes.size() != categoryCodes.size()) {
            throw new ServiceErrorException(ERR_NOT_FOUND_CATEGORY);
        }

        // 온보딩 등록
        MemberOnBoard onBoard = MemberOnBoard.register(member, request.goal());
        memberOnBoardRepository.save(onBoard);

        // 카테고리 등록
        List<MemberOnBoardCategory> categories = categoryCodes.stream()
                .map(code -> MemberOnBoardCategory.register(member, code))
                .toList();
        onBoardCategoryRepository.saveAll(categories);

        // 온보딩 완료 처리
        member.completeOnboarding();

        return OnBoardResponse.register(onBoard, categoryCodes);
    }

    @Transactional
    public OnBoardResponse updateOnBoarding(UUID memberId, UpdateOnBoardRequest request) {
        if (request.goal() == null && (request.categoryCodes() == null || request.categoryCodes().isEmpty())) {
            throw new ServiceErrorException(ERR_NO_UPDATE_FIELD);
        }

        MemberOnBoard onBoard = memberOnBoardRepository.findByMemberId(memberId)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_ONBOARDING));

        // 목표 수정 (null이면 기존 값 유지)
        if (request.goal() != null) {
            onBoard.updateGoal(request.goal());
        }

        // 기존 카테고리 코드 조회
        List<String> existingCodes = onBoardCategoryRepository.findByMemberId(memberId).stream()
                .map(onBoardCategory -> onBoardCategory.getCategoryCode()).toList();

        // 카테고리 수정 (null이면 기존 카테고리 유지)
        List<String> resultCategoryCodes;
        if (request.categoryCodes() != null && !request.categoryCodes().isEmpty()) {
            List<String> requestedCodes = request.categoryCodes();
            if (requestedCodes.size() != new HashSet<>(requestedCodes).size()) {
                throw new ServiceErrorException(ERR_DUPLICATED_CATEGORY_IN_REQUEST);
            }

            Set<String> newCodes = new HashSet<>(requestedCodes);

            // 카테고리 코드 유효성 검증
            Set<String> validCodes = courseCategoryRepository.findExistingCodes(newCodes);
            if (validCodes.size() != newCodes.size()) {
                throw new ServiceErrorException(ERR_NOT_FOUND_CATEGORY);
            }

            // 기존에 있고 새 요청에 없는 것
            Set<String> deleteExistCode = existingCodes.stream()
                    .filter(code -> !newCodes.contains(code))
                    .collect(Collectors.toSet());

            // 새 요청에 있고 기존에 없는 것
            Set<String> addNewCode = newCodes.stream()
                    .filter(code -> !existingCodes.contains(code))
                    .collect(Collectors.toSet());

            if (!deleteExistCode.isEmpty()) {
                onBoardCategoryRepository.deleteByMemberIdAndCategoryCodeIn(memberId, deleteExistCode);
            }

            if (!addNewCode.isEmpty()) {
                Member member = memberRepository.findById(memberId)
                        .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_MEMBER));

                List<MemberOnBoardCategory> categories = addNewCode.stream()
                        .map(code -> MemberOnBoardCategory.register(member, code))
                        .toList();
                onBoardCategoryRepository.saveAll(categories);
            }

            resultCategoryCodes = List.copyOf(newCodes);
        } else {
            resultCategoryCodes = List.copyOf(existingCodes);
        }

        return OnBoardResponse.register(onBoard, resultCategoryCodes);
    }

    @Transactional
    public void withdrawMember(UUID memberId, String email, WithdrawalRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_MEMBER));

        // 비밀번호 검증
        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new ServiceErrorException(ERR_WRONG_PASSWORD);
        }

        LocalDateTime now = LocalDateTime.now();

        // 수강 예정/진행 중 코스(PAID 또는 CONFIRMED 주문 + 아직 종료되지 않은 OPEN 코스) 확인
        if (orderRepository.existsActiveEnrollment(
                memberId,
                List.of(OrderStatus.PAID, OrderStatus.CONFIRMED),
                List.of(CourseStatus.OPEN),
                now
        )) {
            throw new ServiceErrorException(ERR_HAS_COURSE);
        }

        // 예정/진행 중인 강사 코스(PREPARATION 또는 OPEN + 아직 종료되지 않은 코스) 확인
        Optional<InstructorMember> instructorMember = instructorMemberRepository.findByMemberId(memberId);
        if (instructorMember.isPresent() &&
                courseRepository.existsByMemberInstructorIdAndStatusInAndEndAtAfter(
                        instructorMember.get().getId(),
                        List.of(CourseStatus.PREPARATION, CourseStatus.OPEN),
                        now
                )) {
            throw new ServiceErrorException(ERR_HAS_ACTIVE_INSTRUCTOR_COURSES);
        }

        member.withdraw();
        jwtRepository.deleteRefreshToken(email);
    }

    @Transactional
    public ChangeMemberStatusResponse changeMemberStatus(UUID memberId, ChangeMemberStatusRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_MEMBER));

        member.changeStatus(request.status());

        return ChangeMemberStatusResponse.register(member);
    }

    @Transactional
    public void changePassword(UUID memberId, ChangePasswordRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_MEMBER));

        // 현재 비밀번호 일치 확인
        if (!passwordEncoder.matches(request.currentPassword(), member.getPassword())) {
            throw new ServiceErrorException(ERR_WRONG_CURRENT_PASSWORD);
        }

        // 현재와 동일한 비밀번호로 변경 불가
        if (passwordEncoder.matches(request.newPassword(), member.getPassword())) {
            throw new ServiceErrorException(ERR_SAME_AS_CURRENT_PASSWORD);
        }

        member.changePassword(passwordEncoder.encode(request.newPassword()));
    }

    private String getProfileImageUrlOrDefault(Member member) {
        return member.getProfileImageUrl() != null ? member.getProfileImageUrl() : defaultProfileImageUrl;
    }
}
