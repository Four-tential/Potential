package four_tential.potential.common.exception.domain;

import four_tential.potential.common.exception.ServiceErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ChatbotExceptionEnum implements ServiceErrorCode {

    ERR_CHATBOT_RATE_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다, 잠시 후 다시 시도해주세요"),
    ERR_CHATBOT_UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다");

    private final HttpStatus httpStatus;
    private final String message;

    ChatbotExceptionEnum(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
