package four_tential.potential.infra.ai.chatbot;

import jakarta.validation.constraints.NotBlank;

public record ChatbotRequest(@NotBlank(message = "질문을 입력해주세요") String question) {}
