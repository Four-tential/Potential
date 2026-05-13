package four_tential.potential.presentation.auth.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record OAuthTicketExchangeRequest(
        @Schema(description = "OAuth2 redirect URL 쿼리에서 받은 1회용 티켓 (TTL 60초)",
                example = "8f3c2a4d-7b9e-4f0a-8c11-22aa33bb44cc", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "ticket 이 필요합니다") String ticket
) {
}
