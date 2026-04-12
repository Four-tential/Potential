package four_tential.potential.common.exception;

import four_tential.potential.common.dto.BaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ServiceErrorException.class)
    public ResponseEntity<BaseResponse<Void>> handleServiceErrorException(ServiceErrorException e) {
        log.error(e.getMessage(), e);
        return ResponseEntity.status(e.getHttpStatus()).body(BaseResponse.fail(e.getHttpStatus().name(), e.getMessage()));
    }

    @ExceptionHandler(value = IllegalArgumentException.class)
    public ResponseEntity<BaseResponse<Void>> IllegalArgumentExceptionHandler(IllegalArgumentException e) {
        log.error("요청 값 유효성 에러 발생 : ", e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(BaseResponse.fail(HttpStatus.BAD_REQUEST.name(), "요청 값이 유효하지 않습니다"));
    }

    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<Void>> MethodArgumentNotValidExceptionHandler(MethodArgumentNotValidException e) {
        log.error("데이터 유효성 에러 발생 : ", e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(BaseResponse.fail(HttpStatus.BAD_REQUEST.name(), e.getAllErrors().getFirst().getDefaultMessage()));
    }

    @ExceptionHandler(value = HttpMessageNotReadableException.class)
    public ResponseEntity<BaseResponse<Void>> HttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.error("데이터 JSON 변환 에러 발생 : ", e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(BaseResponse.fail(HttpStatus.BAD_REQUEST.name(), "데이터 처리에 문제가 발생하였습니다"));
    }

    @ExceptionHandler(value = DataIntegrityViolationException.class)
    public ResponseEntity<BaseResponse<Void>> DataIntegrityViolationExceptionHandler(DataIntegrityViolationException e) {
        log.error("데이터 저장 실패 발생 : ", e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(BaseResponse.fail(HttpStatus.BAD_REQUEST.name(), "데이터 저장에 실패하였습니다"));
    }

    @ExceptionHandler(value = NoResourceFoundException.class)
    public ResponseEntity<BaseResponse<Void>> NoResourceFoundExceptionHandler(NoResourceFoundException e) {
        log.error("리소스 찾기 실패 : ", e);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(BaseResponse.fail(HttpStatus.NOT_FOUND.name(), "주소를 다시 한번 확인해주세요"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Void>> handleCriticalErrorException(Exception e) {
        log.error("서버 에러 발생", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(BaseResponse.fail(HttpStatus.INTERNAL_SERVER_ERROR.name(), "서버 오류로 인해 잠시 후 다시 시도하시기 바랍니다"));
    }
}
