package four_tential.potential.infra.security;

import four_tential.potential.infra.jwt.JwtFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                // Swagger, Actuator
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/actuator/**").permitAll()

                // 인증 (회원가입, 로그인, 토큰 재발급)
                .requestMatchers(HttpMethod.POST, "/v1/auth/signup", "/v1/auth/login", "/v1/auth/refresh").permitAll()

                // PortOne 웹훅 (외부 서버, 인증 없음)
                .requestMatchers(HttpMethod.POST, "/v1/webhooks/portone").permitAll()

                // PortOne 클라이언트 설정값 (결제 페이지 진입 전 필요)
                .requestMatchers(HttpMethod.GET, "/v1/payments/portone-config").permitAll()

                // 코스 공개 조회
                .requestMatchers(HttpMethod.GET, "/v1/courses", "/v1/courses/*").permitAll()

                // 후기 공개 조회
                .requestMatchers(HttpMethod.GET, "/v1/courses/*/reviews", "/v1/reviews/*").permitAll()

                // 강사 공개 프로필·코스 조회
                .requestMatchers(HttpMethod.GET, "/v1/instructors/*", "/v1/instructors/*/courses").permitAll()

                // 그 외 모든 요청은 인증 필요
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
