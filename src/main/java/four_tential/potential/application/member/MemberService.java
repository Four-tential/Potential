package four_tential.potential.application.member;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.course.course.CourseStatus;
import four_tential.potential.domain.course.course_category.CourseCategoryRepository;
import four_tential.potential.domain.member.follow.Follow;
import four_tential.potential.domain.member.follow.FollowRepository;
import four_tential.potential.domain.member.instructor_member.InstructorMember;
import four_tential.potential.domain.member.instructor_member.InstructorMemberRepository;
import four_tential.potential.domain.member.instructor_member.InstructorMemberStatus;
import four_tential.potential.domain.member.instructor_member.InstructorProfileQueryResult;
import four_tential.potential.domain.member.member.Member;
import four_tential.potential.domain.member.member.MemberRepository;
import four_tential.potential.domain.member.member.MemberStatus;
import four_tential.potential.domain.member.member_onboard.MemberOnBoard;
import four_tential.potential.domain.member.member_onboard.MemberOnBoardRepository;
import four_tential.potential.domain.member.onboard_category.MemberOnBoardCategory;
import four_tential.potential.domain.member.onboard_category.OnBoardCategoryRepository;
import four_tential.potential.domain.order.OrderRepository;
import four_tential.potential.domain.order.OrderStatus;
import four_tential.potential.infra.jwt.JwtRepository;
import four_tential.potential.infra.jwt.JwtUtil;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

import static four_tential.potential.infra.redis.RedisConstants.INSTRUCTOR_PROFILE_CACHE;
import static four_tential.potential.infra.redis.RedisConstants.MY_FOLLOWS_CACHE;
import static four_tential.potential.infra.redis.RedisConstants.MY_PAGE_CACHE;
import four_tential.potential.presentation.member.model.request.ChangePasswordRequest;
import four_tential.potential.presentation.member.model.request.ChangeMemberStatusRequest;
import four_tential.potential.presentation.member.model.request.OnBoardRequest;
import four_tential.potential.presentation.member.model.request.UpdateOnBoardRequest;
import four_tential.potential.presentation.member.model.request.UpdateMyPageRequest;
import four_tential.potential.presentation.member.model.request.WithdrawalRequest;
import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.presentation.member.model.response.ChangeMemberStatusResponse;
import four_tential.potential.presentation.member.model.response.FollowedInstructorItem;
import four_tential.potential.presentation.member.model.response.FollowResponse;
import four_tential.potential.presentation.member.model.response.InstructorProfileResponse;
import four_tential.potential.presentation.member.model.response.MyPageResponse;
import four_tential.potential.presentation.member.model.response.OnBoardResponse;
import four_tential.potential.presentation.member.model.response.UpdateMyPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
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

import static four_tential.potential.common.exception.domain.CourseExceptionEnum.ERR_CATEGORY_NOT_FOUND;
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
    private final FollowRepository followRepository;
    private final JwtRepository jwtRepository;
    private final JwtUtil jwtUtil;

    @Value("${member.default-profile-image-url}")
    private String defaultProfileImageUrl;

    @Cacheable(cacheNames = MY_PAGE_CACHE, key = "#memberId")
    @Transactional(readOnly = true)
    public MyPageResponse getMyPageInfo(UUID memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_MEMBER));

        return MyPageResponse.register(member, getProfileImageUrlOrDefault(member));
    }

    @CacheEvict(cacheNames = MY_PAGE_CACHE, key = "#memberId")
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
            throw new ServiceErrorException(ERR_CATEGORY_NOT_FOUND);
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
                throw new ServiceErrorException(ERR_CATEGORY_NOT_FOUND);
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

    @CacheEvict(cacheNames = {MY_PAGE_CACHE, INSTRUCTOR_PROFILE_CACHE}, key = "#memberId")
    @Transactional
    public void withdrawMember(UUID memberId, String email, String accessToken, WithdrawalRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_MEMBER));

        // 토큰 검증
        if (!jwtUtil.validateToken(accessToken)) {
            throw new ServiceErrorException(ERR_INVALID_AUTHORIZE);
        }

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

        long remainingTime = jwtUtil.getRemainingTime(accessToken);
        if (remainingTime > 0) {
            jwtRepository.addBlacklist(accessToken, remainingTime);
        }
    }

    @CacheEvict(cacheNames = {MY_PAGE_CACHE, INSTRUCTOR_PROFILE_CACHE}, key = "#memberId")
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

    @CacheEvict(cacheNames = MY_FOLLOWS_CACHE, allEntries = true)
    @Transactional
    public FollowResponse followInstructor(UUID followerId, UUID instructorMemberId) {
        // 본인 팔로우 방지
        if (followerId.equals(instructorMemberId)) {
            throw new ServiceErrorException(ERR_CANNOT_FOLLOW_SELF);
        }

        // 승인된 강사 존재 확인
        InstructorMember instructorMember = findApprovedInstructor(instructorMemberId);

        // 중복 팔로우 방지
        if (followRepository.existsByMemberIdAndMemberInstructorId(followerId, instructorMember.getId())) {
            throw new ServiceErrorException(ERR_ALREADY_FOLLOWED);
        }

        followRepository.save(Follow.register(followerId, instructorMember.getId()));

        return FollowResponse.register(instructorMemberId, true);
    }

    @Cacheable(cacheNames = MY_FOLLOWS_CACHE, key = "#memberId + ':' + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort.toString()")
    @Transactional(readOnly = true)
    public PageResponse<FollowedInstructorItem> getMyFollows(UUID memberId, Pageable pageable) {
        return PageResponse.register(
                followRepository.findFollowedInstructors(memberId, pageable)
                        .map(FollowedInstructorItem::register)
        );
    }

    @Cacheable(cacheNames = INSTRUCTOR_PROFILE_CACHE, key = "#instructorId")
    @Transactional(readOnly = true)
    public InstructorProfileResponse getInstructorProfile(UUID instructorId) {
        InstructorProfileQueryResult result = instructorMemberRepository.findInstructorProfile(instructorId)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_INSTRUCTOR));

        return new InstructorProfileResponse(
                result.memberId(),
                result.memberName(),
                result.instructorImageUrl(),
                result.categoryCode(),
                result.categoryName(),
                result.content(),
                result.courseCount(),
                result.averageRating(),
                result.totalStudentCount()
        );
    }

    @CacheEvict(cacheNames = MY_FOLLOWS_CACHE, allEntries = true)
    @Transactional
    public FollowResponse unfollowInstructor(UUID followerId, UUID instructorMemberId) {
        // 승인된 강사 존재 확인
        InstructorMember instructorMember = findApprovedInstructor(instructorMemberId);

        // 팔로우 기록 조회
        Follow follow = followRepository.findByMemberIdAndMemberInstructorId(followerId, instructorMember.getId())
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_FOLLOW));

        followRepository.delete(follow);

        return FollowResponse.register(instructorMemberId, false);
    }

    private InstructorMember findApprovedInstructor(UUID memberId) {
        return instructorMemberRepository.findByMemberId(memberId)
                .filter(im -> im.getStatus() == InstructorMemberStatus.APPROVED)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_INSTRUCTOR));
    }

    private String getProfileImageUrlOrDefault(Member member) {
        return member.getProfileImageUrl() != null ? member.getProfileImageUrl() : defaultProfileImageUrl;
    }
}
