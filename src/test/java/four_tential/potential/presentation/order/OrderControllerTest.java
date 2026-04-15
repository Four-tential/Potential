package four_tential.potential.presentation.order;

import four_tential.potential.application.order.OrderFacade;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import four_tential.potential.presentation.order.dto.OrderCreateRequest;
import four_tential.potential.presentation.order.dto.OrderCreateResponse;
import four_tential.potential.presentation.order.dto.OrderPlaceResult;
import four_tential.potential.presentation.order.dto.OrderWaitingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderFacade orderFacade;

    @InjectMocks
    private OrderController orderController;

    private MemberPrincipal studentPrincipal;
    private OrderCreateRequest orderCreateRequest;
    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID COURSE_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        studentPrincipal = new MemberPrincipal(MEMBER_ID, "student@test.com", "ROLE_STUDENT");
        orderCreateRequest = new OrderCreateRequest(
                COURSE_ID,
                1,
                BigInteger.valueOf(10000),
                "테스트 강의"
        );
    }

    @Test
    @DisplayName("주문 생성 성공 시 201 Created와 주문 정보를 반환한다")
    void createOrder_success() {
        // given
        OrderCreateResponse expectedResponse = new OrderCreateResponse(
                ORDER_ID,
                "PENDING",
                LocalDateTime.now().plusMinutes(10),
                "주문이 성공적으로 생성되었습니다."
        );
        given(orderFacade.placeOrder(MEMBER_ID, orderCreateRequest)).willReturn(expectedResponse);

        // when
        ResponseEntity<BaseResponse<OrderPlaceResult>> response = orderController.createOrder(studentPrincipal, orderCreateRequest);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isInstanceOf(OrderCreateResponse.class);
        
        OrderCreateResponse actualResponse = (OrderCreateResponse) response.getBody().data();
        assertThat(actualResponse.orderId()).isEqualTo(ORDER_ID);
        assertThat(actualResponse.message()).isEqualTo(expectedResponse.message());
    }

    @Test
    @DisplayName("잔여석 부족으로 대기열 진입 시 202 Accepted와 대기 정보를 반환한다")
    void createOrder_waiting() {
        // given
        OrderWaitingResponse expectedResponse = new OrderWaitingResponse(
                COURSE_ID,
                "WAITING",
                "대기열에 등록되었습니다."
        );
        given(orderFacade.placeOrder(MEMBER_ID, orderCreateRequest)).willReturn(expectedResponse);

        // when
        ResponseEntity<BaseResponse<OrderPlaceResult>> response = orderController.createOrder(studentPrincipal, orderCreateRequest);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isInstanceOf(OrderWaitingResponse.class);

        OrderWaitingResponse actualResponse = (OrderWaitingResponse) response.getBody().data();
        assertThat(actualResponse.courseId()).isEqualTo(COURSE_ID);
        assertThat(actualResponse.message()).isEqualTo(expectedResponse.message());
    }
}
