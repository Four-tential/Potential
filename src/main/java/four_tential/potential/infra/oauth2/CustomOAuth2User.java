package four_tential.potential.infra.oauth2;

import four_tential.potential.domain.member.member.MemberRole;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
public class CustomOAuth2User implements OAuth2User {

    private final UUID memberId;
    private final String email;
    private final MemberRole role;
    private final boolean hasOnboarding;
    private final boolean requiresPhoneSetup;
    private final Map<String, Object> attributes;

    public CustomOAuth2User(
            UUID memberId,
            String email,
            MemberRole role,
            boolean hasOnboarding,
            boolean requiresPhoneSetup,
            Map<String, Object> attributes
    ) {
        this.memberId = memberId;
        this.email = email;
        this.role = role;
        this.hasOnboarding = hasOnboarding;
        this.requiresPhoneSetup = requiresPhoneSetup;
        this.attributes = attributes;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.name()));
    }

    @Override
    public @NonNull String getName() {
        return memberId.toString();
    }
}
