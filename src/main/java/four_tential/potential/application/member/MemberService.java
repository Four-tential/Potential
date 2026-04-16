package four_tential.potential.application.member;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.course.course_category.CourseCategoryRepository;
import four_tential.potential.domain.member.member.Member;
import four_tential.potential.domain.member.member.MemberRepository;
import four_tential.potential.domain.member.member_onboard.MemberOnBoard;
import four_tential.potential.domain.member.member_onboard.MemberOnBoardRepository;
import four_tential.potential.domain.member.onboard_category.OnBoardCategory;
import four_tential.potential.domain.member.onboard_category.OnBoardCategoryRepository;
import four_tential.potential.presentation.member.model.request.OnBoardRequest;
import four_tential.potential.presentation.member.model.request.UpdateMyPageRequest;
import four_tential.potential.presentation.member.model.response.MyPageResponse;
import four_tential.potential.presentation.member.model.response.OnBoardResponse;
import four_tential.potential.presentation.member.model.response.UpdateMyPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static four_tential.potential.common.exception.domain.CourseExceptionEnum.ERR_NOT_FOUND_CATEGORY;
import static four_tential.potential.common.exception.domain.MemberExceptionEnum.*;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final MemberOnBoardRepository memberOnBoardRepository;
    private final OnBoardCategoryRepository onBoardCategoryRepository;
    private final CourseCategoryRepository courseCategoryRepository;

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

        // 카테고리 코드 유효성 검증
        List<String> categoryCodes = request.categoryCodes();
        categoryCodes.forEach(code -> {
            if (!courseCategoryRepository.existsByCode(code)) {
                throw new ServiceErrorException(ERR_NOT_FOUND_CATEGORY);
            }
        });

        // 온보딩 등록
        MemberOnBoard onBoard = MemberOnBoard.register(member, request.goal());
        memberOnBoardRepository.save(onBoard);

        // 카테고리 등록
        List<OnBoardCategory> categories = categoryCodes.stream()
                .map(code -> OnBoardCategory.register(member, code))
                .toList();
        onBoardCategoryRepository.saveAll(categories);

        // 온보딩 완료 처리
        member.completeOnboarding();

        return OnBoardResponse.register(onBoard, categoryCodes);
    }

    private String getProfileImageUrlOrDefault(Member member) {
        return member.getProfileImageUrl() != null ? member.getProfileImageUrl() : defaultProfileImageUrl;
    }
}
