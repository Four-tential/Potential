package four_tential.potential.infra.ai.chatbot;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.ChatbotExceptionEnum;
import four_tential.potential.infra.ai.AiMetrics;
import four_tential.potential.infra.ai.advisor.AiLoggingAdvisor;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
@Slf4j
public class ChatbotService {

    private final ChatClient chatbotChatClient;
    private final ChatbotRateLimiter chatbotRateLimiter;
    private final AiMetrics aiMetrics;

    public ChatbotService(
            @Qualifier("chatbotChatClient") ChatClient chatbotChatClient,
            ChatbotRateLimiter chatbotRateLimiter,
            AiMetrics aiMetrics
    ) {
        this.chatbotChatClient = chatbotChatClient;
        this.chatbotRateLimiter = chatbotRateLimiter;
        this.aiMetrics = aiMetrics;
    }

    // 챗봇 요청을 처리하면서 요청 결과와 전체 처리 시간을 메트릭/로그로 함께 남김
    public String ask(MemberPrincipal principal, String question) {
        long startedAt = System.nanoTime();
        String result = "success";
        int questionLength = question == null ? 0 : question.length();

        try {
            if (principal == null) {
                result = "unauthenticated";
                throw new ServiceErrorException(ChatbotExceptionEnum.ERR_CHATBOT_UNAUTHENTICATED);
            }

            chatbotRateLimiter.check(principal.memberId(), principal.role());

            // 질문 원문 대신 길이만 로그에 남겨 개인정보와 로그 폭증 위험을 줄임
            log.info(
                    "[CHATBOT] 요청 수신. memberId={} questionLength={}",
                    principal.memberId(),
                    questionLength
            );

            String answer = chatbotChatClient.prompt()
                    .user(question)
                    .advisors(advisor -> advisor.param(AiLoggingAdvisor.CONTEXT_FEATURE, AiLoggingAdvisor.FEATURE_CHATBOT))
                    .call()
                    .content();

            // 응답 원문 대신 길이만 기록해서 응답 규모와 처리 흐름만 추적
            log.info(
                    "[CHATBOT] 응답 완료. memberId={} questionLength={} answerLength={}",
                    principal.memberId(),
                    questionLength,
                    answer == null ? 0 : answer.length()
            );

            return answer;

        // 인증 실패와 rate limit 같은 서비스 레벨 예외를 결과값으로 구분해서 기록
        } catch (ServiceErrorException e) {
            if (e.getHttpStatus() == HttpStatus.TOO_MANY_REQUESTS) {
                result = "rate_limited";
            } else if (e.getHttpStatus() == HttpStatus.UNAUTHORIZED) {
                result = "unauthenticated";
            } else {
                result = "fail";
            }

            log.warn(
                    "[CHATBOT] 요청 실패. memberId={} questionLength={} result={} errorType={}",
                    principal != null ? principal.memberId() : null,
                    questionLength,
                    result,
                    e.getClass().getSimpleName(),
                    e
            );
            throw e;

        // 모델 호출 또는 기타 예기치 못한 실패를 일반 fail 결과로 기록
        } catch (RuntimeException e) {
            result = "fail";

            log.warn(
                    "[CHATBOT] 요청 실패. memberId={} questionLength={} result={} errorType={}",
                    principal != null ? principal.memberId() : null,
                    questionLength,
                    result,
                    e.getClass().getSimpleName(),
                    e
            );
            throw e;

        // 성공/실패 여부와 상관없이 모든 요청의 건수와 전체 처리 시간을 기록
        } finally {
            aiMetrics.recordChatbotRequest(result);
            aiMetrics.recordChatbotRequestDuration(result, System.nanoTime() - startedAt);
        }
    }
}
