package four_tential.potential.presentation.order;

import four_tential.potential.application.order.OrderFacade;
import four_tential.potential.common.dto.BaseResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderPerformanceControllerTest {

    @Mock
    private OrderFacade orderFacade;

    @InjectMocks
    private OrderPerformanceController orderPerformanceController;

    @Test
    @DisplayName("성능 테스트 데이터 삭제 요청 시 200 OK를 반환하고 facade 메서드를 호출한다")
    void deletePerformanceTestData_success() {
        // when
        ResponseEntity<BaseResponse<Void>> response = orderPerformanceController.deletePerformanceTestData();

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("성능 테스트 데이터 삭제 성공");
        
        verify(orderFacade).deletePerformanceTestData();
    }
}
