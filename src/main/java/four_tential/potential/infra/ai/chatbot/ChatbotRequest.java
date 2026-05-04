package four_tential.potential.infra.ai.chatbot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatbotRequest(
        @NotBlank(message = "질문을 입력해주세요")
        @Size(max = 500, message = "질문은 500자 이하여야 합니다")
        String question
) {}
