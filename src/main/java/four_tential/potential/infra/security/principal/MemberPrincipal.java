package four_tential.potential.infra.security.principal;

import java.util.UUID;

public record MemberPrincipal(
        UUID memberId,
        String email,
        String role
) {
}
