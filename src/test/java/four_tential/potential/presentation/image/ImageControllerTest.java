package four_tential.potential.presentation.image;

import four_tential.potential.common.exception.GlobalExceptionHandler;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.infra.jwt.JwtFilter;
import four_tential.potential.infra.s3.ImageType;
import four_tential.potential.infra.s3.PresignedUrlResult;
import four_tential.potential.infra.s3.S3Service;
import four_tential.potential.infra.security.SecurityConfig;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import four_tential.potential.presentation.image.model.request.PresignedUrlRequest;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static four_tential.potential.common.exception.domain.MemberExceptionEnum.ERR_INVALID_IMAGE_FILE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImageController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class ImageControllerTest {

    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID COURSE_ID = UUID.randomUUID();
    private static final MemberPrincipal INSTRUCTOR_PRINCIPAL =
            new MemberPrincipal(MEMBER_ID, "instructor@example.com", "ROLE_INSTRUCTOR");
    private static final MemberPrincipal MEMBER_PRINCIPAL =
            new MemberPrincipal(MEMBER_ID, "member@example.com", "ROLE_MEMBER");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private S3Service s3Service;

    @MockitoBean
    private JwtFilter jwtFilter;

    @BeforeEach
    void setUp() throws Exception {
        willAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).given(jwtFilter).doFilter(any(), any(), any());
    }

    private RequestPostProcessor instructorAuth() {
        return authentication(
                new UsernamePasswordAuthenticationToken(
                        INSTRUCTOR_PRINCIPAL, null,
                        List.of(new SimpleGrantedAuthority("ROLE_INSTRUCTOR"))
                )
        );
    }

    private RequestPostProcessor memberAuth() {
        return authentication(
                new UsernamePasswordAuthenticationToken(
                        MEMBER_PRINCIPAL, null,
                        List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))
                )
        );
    }

    @Test
    @DisplayName("Presigned URL 발급 - 코스 이미지 성공 (resourceId 포함)")
    void getPresignedUrls_course_success() throws Exception {
        PresignedUrlRequest request = new PresignedUrlRequest(
                ImageType.COURSE, COURSE_ID, List.of("image/jpeg", "image/png")
        );

        given(s3Service.generatePresignedUrls(eq("course-image"), eq(COURSE_ID), eq(List.of("image/jpeg", "image/png"))))
                .willReturn(List.of(
                        new PresignedUrlResult("https://s3.presigned/1", "https://cdn.example.com/course-image/" + COURSE_ID + "/1.jpg"),
                        new PresignedUrlResult("https://s3.presigned/2", "https://cdn.example.com/course-image/" + COURSE_ID + "/2.png")
                ));

        mockMvc.perform(post("/v1/images/presigned-urls")
                        .with(instructorAuth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Presigned URL 발급 성공"))
                .andExpect(jsonPath("$.data.urls").isArray())
                .andExpect(jsonPath("$.data.urls.length()").value(2));

        then(s3Service).should().generatePresignedUrls(
                eq("course-image"), eq(COURSE_ID), eq(List.of("image/jpeg", "image/png"))
        );
    }

    @Test
    @DisplayName("Presigned URL 발급 - 프로필 이미지 성공 (resourceId 자동 주입)")
    void getPresignedUrls_profile_success() throws Exception {
        PresignedUrlRequest request = new PresignedUrlRequest(
                ImageType.PROFILE, null, List.of("image/jpeg")
        );

        given(s3Service.generatePresignedUrls(eq("profile-image"), eq(MEMBER_ID), eq(List.of("image/jpeg"))))
                .willReturn(List.of(
                        new PresignedUrlResult("https://s3.presigned/1", "https://cdn.example.com/profile-image/" + MEMBER_ID + "/1.jpg")
                ));

        mockMvc.perform(post("/v1/images/presigned-urls")
                        .with(memberAuth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        then(s3Service).should().generatePresignedUrls(
                eq("profile-image"), eq(MEMBER_ID), eq(List.of("image/jpeg"))
        );
    }

    @Test
    @DisplayName("Presigned URL 발급 - 강사 신청 이미지 성공 (resourceId 자동 주입)")
    void getPresignedUrls_instructor_success() throws Exception {
        PresignedUrlRequest request = new PresignedUrlRequest(
                ImageType.INSTRUCTOR, null, List.of("image/jpeg")
        );

        given(s3Service.generatePresignedUrls(eq("instructor-image"), eq(MEMBER_ID), eq(List.of("image/jpeg"))))
                .willReturn(List.of(
                        new PresignedUrlResult("https://s3.presigned/1", "https://cdn.example.com/instructor-image/" + MEMBER_ID + "/1.jpg")
                ));

        mockMvc.perform(post("/v1/images/presigned-urls")
                        .with(memberAuth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        then(s3Service).should().generatePresignedUrls(
                eq("instructor-image"), eq(MEMBER_ID), eq(List.of("image/jpeg"))
        );
    }

    @Test
    @DisplayName("Presigned URL 발급 - 잘못된 Content-Type")
    void getPresignedUrls_invalidContentType() throws Exception {
        PresignedUrlRequest request = new PresignedUrlRequest(
                ImageType.COURSE, COURSE_ID, List.of("application/pdf")
        );

        given(s3Service.generatePresignedUrls(any(), any(), any()))
                .willThrow(new ServiceErrorException(ERR_INVALID_IMAGE_FILE));

        mockMvc.perform(post("/v1/images/presigned-urls")
                        .with(instructorAuth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Presigned URL 발급 - type 누락 시 400")
    void getPresignedUrls_missingType() throws Exception {
        String body = """
                {"contentTypes": ["image/jpeg"]}
                """;

        mockMvc.perform(post("/v1/images/presigned-urls")
                        .with(instructorAuth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Presigned URL 발급 - COURSE 타입 resourceId 누락 시 400")
    void getPresignedUrls_course_missingResourceId() throws Exception {
        String body = """
                {"type": "COURSE", "contentTypes": ["image/jpeg"]}
                """;

        mockMvc.perform(post("/v1/images/presigned-urls")
                        .with(instructorAuth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Presigned URL 발급 - contentTypes 누락 시 400")
    void getPresignedUrls_missingContentTypes() throws Exception {
        String body = """
                {"type": "COURSE"}
                """;

        mockMvc.perform(post("/v1/images/presigned-urls")
                        .with(instructorAuth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
