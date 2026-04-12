package four_tential.potential.common.exception;

import org.springframework.http.HttpStatus;

public interface ServiceErrorCode {
    HttpStatus getHttpStatus();
    String getMessage();
}
