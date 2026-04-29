package four_tential.potential.infra.ai.chatbot;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
public class ChatbotService {

    private final ChatClient chatbotChatClient;

    public ChatbotService(@Qualifier("chatbotChatClient") ChatClient chatbotChatClient) {
        this.chatbotChatClient = chatbotChatClient;
    }

    public String ask(String question) {
        return chatbotChatClient.prompt()
                .user(question)
                .call()
                .content();
    }
}
