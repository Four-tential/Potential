package four_tential.potential.infra.ai.chatbot;

import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "챗봇", description = "RAG 기반 FAQ 챗봇 API")
@RestController
@RequestMapping("/v1/chatbot")
@Profile("!test")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService chatbotService;

    @Operation(summary = "챗봇 질문", description = "정책 문서 기반으로 질문에 답변")
    @PostMapping
    public ResponseEntity<BaseResponse<ChatbotResponse>> ask(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody ChatbotRequest request
    ) {
        String answer = chatbotService.ask(principal, request.question());
        return ResponseEntity.ok(BaseResponse.success(
                "200",
                "답변 완료",
                new ChatbotResponse(answer)
        ));
    }
}
