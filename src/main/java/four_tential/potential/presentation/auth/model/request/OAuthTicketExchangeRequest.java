package four_tential.potential.presentation.auth.model.request;

import jakarta.validation.constraints.NotBlank;

public record OAuthTicketExchangeRequest(
        @NotBlank(message = "ticket 이 필요합니다") String ticket
) {
}
