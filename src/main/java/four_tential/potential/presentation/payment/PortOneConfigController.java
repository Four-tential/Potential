package four_tential.potential.presentation.payment;

import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.infra.portone.PortOneProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PortOne 클라이언트 설정값 제공 컨트롤러
 * 결제 테스트 HTML 에서 storeId, channelKey 를 가져올 때 사용
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/payments")
public class PortOneConfigController {

    private final PortOneProperties portOneProperties;

    /**
     * PortOne 클라이언트 설정값 조회
     * storeId, channelKey 만 반환 (API 시크릿 제외)
     *
     * @return PortOne 클라이언트 설정값
     */
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
