package four_tential.potential.infra.ai.chatbot;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.ChatbotExceptionEnum;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
public class ChatbotService {

    private final ChatClient chatbotChatClient;
    private final ChatbotRateLimiter chatbotRateLimiter;

    public ChatbotService(
            @Qualifier("chatbotChatClient") ChatClient chatbotChatClient,
            ChatbotRateLimiter chatbotRateLimiter
    ) {
        this.chatbotChatClient = chatbotChatClient;
        this.chatbotRateLimiter = chatbotRateLimiter;
    }

    public String ask(MemberPrincipal principal, String question) {
        if (principal == null) {
            throw new ServiceErrorException(ChatbotExceptionEnum.ERR_CHATBOT_UNAUTHENTICATED);
        }
        chatbotRateLimiter.check(principal.memberId(), principal.role());

        return chatbotChatClient.prompt()
                .user(question)
                .call()
                .content();
    }
}
