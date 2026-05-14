package four_tential.potential.presentation.order;

import four_tential.potential.application.order.OrderFacade;
import four_tential.potential.common.dto.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Order - 성능 테스트", description = "성능 테스트 데이터 관리 API (비운영 환경 전용)")
@Profile({"local", "dev", "perf"})
@RestController
@RequestMapping("/v1/orders")
@RequiredArgsConstructor
public class OrderPerformanceController {

    private final OrderFacade orderFacade;

    @Operation(summary = "성능 테스트 데이터 일괄 삭제", description = "성능 테스트를 위해 생성된 회원, 코스, 주문, Redis 대기열 데이터를 일괄 삭제합니다.")
    @DeleteMapping("/performance-test-data")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<Void>> deletePerformanceTestData() {
        orderFacade.deletePerformanceTestData();
        return ResponseEntity.ok(BaseResponse.success(HttpStatus.OK.name(), "성능 테스트 데이터 삭제 성공", null));
    }
}
