package four_tential.potential.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ServiceErrorException extends RuntimeException {
    private final HttpStatus httpStatus;
    private final ServiceErrorCode errorCode;

    public ServiceErrorException(ServiceErrorCode serviceErrorCode) {
        super(serviceErrorCode.getMessage());
        this.httpStatus = serviceErrorCode.getHttpStatus();
        this.errorCode = serviceErrorCode;
    }
}
