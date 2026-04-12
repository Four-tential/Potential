package four_tential.potential.common.dto;

public record BaseResponse<T> (
        boolean success
        , String status
        , String message
        , T data
) {
    public static <T> BaseResponse<T> success(String status, String message, T data) {
        return new BaseResponse<>(true, status, message, data);
    }

    public static <T> BaseResponse<T> fail(String status, String message) {
        return new BaseResponse<>(false, status, message, null);
    }
}
