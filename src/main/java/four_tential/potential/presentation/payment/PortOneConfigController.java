package four_tential.potential.presentation.payment;

import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.infra.portone.PortOneProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PortOne 클라이언트 설정값 제공 컨트롤러
 * 결제 테스트 HTML 또는 프론트엔드 SDK 초기화 시 사용
 */
@Tag(name = "결제 설정", description = "PortOne 클라이언트 결제창 초기화용 공개 설정 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/payments")
public class PortOneConfigController {

    private final PortOneProperties portOneProperties;

    /**
     * PortOne 클라이언트 설정값 조회
     * storeId, channelKey만 반환 (API Secret 제외)
     *
     * @return PortOne 클라이언트 설정값
     */
    @Operation(
            summary = "PortOne 클라이언트 설정 조회",
            description = """
                    클라이언트 결제창 호출에 필요한 PortOne 공개 설정값을 조회합니다.

                    - storeId와 channelKey만 반환합니다.
                    - API Secret 등 서버 전용 비밀키는 노출하지 않습니다.
                    - 프론트엔드 SDK 초기화 또는 결제 테스트 페이지에서 사용합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PortOne 공개 설정 조회 성공")
    })
    @GetMapping("/portone-config")
    public ResponseEntity<BaseResponse<PortOneConfigResponse>> getPortOneConfig() {
        PortOneConfigResponse response = new PortOneConfigResponse(
                portOneProperties.getStoreId(),
                portOneProperties.getChannelKey()
        );
        return ResponseEntity.ok(BaseResponse.success("OK", "PortOne 설정값 조회 성공", response));
    }

    /**
     * PortOne 클라이언트 설정값 응답 DTO
     */
    public record PortOneConfigResponse(String storeId, String channelKey) {
    }
}
