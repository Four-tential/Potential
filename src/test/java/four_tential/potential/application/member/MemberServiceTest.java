package four_tential.potential.application.member;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.course.course.CourseStatus;
import four_tential.potential.domain.course.course_category.CourseCategoryRepository;
import four_tential.potential.domain.member.fixture.InstructorMemberFixture;
import four_tential.potential.domain.member.follow.Follow;
import four_tential.potential.domain.member.follow.FollowRepository;
import four_tential.potential.domain.member.instructor_member.InstructorMember;
import four_tential.potential.domain.member.instructor_member.InstructorMemberRepository;
import four_tential.potential.domain.order.OrderRepository;
import four_tential.potential.domain.order.OrderStatus;
import four_tential.potential.infra.jwt.JwtRepository;
import four_tential.potential.infra.jwt.JwtUtil;
import four_tential.potential.domain.member.fixture.MemberFixture;
import four_tential.potential.domain.member.fixture.MemberOnBoardFixture;
import four_tential.potential.domain.member.member.Member;
import four_tential.potential.domain.member.member.MemberRepository;
import four_tential.potential.domain.member.member_onboard.MemberOnBoard;
import four_tential.potential.domain.member.member_onboard.MemberOnBoardGoal;
import four_tential.potential.domain.member.member_onboard.MemberOnBoardRepository;
import four_tential.potential.domain.member.onboard_category.MemberOnBoardCategory;
import four_tential.potential.domain.member.onboard_category.OnBoardCategoryRepository;
import four_tential.potential.presentation.member.model.request.ChangePasswordRequest;
import four_tential.potential.presentation.member.model.request.ChangeMemberStatusRequest;
import four_tential.potential.presentation.member.model.request.WithdrawalRequest;
import four_tential.potential.presentation.member.model.request.OnBoardRequest;
import four_tential.potential.presentation.member.model.response.ChangeMemberStatusResponse;
import four_tential.potential.presentation.member.model.response.FollowResponse;
import four_tential.potential.domain.member.member.MemberStatus;
import four_tential.potential.presentation.member.model.request.UpdateMyPageRequest;
import four_tential.potential.presentation.member.model.request.UpdateOnBoardRequest;
import four_tential.potential.presentation.member.model.response.MyPageResponse;
import four_tential.potential.presentation.member.model.response.OnBoardResponse;
import four_tential.potential.presentation.member.model.response.UpdateMyPageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberOnBoardRepository memberOnBoardRepository;

    @Mock
    private OnBoardCategoryRepository onBoardCategoryRepository;

    @Mock
    private CourseCategoryRepository courseCategoryRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private InstructorMemberRepository instructorMemberRepository;

    @Mock
    private JwtRepository jwtRepository;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private FollowRepository followRepository;

    @InjectMocks
    private MemberService memberService;

    private static final String DEFAULT_IMAGE_URL = "https://bucketurl/default-profile-image.png";
    private static final String CUSTOM_IMAGE_URL = MemberFixture.DEFAULT_PROFILE_IMAGE_URL;
    private static final String ACCESS_TOKEN = "valid.access.token";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(memberService, "defaultProfileImageUrl", DEFAULT_IMAGE_URL);
    }

    @Test
    @DisplayName("마이페이지 조회 성공 - 프로필 이미지가 있으면 실제 URL 반환")
    void getMyPageInfo_withProfileImage() {
        Member member = MemberFixture.defaultMember();
        member.setProfileImageUrl(CUSTOM_IMAGE_URL);
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));

        MyPageResponse response = memberService.getMyPageInfo(member.getId());

        assertThat(response.email()).isEqualTo(MemberFixture.DEFAULT_EMAIL);
        assertThat(response.profileImageUrl()).isEqualTo(CUSTOM_IMAGE_URL);
    }

    @Test
    @DisplayName("마이페이지 조회 성공 - 프로필 이미지가 없으면 기본 URL 반환")
    void getMyPageInfo_withoutProfileImage() {
        Member member = MemberFixture.defaultMember(); // profileImageUrl = null
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));

        MyPageResponse response = memberService.getMyPageInfo(member.getId());

        assertThat(response.profileImageUrl()).isEqualTo(DEFAULT_IMAGE_URL);
    }

    @Test
    @DisplayName("마이페이지 조회 - 존재하지 않는 회원이면 ServiceErrorException 발생")
    void getMyPageInfo_memberNotFound() {
        UUID unknownId = UUID.randomUUID();
        given(memberRepository.findById(unknownId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.getMyPageInfo(unknownId))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 회원입니다");
    }

    @Test
    @DisplayName("마이페이지 수정 성공 - phone만 전송 시 phone 변경, 이미지 유지")
    void updateMyPageInfo_phoneOnly() {
        Member member = MemberFixture.defaultMember();
        member.setProfileImageUrl(CUSTOM_IMAGE_URL);
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));

        UpdateMyPageRequest request = new UpdateMyPageRequest("010-9999-9999", null);
        UpdateMyPageResponse response = memberService.updateMyPageInfo(member.getId(), request);

        assertThat(response.phone()).isEqualTo("010-9999-9999");
        assertThat(response.profileImageUrl()).isEqualTo(CUSTOM_IMAGE_URL); // 기존 이미지 유지
    }

    @Test
    @DisplayName("마이페이지 수정 성공 - profileImageUrl만 전송 시 이미지 변경, phone 유지")
    void updateMyPageInfo_profileImageOnly() {
        Member member = MemberFixture.defaultMember();
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));

        UpdateMyPageRequest request = new UpdateMyPageRequest(null, CUSTOM_IMAGE_URL);
        UpdateMyPageResponse response = memberService.updateMyPageInfo(member.getId(), request);

        assertThat(response.phone()).isEqualTo(MemberFixture.DEFAULT_PHONE); // 기존 phone 유지
        assertThat(response.profileImageUrl()).isEqualTo(CUSTOM_IMAGE_URL);
    }

    @Test
    @DisplayName("마이페이지 수정 성공 - phone, profileImageUrl 모두 전송 시 둘 다 변경")
    void updateMyPageInfo_both() {
        Member member = MemberFixture.defaultMember();
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));

        UpdateMyPageRequest request = new UpdateMyPageRequest("010-9999-9999", CUSTOM_IMAGE_URL);
        UpdateMyPageResponse response = memberService.updateMyPageInfo(member.getId(), request);

        assertThat(response.phone()).isEqualTo("010-9999-9999");
        assertThat(response.profileImageUrl()).isEqualTo(CUSTOM_IMAGE_URL);
    }

    @Test
    @DisplayName("마이페이지 수정 - 모든 필드가 null이면 ServiceErrorException 발생")
    void updateMyPageInfo_allNull() {
        UpdateMyPageRequest request = new UpdateMyPageRequest(null, null);

        assertThatThrownBy(() -> memberService.updateMyPageInfo(UUID.randomUUID(), request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("수정할 항목을 하나 이상 입력해주세요");
    }

    @Test
    @DisplayName("마이페이지 수정 - 존재하지 않는 회원이면 ServiceErrorException 발생")
    void updateMyPageInfo_memberNotFound() {
        UUID unknownId = UUID.randomUUID();
        given(memberRepository.findById(unknownId)).willReturn(Optional.empty());

        UpdateMyPageRequest request = new UpdateMyPageRequest("010-9999-9999", null);

        assertThatThrownBy(() -> memberService.updateMyPageInfo(unknownId, request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 회원입니다");
    }

    @Test
    @DisplayName("온보딩 등록 성공 - 목표와 카테고리가 저장되고 멤버 온보딩 완료 처리")
    void createOnBoarding_success() {
        Member member = MemberFixture.defaultMember();
        MemberOnBoard onBoard = MemberOnBoardFixture.defaultMemberOnBoard();
        List<String> categoryCodes = List.of("FITNESS", "ART");

        given(memberOnBoardRepository.existsByMemberId(member.getId())).willReturn(false);
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
        given(courseCategoryRepository.findExistingCodes(any())).willReturn(Set.of("FITNESS", "ART"));
        given(memberOnBoardRepository.save(any())).willReturn(onBoard);

        OnBoardRequest request = new OnBoardRequest(MemberOnBoardGoal.HOBBY, categoryCodes);
        OnBoardResponse response = memberService.createOnBoarding(member.getId(), request);

        assertThat(response.goal()).isEqualTo("HOBBY");
        assertThat(response.categoryCodes()).containsExactly("FITNESS", "ART");
        assertThat(member.isHasOnboarding()).isTrue();
        verify(onBoardCategoryRepository).saveAll(any());
    }

    @Test
    @DisplayName("온보딩 등록 - 이미 온보딩을 완료한 회원이면 ServiceErrorException 발생")
    void createOnBoarding_alreadyOnboarded() {
        Member member = MemberFixture.memberWithOnboarding();
        given(memberOnBoardRepository.existsByMemberId(member.getId())).willReturn(true);

        OnBoardRequest request = new OnBoardRequest(MemberOnBoardGoal.HOBBY, List.of("FITNESS"));

        assertThatThrownBy(() -> memberService.createOnBoarding(member.getId(), request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("이미 온보딩을 완료한 회원입니다");
    }

    @Test
    @DisplayName("온보딩 등록 - 중복 카테고리 코드가 포함되면 ServiceErrorException 발생")
    void createOnBoarding_duplicatedCategory() {
        Member member = MemberFixture.defaultMember();
        given(memberOnBoardRepository.existsByMemberId(member.getId())).willReturn(false);
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));

        OnBoardRequest request = new OnBoardRequest(MemberOnBoardGoal.HOBBY, List.of("FITNESS", "FITNESS"));

        assertThatThrownBy(() -> memberService.createOnBoarding(member.getId(), request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("중복된 카테고리 코드가 포함되어 있습니다");
    }

    @Test
    @DisplayName("온보딩 등록 - 존재하지 않는 카테고리 코드가 포함되면 ServiceErrorException 발생")
    void createOnBoarding_invalidCategory() {
        Member member = MemberFixture.defaultMember();
        given(memberOnBoardRepository.existsByMemberId(member.getId())).willReturn(false);
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
        given(courseCategoryRepository.findExistingCodes(any())).willReturn(Set.of("FITNESS")); // INVALID 미포함

        OnBoardRequest request = new OnBoardRequest(MemberOnBoardGoal.HOBBY, List.of("FITNESS", "INVALID"));

        assertThatThrownBy(() -> memberService.createOnBoarding(member.getId(), request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 카테고리입니다");
    }

    @Test
    @DisplayName("온보딩 등록 - 존재하지 않는 회원이면 ServiceErrorException 발생")
    void createOnBoarding_memberNotFound() {
        UUID unknownId = UUID.randomUUID();
        given(memberOnBoardRepository.existsByMemberId(unknownId)).willReturn(false);
        given(memberRepository.findById(unknownId)).willReturn(Optional.empty());

        OnBoardRequest request = new OnBoardRequest(MemberOnBoardGoal.HOBBY, List.of("FITNESS"));

        assertThatThrownBy(() -> memberService.createOnBoarding(unknownId, request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 회원입니다");
    }

    @Test
    @DisplayName("온보딩 수정 성공 - 기존과 다른 카테고리로 변경 시 삭제 대상은 지우고 추가 대상만 저장")
    void updateOnBoarding_diffUpdate() {
        Member member = MemberFixture.memberWithOnboarding();
        MemberOnBoard onBoard = MemberOnBoardFixture.defaultMemberOnBoard();

        // 기존: FITNESS / 요청: COOK → FITNESS 삭제, COOK 추가
        MemberOnBoardCategory existingCategory = MemberOnBoardCategory.register(member, "FITNESS");
        given(memberOnBoardRepository.findByMemberId(member.getId())).willReturn(Optional.of(onBoard));
        given(onBoardCategoryRepository.findByMemberId(member.getId())).willReturn(List.of(existingCategory));
        given(courseCategoryRepository.findExistingCodes(any())).willReturn(Set.of("COOK"));
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));

        UpdateOnBoardRequest request = new UpdateOnBoardRequest(MemberOnBoardGoal.STRESS_OUT, List.of("COOK"));
        OnBoardResponse response = memberService.updateOnBoarding(member.getId(), request);

        assertThat(response.goal()).isEqualTo("STRESS_OUT");
        assertThat(response.categoryCodes()).containsExactly("COOK");
        verify(onBoardCategoryRepository).deleteByMemberIdAndCategoryCodeIn(eq(member.getId()), any());
        verify(onBoardCategoryRepository).saveAll(any());
    }

    @Test
    @DisplayName("온보딩 수정 성공 - 기존 카테고리 포함하여 카테고리 추가 시 삭제 없이 신규 코드만 저장")
    void updateOnBoarding_addCategory() {
        Member member = MemberFixture.memberWithOnboarding();
        MemberOnBoard onBoard = MemberOnBoardFixture.defaultMemberOnBoard();

        // 기존: FITNESS / 요청: FITNESS, COOK → FITNESS 유지, COOK만 추가
        MemberOnBoardCategory existingCategory = MemberOnBoardCategory.register(member, "FITNESS");
        given(memberOnBoardRepository.findByMemberId(member.getId())).willReturn(Optional.of(onBoard));
        given(onBoardCategoryRepository.findByMemberId(member.getId())).willReturn(List.of(existingCategory));
        given(courseCategoryRepository.findExistingCodes(any())).willReturn(Set.of("FITNESS", "COOK"));
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));

        UpdateOnBoardRequest request = new UpdateOnBoardRequest(null, List.of("FITNESS", "COOK"));
        OnBoardResponse response = memberService.updateOnBoarding(member.getId(), request);

        assertThat(response.categoryCodes()).containsExactlyInAnyOrder("FITNESS", "COOK");
        verify(onBoardCategoryRepository, never()).deleteByMemberIdAndCategoryCodeIn(any(), any());
        verify(onBoardCategoryRepository).saveAll(any());
    }

    @Test
    @DisplayName("온보딩 수정 성공 - 목표만 전송 시 목표만 변경, 카테고리 조회 결과 그대로 반환")
    void updateOnBoarding_goalOnly() {
        Member member = MemberFixture.memberWithOnboarding();
        MemberOnBoard onBoard = MemberOnBoardFixture.defaultMemberOnBoard();

        MemberOnBoardCategory existingCategory = MemberOnBoardCategory.register(member, "FITNESS");
        given(memberOnBoardRepository.findByMemberId(member.getId())).willReturn(Optional.of(onBoard));
        given(onBoardCategoryRepository.findByMemberId(member.getId())).willReturn(List.of(existingCategory));

        UpdateOnBoardRequest request = new UpdateOnBoardRequest(MemberOnBoardGoal.STRESS_OUT, null);
        OnBoardResponse response = memberService.updateOnBoarding(member.getId(), request);

        assertThat(response.goal()).isEqualTo("STRESS_OUT");
        assertThat(response.categoryCodes()).containsExactly("FITNESS");
        verify(onBoardCategoryRepository, never()).deleteByMemberIdAndCategoryCodeIn(any(), any());
        verify(onBoardCategoryRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("온보딩 수정 - 모든 필드가 null이면 ServiceErrorException 발생")
    void updateOnBoarding_allNull() {
        UpdateOnBoardRequest request = new UpdateOnBoardRequest(null, null);

        assertThatThrownBy(() -> memberService.updateOnBoarding(UUID.randomUUID(), request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("수정할 항목을 하나 이상 입력해주세요");
    }

    @Test
    @DisplayName("온보딩 수정 - 온보딩 미설정 회원이면 ServiceErrorException 발생")
    void updateOnBoarding_notFound() {
        UUID unknownId = UUID.randomUUID();
        given(memberOnBoardRepository.findByMemberId(unknownId)).willReturn(Optional.empty());

        UpdateOnBoardRequest request = new UpdateOnBoardRequest(MemberOnBoardGoal.STRESS_OUT, List.of("COOK"));

        assertThatThrownBy(() -> memberService.updateOnBoarding(unknownId, request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("온보딩 정보가 존재하지 않습니다");
    }

    @Test
    @DisplayName("온보딩 수정 - 중복 카테고리 코드가 포함되면 ServiceErrorException 발생")
    void updateOnBoarding_duplicatedCategory() {
        Member member = MemberFixture.memberWithOnboarding();
        MemberOnBoard onBoard = MemberOnBoardFixture.defaultMemberOnBoard();

        given(memberOnBoardRepository.findByMemberId(member.getId())).willReturn(Optional.of(onBoard));
        given(onBoardCategoryRepository.findByMemberId(member.getId())).willReturn(List.of());

        UpdateOnBoardRequest request = new UpdateOnBoardRequest(null, List.of("FITNESS", "FITNESS"));

        assertThatThrownBy(() -> memberService.updateOnBoarding(member.getId(), request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("중복된 카테고리 코드가 포함되어 있습니다");
    }

    @Test
    @DisplayName("온보딩 수정 - 존재하지 않는 카테고리 코드이면 ServiceErrorException 발생")
    void updateOnBoarding_invalidCategory() {
        Member member = MemberFixture.memberWithOnboarding();
        MemberOnBoard onBoard = MemberOnBoardFixture.defaultMemberOnBoard();

        given(memberOnBoardRepository.findByMemberId(member.getId())).willReturn(Optional.of(onBoard));
        given(onBoardCategoryRepository.findByMemberId(member.getId())).willReturn(List.of());
        given(courseCategoryRepository.findExistingCodes(any())).willReturn(Set.of()); // INVALID 미포함

        UpdateOnBoardRequest request = new UpdateOnBoardRequest(MemberOnBoardGoal.STRESS_OUT, List.of("INVALID"));

        assertThatThrownBy(() -> memberService.updateOnBoarding(member.getId(), request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 카테고리입니다");
    }

    @Test
    @DisplayName("회원 탈퇴 성공 - 수강 예정 코스 없고 강사 이력 없음")
    void withdrawMember_success_noScheduledCourseNoInstructor() {
        Member member = MemberFixture.defaultMember();
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
        given(passwordEncoder.matches("P@ssw0rd1!", MemberFixture.DEFAULT_PASSWORD)).willReturn(true);
        given(jwtUtil.validateToken(ACCESS_TOKEN)).willReturn(true);
        given(orderRepository.existsActiveEnrollment(
                eq(member.getId()),
                eq(List.of(OrderStatus.PAID, OrderStatus.CONFIRMED)),
                eq(List.of(CourseStatus.OPEN)),
                any(LocalDateTime.class)
        )).willReturn(false);
        given(instructorMemberRepository.findByMemberId(member.getId())).willReturn(Optional.empty());
        given(jwtUtil.getRemainingTime(ACCESS_TOKEN)).willReturn(1000L);

        memberService.withdrawMember(member.getId(), MemberFixture.DEFAULT_EMAIL, ACCESS_TOKEN, new WithdrawalRequest("P@ssw0rd1!"));

        assertThat(member.getStatus()).isEqualTo(MemberStatus.WITHDRAWAL);
        verify(jwtRepository).deleteRefreshToken(MemberFixture.DEFAULT_EMAIL);
        verify(jwtRepository).addBlacklist(ACCESS_TOKEN, 1000L);
    }

    @Test
    @DisplayName("회원 탈퇴 성공 - 결제 완료 주문이 있어도 종료되지 않은 OPEN 코스가 없음")
    void withdrawMember_success_paidOrderButNoActiveEnrollment() {
        Member member = MemberFixture.defaultMember();
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
        given(passwordEncoder.matches("P@ssw0rd1!", MemberFixture.DEFAULT_PASSWORD)).willReturn(true);
        given(jwtUtil.validateToken(ACCESS_TOKEN)).willReturn(true);
        given(orderRepository.existsActiveEnrollment(
                eq(member.getId()),
                eq(List.of(OrderStatus.PAID, OrderStatus.CONFIRMED)),
                eq(List.of(CourseStatus.OPEN)),
                any(LocalDateTime.class)
        )).willReturn(false);
        given(instructorMemberRepository.findByMemberId(member.getId())).willReturn(Optional.empty());
        given(jwtUtil.getRemainingTime(ACCESS_TOKEN)).willReturn(1000L);

        memberService.withdrawMember(member.getId(), MemberFixture.DEFAULT_EMAIL, ACCESS_TOKEN, new WithdrawalRequest("P@ssw0rd1!"));

        assertThat(member.getStatus()).isEqualTo(MemberStatus.WITHDRAWAL);
        verify(jwtRepository).deleteRefreshToken(MemberFixture.DEFAULT_EMAIL);
        verify(jwtRepository).addBlacklist(ACCESS_TOKEN, 1000L);
    }

    @Test
    @DisplayName("회원 탈퇴 성공 - 강사 이력 있으나 OPEN 코스 없음")
    void withdrawMember_success_instructorExistsButNoOpenCourse() {
        Member member = MemberFixture.defaultMember();
        UUID instructorId = UUID.randomUUID();
        InstructorMember instructorMember = createInstructorMember(instructorId);
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
        given(passwordEncoder.matches("P@ssw0rd1!", MemberFixture.DEFAULT_PASSWORD)).willReturn(true);
        given(jwtUtil.validateToken(ACCESS_TOKEN)).willReturn(true);
        given(orderRepository.existsActiveEnrollment(
                eq(member.getId()),
                eq(List.of(OrderStatus.PAID, OrderStatus.CONFIRMED)),
                eq(List.of(CourseStatus.OPEN)),
                any(LocalDateTime.class)
        )).willReturn(false);
        given(instructorMemberRepository.findByMemberId(member.getId())).willReturn(Optional.of(instructorMember));
        given(courseRepository.existsByMemberInstructorIdAndStatusInAndEndAtAfter(
                eq(instructorId),
                eq(List.of(CourseStatus.PREPARATION, CourseStatus.OPEN)),
                any(LocalDateTime.class)
        )).willReturn(false);
        given(jwtUtil.getRemainingTime(ACCESS_TOKEN)).willReturn(1000L);

        memberService.withdrawMember(member.getId(), MemberFixture.DEFAULT_EMAIL, ACCESS_TOKEN, new WithdrawalRequest("P@ssw0rd1!"));

        assertThat(member.getStatus()).isEqualTo(MemberStatus.WITHDRAWAL);
        verify(jwtRepository).deleteRefreshToken(MemberFixture.DEFAULT_EMAIL);
        verify(jwtRepository).addBlacklist(ACCESS_TOKEN, 1000L);
    }

    @Test
    @DisplayName("회원 탈퇴 실패 - 존재하지 않는 회원 ID")
    void withdrawMember_memberNotFound() {
        UUID unknownId = UUID.randomUUID();
        given(memberRepository.findById(unknownId)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                memberService.withdrawMember(unknownId, MemberFixture.DEFAULT_EMAIL, ACCESS_TOKEN, new WithdrawalRequest("P@ssw0rd1!"))
        )
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 회원입니다");
        verify(jwtRepository, never()).deleteRefreshToken(any());
    }

    @Test
    @DisplayName("회원 탈퇴 실패 - 비밀번호 불일치 시 BAD_REQUEST")
    void withdrawMember_wrongPassword() {
        Member member = MemberFixture.defaultMember();
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
        given(jwtUtil.validateToken(ACCESS_TOKEN)).willReturn(true);
        given(passwordEncoder.matches("WrongPass!", MemberFixture.DEFAULT_PASSWORD)).willReturn(false);

        assertThatThrownBy(() ->
                memberService.withdrawMember(member.getId(), MemberFixture.DEFAULT_EMAIL, ACCESS_TOKEN, new WithdrawalRequest("WrongPass!"))
        )
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("비밀번호가 올바르지 않습니다");
        verify(jwtRepository, never()).deleteRefreshToken(any());
    }

    @Test
    @DisplayName("회원 탈퇴 실패 - 유효하지 않은 Access Token이면 비밀번호 검증 전 UNAUTHORIZED")
    void withdrawMember_invalidAccessToken() {
        Member member = MemberFixture.defaultMember();
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
        given(jwtUtil.validateToken(ACCESS_TOKEN)).willReturn(false);

        assertThatThrownBy(() ->
                memberService.withdrawMember(member.getId(), MemberFixture.DEFAULT_EMAIL, ACCESS_TOKEN, new WithdrawalRequest("P@ssw0rd1!"))
        )
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("잘못된 인증 정보입니다, 다시 로그인 하시기 바랍니다");

        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtRepository, never()).deleteRefreshToken(any());
        verify(jwtRepository, never()).addBlacklist(any(), anyLong());
    }

    @Test
    @DisplayName("회원 탈퇴 실패 - 수강 예정/진행 중 코스(PAID 또는 CONFIRMED 주문 + 종료 전 OPEN 코스) 존재 시 CONFLICT")
    void withdrawMember_hasScheduledCourse() {
        Member member = MemberFixture.defaultMember();
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
        given(passwordEncoder.matches("P@ssw0rd1!", MemberFixture.DEFAULT_PASSWORD)).willReturn(true);
        given(jwtUtil.validateToken(ACCESS_TOKEN)).willReturn(true);
        given(orderRepository.existsActiveEnrollment(
                eq(member.getId()),
                eq(List.of(OrderStatus.PAID, OrderStatus.CONFIRMED)),
                eq(List.of(CourseStatus.OPEN)),
                any(LocalDateTime.class)
        )).willReturn(true);

        assertThatThrownBy(() ->
                memberService.withdrawMember(member.getId(), MemberFixture.DEFAULT_EMAIL, ACCESS_TOKEN, new WithdrawalRequest("P@ssw0rd1!"))
        )
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("수강해야할 코스가 있어 탈퇴가 불가합니다");
        verify(jwtRepository, never()).deleteRefreshToken(any());
    }

    @Test
    @DisplayName("회원 탈퇴 실패 - 예정/진행 중 강사 코스(PREPARATION 또는 OPEN + 종료 전 코스) 존재 시 CONFLICT")
    void withdrawMember_hasOpenInstructorCourse() {
        Member member = MemberFixture.defaultMember();
        UUID instructorId = UUID.randomUUID();
        InstructorMember instructorMember = createInstructorMember(instructorId);
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
        given(passwordEncoder.matches("P@ssw0rd1!", MemberFixture.DEFAULT_PASSWORD)).willReturn(true);
        given(jwtUtil.validateToken(ACCESS_TOKEN)).willReturn(true);
        given(orderRepository.existsActiveEnrollment(
                eq(member.getId()),
                eq(List.of(OrderStatus.PAID, OrderStatus.CONFIRMED)),
                eq(List.of(CourseStatus.OPEN)),
                any(LocalDateTime.class)
        )).willReturn(false);
        given(instructorMemberRepository.findByMemberId(member.getId())).willReturn(Optional.of(instructorMember));
        given(courseRepository.existsByMemberInstructorIdAndStatusInAndEndAtAfter(
                eq(instructorId),
                eq(List.of(CourseStatus.PREPARATION, CourseStatus.OPEN)),
                any(LocalDateTime.class)
        )).willReturn(true);

        assertThatThrownBy(() ->
                memberService.withdrawMember(member.getId(), MemberFixture.DEFAULT_EMAIL, ACCESS_TOKEN, new WithdrawalRequest("P@ssw0rd1!"))
        )
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("진행 중인 코스가 있어 탈퇴가 불가합니다");
        verify(jwtRepository, never()).deleteRefreshToken(any());
    }

    @Test
    @DisplayName("회원 탈퇴 실패 - 비밀번호 불일치 시 member.withdraw() 호출되지 않음")
    void withdrawMember_wrongPassword_withdrawNeverCalled() {
        Member member = MemberFixture.defaultMember();
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
        given(jwtUtil.validateToken(ACCESS_TOKEN)).willReturn(true);
        given(passwordEncoder.matches("WrongPass!", MemberFixture.DEFAULT_PASSWORD)).willReturn(false);

        assertThatThrownBy(() ->
                memberService.withdrawMember(member.getId(), MemberFixture.DEFAULT_EMAIL, ACCESS_TOKEN, new WithdrawalRequest("WrongPass!"))
        ).isInstanceOf(ServiceErrorException.class);

        assertThat(member.getStatus()).isNotEqualTo(MemberStatus.WITHDRAWAL);
    }

    @Test
    @DisplayName("회원 탈퇴 성공 - Access Token 남은 시간이 0 이하이면 블랙리스트 등록하지 않음")
    void withdrawMember_success_nonPositiveRemainingTime_doesNotAddBlacklist() {
        Member member = MemberFixture.defaultMember();
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
        given(jwtUtil.validateToken(ACCESS_TOKEN)).willReturn(true);
        given(passwordEncoder.matches("P@ssw0rd1!", MemberFixture.DEFAULT_PASSWORD)).willReturn(true);
        given(orderRepository.existsActiveEnrollment(
                eq(member.getId()),
                eq(List.of(OrderStatus.PAID, OrderStatus.CONFIRMED)),
                eq(List.of(CourseStatus.OPEN)),
                any(LocalDateTime.class)
        )).willReturn(false);
        given(instructorMemberRepository.findByMemberId(member.getId())).willReturn(Optional.empty());
        given(jwtUtil.getRemainingTime(ACCESS_TOKEN)).willReturn(0L);

        memberService.withdrawMember(member.getId(), MemberFixture.DEFAULT_EMAIL, ACCESS_TOKEN, new WithdrawalRequest("P@ssw0rd1!"));

        assertThat(member.getStatus()).isEqualTo(MemberStatus.WITHDRAWAL);
        verify(jwtRepository).deleteRefreshToken(MemberFixture.DEFAULT_EMAIL);
        verify(jwtRepository, never()).addBlacklist(any(), anyLong());
    }

    private InstructorMember createInstructorMember(UUID expectedId) {
        InstructorMember im = InstructorMember.register(UUID.randomUUID(), "FITNESS", "소개", "http://img.url");
        org.springframework.test.util.ReflectionTestUtils.setField(im, "id", expectedId);
        return im;
    }

    @Test
    @DisplayName("회원 상태 변경 성공 - ACTIVE → SUSPENDED, 응답에 변경된 상태 반환")
    void changeMemberStatus_activeToSuspended_success() {
        Member member = MemberFixture.defaultMember(); // ACTIVE
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));

        ChangeMemberStatusResponse response =
                memberService.changeMemberStatus(member.getId(), new ChangeMemberStatusRequest(MemberStatus.SUSPENDED));

        assertThat(member.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(response.memberId()).isEqualTo(member.getId());
        assertThat(response.status()).isEqualTo("SUSPENDED");
    }

    @Test
    @DisplayName("회원 상태 변경 성공 - SUSPENDED → ACTIVE, 응답에 변경된 상태 반환")
    void changeMemberStatus_suspendedToActive_success() {
        Member member = MemberFixture.defaultMember();
        member.suspend(); // SUSPENDED
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));

        ChangeMemberStatusResponse response =
                memberService.changeMemberStatus(member.getId(), new ChangeMemberStatusRequest(MemberStatus.ACTIVE));

        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("회원 상태 변경 실패 - 존재하지 않는 회원 ID면 NOT_FOUND")
    void changeMemberStatus_memberNotFound() {
        UUID unknownId = UUID.randomUUID();
        given(memberRepository.findById(unknownId)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                memberService.changeMemberStatus(unknownId, new ChangeMemberStatusRequest(MemberStatus.SUSPENDED))
        )
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 회원입니다");
    }

    @Test
    @DisplayName("회원 상태 변경 실패 - ACTIVE → ACTIVE 동일 상태 전환 시 BAD_REQUEST")
    void changeMemberStatus_sameStatus_throwsBadRequest() {
        Member member = MemberFixture.defaultMember(); // ACTIVE
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));

        assertThatThrownBy(() ->
                memberService.changeMemberStatus(member.getId(), new ChangeMemberStatusRequest(MemberStatus.ACTIVE))
        )
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("잘못된 상태 전환입니다, ACTIVE와 SUSPENDED 간의 전환만 가능합니다");
    }

    @Test
    @DisplayName("회원 상태 변경 실패 - WITHDRAWAL 전환 시도 시 BAD_REQUEST")
    void changeMemberStatus_toWithdrawal_throwsBadRequest() {
        Member member = MemberFixture.defaultMember();
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));

        assertThatThrownBy(() ->
                memberService.changeMemberStatus(member.getId(), new ChangeMemberStatusRequest(MemberStatus.WITHDRAWAL))
        )
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("잘못된 상태 전환입니다, ACTIVE와 SUSPENDED 간의 전환만 가능합니다");
    }

    @Test
    @DisplayName("회원 상태 변경 실패 - 이미 탈퇴한 회원의 상태 변경 시 BAD_REQUEST")
    void changeMemberStatus_fromWithdrawal_throwsBadRequest() {
        Member member = MemberFixture.defaultMember();
        member.withdraw(); // WITHDRAWAL
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));

        assertThatThrownBy(() ->
                memberService.changeMemberStatus(member.getId(), new ChangeMemberStatusRequest(MemberStatus.ACTIVE))
        )
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("잘못된 상태 전환입니다, ACTIVE와 SUSPENDED 간의 전환만 가능합니다");
    }

    @Test
    @DisplayName("비밀번호 변경 성공 - 현재 비밀번호 일치 시 새 비밀번호로 변경")
    void changePassword_success() {
        Member member = MemberFixture.defaultMember();
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
        given(passwordEncoder.matches("OldP@ss1!", MemberFixture.DEFAULT_PASSWORD)).willReturn(true);
        given(passwordEncoder.matches("NewP@ss2!", MemberFixture.DEFAULT_PASSWORD)).willReturn(false);
        given(passwordEncoder.encode("NewP@ss2!")).willReturn("encodedNewPassword");

        memberService.changePassword(member.getId(), new ChangePasswordRequest("OldP@ss1!", "NewP@ss2!"));

        assertThat(member.getPassword()).isEqualTo("encodedNewPassword");
    }

    @Test
    @DisplayName("비밀번호 변경 실패 - 현재 비밀번호 불일치 시 BAD_REQUEST")
    void changePassword_wrongCurrentPassword_throwsBadRequest() {
        Member member = MemberFixture.defaultMember();
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
        given(passwordEncoder.matches("WrongP@ss!", MemberFixture.DEFAULT_PASSWORD)).willReturn(false);

        assertThatThrownBy(() ->
                memberService.changePassword(member.getId(), new ChangePasswordRequest("WrongP@ss!", "NewP@ss2!"))
        )
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("현재 비밀번호가 올바르지 않습니다");
    }

    @Test
    @DisplayName("비밀번호 변경 실패 - 현재와 동일한 비밀번호로 변경 시 BAD_REQUEST")
    void changePassword_sameAsCurrentPassword_throwsBadRequest() {
        Member member = MemberFixture.defaultMember();
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
        given(passwordEncoder.matches("P@ssw0rd1!", MemberFixture.DEFAULT_PASSWORD)).willReturn(true);

        assertThatThrownBy(() ->
                memberService.changePassword(member.getId(), new ChangePasswordRequest("P@ssw0rd1!", "P@ssw0rd1!"))
        )
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("현재 비밀번호와 동일한 비밀번호로 변경할 수 없습니다");
    }

    @Test
    @DisplayName("비밀번호 변경 실패 - 존재하지 않는 회원 ID면 NOT_FOUND")
    void changePassword_memberNotFound_throwsNotFound() {
        UUID unknownId = UUID.randomUUID();
        given(memberRepository.findById(unknownId)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                memberService.changePassword(unknownId, new ChangePasswordRequest("OldP@ss1!", "NewP@ss2!"))
        )
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 회원입니다");
    }

    @Test
    @DisplayName("비밀번호 변경 성공 - passwordEncoder.encode()가 새 비밀번호로 호출됨")
    void changePassword_success_verifyEncodeCalledWithNewPassword() {
        Member member = MemberFixture.defaultMember();
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
        given(passwordEncoder.matches("OldP@ss1!", MemberFixture.DEFAULT_PASSWORD)).willReturn(true);
        given(passwordEncoder.matches("NewP@ss2!", MemberFixture.DEFAULT_PASSWORD)).willReturn(false);
        given(passwordEncoder.encode("NewP@ss2!")).willReturn("encodedNewPassword");

        memberService.changePassword(member.getId(), new ChangePasswordRequest("OldP@ss1!", "NewP@ss2!"));

        verify(passwordEncoder).encode("NewP@ss2!");
    }

    @Test
    @DisplayName("비밀번호 변경 실패 - 현재 비밀번호 불일치 시 encode()는 호출되지 않음")
    void changePassword_wrongCurrentPassword_encodeNeverCalled() {
        Member member = MemberFixture.defaultMember();
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
        given(passwordEncoder.matches("WrongP@ss!", MemberFixture.DEFAULT_PASSWORD)).willReturn(false);

        assertThatThrownBy(() ->
                memberService.changePassword(member.getId(), new ChangePasswordRequest("WrongP@ss!", "NewP@ss2!"))
        ).isInstanceOf(ServiceErrorException.class);

        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("비밀번호 변경 실패 - 동일 비밀번호 시 encode()는 호출되지 않음")
    void changePassword_sameAsCurrentPassword_encodeNeverCalled() {
        Member member = MemberFixture.defaultMember();
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
        given(passwordEncoder.matches("P@ssw0rd1!", MemberFixture.DEFAULT_PASSWORD)).willReturn(true);

        assertThatThrownBy(() ->
                memberService.changePassword(member.getId(), new ChangePasswordRequest("P@ssw0rd1!", "P@ssw0rd1!"))
        ).isInstanceOf(ServiceErrorException.class);

        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("팔로우 성공 - 승인된 강사이고 미팔로우 상태면 팔로우 저장 및 응답 반환")
    void followInstructor_success() {
        InstructorMember instructor = approvedInstructorMember();
        UUID instructorMemberId = InstructorMemberFixture.DEFAULT_MEMBER_ID;
        UUID followerId = UUID.randomUUID();
        given(instructorMemberRepository.findByMemberId(instructorMemberId)).willReturn(Optional.of(instructor));
        given(followRepository.existsByMemberIdAndMemberInstructorId(followerId, instructor.getId())).willReturn(false);

        FollowResponse response = memberService.followInstructor(followerId, instructorMemberId);

        assertThat(response.instructorId()).isEqualTo(instructorMemberId);
        assertThat(response.isFollowed()).isTrue();
        verify(followRepository).save(any(Follow.class));
    }

    @Test
    @DisplayName("팔로우 실패 - 본인을 팔로우하면 BAD_REQUEST")
    void followInstructor_selfFollow_throwsBadRequest() {
        UUID selfId = UUID.randomUUID();

        assertThatThrownBy(() -> memberService.followInstructor(selfId, selfId))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("본인을 팔로우 할 수 없습니다");

        verify(followRepository, never()).save(any());
    }

    @Test
    @DisplayName("팔로우 실패 - 존재하지 않는 강사 memberId면 NOT_FOUND")
    void followInstructor_instructorNotFound_throwsNotFound() {
        UUID followerId = UUID.randomUUID();
        UUID unknownInstructorId = UUID.randomUUID();
        given(instructorMemberRepository.findByMemberId(unknownInstructorId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.followInstructor(followerId, unknownInstructorId))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 강사입니다");

        verify(followRepository, never()).save(any());
    }

    @Test
    @DisplayName("팔로우 실패 - 승인 대기 중인 강사(PENDING)는 NOT_FOUND 처리")
    void followInstructor_instructorNotApproved_throwsNotFound() {
        UUID followerId = UUID.randomUUID();
        UUID instructorMemberId = InstructorMemberFixture.DEFAULT_MEMBER_ID;
        InstructorMember pendingInstructor = InstructorMemberFixture.defaultInstructorMember(); // PENDING
        given(instructorMemberRepository.findByMemberId(instructorMemberId)).willReturn(Optional.of(pendingInstructor));

        assertThatThrownBy(() -> memberService.followInstructor(followerId, instructorMemberId))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 강사입니다");

        verify(followRepository, never()).save(any());
    }

    @Test
    @DisplayName("팔로우 실패 - 이미 팔로우한 강사이면 CONFLICT")
    void followInstructor_alreadyFollowed_throwsConflict() {
        UUID followerId = UUID.randomUUID();
        UUID instructorMemberId = InstructorMemberFixture.DEFAULT_MEMBER_ID;
        InstructorMember instructor = approvedInstructorMember();
        given(instructorMemberRepository.findByMemberId(instructorMemberId)).willReturn(Optional.of(instructor));
        given(followRepository.existsByMemberIdAndMemberInstructorId(followerId, instructor.getId())).willReturn(true);

        assertThatThrownBy(() -> memberService.followInstructor(followerId, instructorMemberId))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("이미 팔로우한 강사입니다");

        verify(followRepository, never()).save(any());
    }

    @Test
    @DisplayName("팔로우 성공 - followRepository.save()가 정확히 한 번 호출됨")
    void followInstructor_success_saveCalledOnce() {
        UUID followerId = UUID.randomUUID();
        UUID instructorMemberId = InstructorMemberFixture.DEFAULT_MEMBER_ID;
        InstructorMember instructor = approvedInstructorMember();
        given(instructorMemberRepository.findByMemberId(instructorMemberId)).willReturn(Optional.of(instructor));
        given(followRepository.existsByMemberIdAndMemberInstructorId(followerId, instructor.getId())).willReturn(false);

        memberService.followInstructor(followerId, instructorMemberId);

        verify(followRepository).save(any(Follow.class));
    }

    private InstructorMember approvedInstructorMember() {
        InstructorMember im = InstructorMemberFixture.defaultInstructorMember();
        im.approve();
        ReflectionTestUtils.setField(im, "id", UUID.randomUUID());
        return im;
    }

    @Test
    @DisplayName("팔로우 해제 성공 - 팔로우 기록 삭제 후 isFollowed=false 반환")
    void unfollowInstructor_success() {
        UUID followerId = UUID.randomUUID();
        UUID instructorMemberId = InstructorMemberFixture.DEFAULT_MEMBER_ID;
        InstructorMember instructor = approvedInstructorMember();
        Follow follow = Follow.register(followerId, instructor.getId());
        given(instructorMemberRepository.findByMemberId(instructorMemberId)).willReturn(Optional.of(instructor));
        given(followRepository.findByMemberIdAndMemberInstructorId(followerId, instructor.getId())).willReturn(Optional.of(follow));

        FollowResponse response = memberService.unfollowInstructor(followerId, instructorMemberId);

        assertThat(response.instructorId()).isEqualTo(instructorMemberId);
        assertThat(response.isFollowed()).isFalse();
        verify(followRepository).delete(follow);
    }

    @Test
    @DisplayName("팔로우 해제 실패 - 존재하지 않는 강사 memberId면 NOT_FOUND")
    void unfollowInstructor_instructorNotFound_throwsNotFound() {
        UUID followerId = UUID.randomUUID();
        UUID unknownInstructorId = UUID.randomUUID();
        given(instructorMemberRepository.findByMemberId(unknownInstructorId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.unfollowInstructor(followerId, unknownInstructorId))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 강사입니다");

        verify(followRepository, never()).delete(any());
    }

    @Test
    @DisplayName("팔로우 해제 실패 - 미승인 강사(PENDING)는 NOT_FOUND 처리")
    void unfollowInstructor_instructorNotApproved_throwsNotFound() {
        UUID followerId = UUID.randomUUID();
        UUID instructorMemberId = InstructorMemberFixture.DEFAULT_MEMBER_ID;
        InstructorMember pendingInstructor = InstructorMemberFixture.defaultInstructorMember();
        given(instructorMemberRepository.findByMemberId(instructorMemberId)).willReturn(Optional.of(pendingInstructor));

        assertThatThrownBy(() -> memberService.unfollowInstructor(followerId, instructorMemberId))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 강사입니다");

        verify(followRepository, never()).delete(any());
    }

    @Test
    @DisplayName("팔로우 해제 실패 - 팔로우 기록이 없으면 NOT_FOUND")
    void unfollowInstructor_followNotFound_throwsNotFound() {
        UUID followerId = UUID.randomUUID();
        UUID instructorMemberId = InstructorMemberFixture.DEFAULT_MEMBER_ID;
        InstructorMember instructor = approvedInstructorMember();
        given(instructorMemberRepository.findByMemberId(instructorMemberId)).willReturn(Optional.of(instructor));
        given(followRepository.findByMemberIdAndMemberInstructorId(followerId, instructor.getId())).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.unfollowInstructor(followerId, instructorMemberId))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("팔로우한 기록이 없습니다");

        verify(followRepository, never()).delete(any());
    }

    @Test
    @DisplayName("팔로우 해제 성공 - followRepository.delete()가 정확히 한 번 호출됨")
    void unfollowInstructor_success_deleteCalledOnce() {
        UUID followerId = UUID.randomUUID();
        UUID instructorMemberId = InstructorMemberFixture.DEFAULT_MEMBER_ID;
        InstructorMember instructor = approvedInstructorMember();
        Follow follow = Follow.register(followerId, instructor.getId());
        given(instructorMemberRepository.findByMemberId(instructorMemberId)).willReturn(Optional.of(instructor));
        given(followRepository.findByMemberIdAndMemberInstructorId(followerId, instructor.getId())).willReturn(Optional.of(follow));

        memberService.unfollowInstructor(followerId, instructorMemberId);

        verify(followRepository).delete(follow);
    }

}
