package four_tential.potential.presentation.order;

import four_tential.potential.application.order.OrderFacade;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.domain.order.OrderStatus;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import four_tential.potential.presentation.order.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

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

    @Test
    @DisplayName("주문 상세 조회 성공 시 200 OK와 주문 정보를 반환한다")
    void getOrderDetails_success() {
        // given
        LocalDateTime now = LocalDateTime.now();
        OrderDetailResponse expectedResponse = new OrderDetailResponse(
                ORDER_ID,
                COURSE_ID,
                "테스트 강의",
                1,
                BigInteger.valueOf(10000),
                BigInteger.valueOf(10000),
                OrderStatus.PENDING,
                now,
                now,
                now.plusMinutes(10)
        );
        given(orderFacade.getOrderDetails(ORDER_ID, MEMBER_ID)).willReturn(expectedResponse);

        // when
        ResponseEntity<BaseResponse<OrderDetailResponse>> response = orderController.getOrderDetails(studentPrincipal, ORDER_ID);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        OrderDetailResponse actualResponse = response.getBody().data();
        assertThat(actualResponse).isNotNull();
        assertThat(actualResponse.orderId()).isEqualTo(ORDER_ID);
        assertThat(actualResponse.courseId()).isEqualTo(COURSE_ID);
        assertThat(actualResponse.titleSnap()).isEqualTo("테스트 강의");
        assertThat(actualResponse.orderCount()).isEqualTo(1);
        assertThat(actualResponse.priceSnap()).isEqualTo(BigInteger.valueOf(10000));
        assertThat(actualResponse.totalPriceSnap()).isEqualTo(BigInteger.valueOf(10000));
        assertThat(actualResponse.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(actualResponse.createdAt()).isEqualTo(now);
        assertThat(actualResponse.updatedAt()).isEqualTo(now);
        assertThat(actualResponse.expireAt()).isEqualTo(now.plusMinutes(10));
    }

    @Test
    @DisplayName("나의 주문 목록 조회 성공 시 200 OK와 페이징된 정보를 반환한다")
    void getMyOrders_success() {
        // given
        LocalDateTime now = LocalDateTime.now();
        OrderMyListResponse orderResponse = new OrderMyListResponse(
                ORDER_ID,
                COURSE_ID,
                "테스트 강의",
                BigInteger.valueOf(10000),
                OrderStatus.PAID,
                now,
                now,
                now.plusMinutes(10)
        );
        PageResponse<OrderMyListResponse> expectedPageResponse = new PageResponse<>(
                List.of(orderResponse),
                0,
                1,
                1,
                10,
                true
        );
        Pageable pageable = PageRequest.of(0, 10);
        given(orderFacade.getMyOrders(eq(MEMBER_ID), eq(pageable))).willReturn(expectedPageResponse);

        // when
        ResponseEntity<BaseResponse<PageResponse<OrderMyListResponse>>> response =
                orderController.getMyOrders(studentPrincipal, pageable);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        PageResponse<OrderMyListResponse> actualResponse = response.getBody().data();
        assertThat(actualResponse.content()).hasSize(1);
        assertThat(actualResponse.content().get(0).orderId()).isEqualTo(ORDER_ID);
        assertThat(actualResponse.totalElements()).isEqualTo(1);
        assertThat(actualResponse.isLast()).isTrue();

        verify(orderFacade).getMyOrders(MEMBER_ID, pageable);
    }

    @Test
    @DisplayName("주문 취소 요청 시 200 OK와 취소 정보를 반환한다")
    void cancelOrder_success() {
        // given
        OrderCancelResponse expectedResponse = new OrderCancelResponse(
                ORDER_ID,
                "CANCELLED",
                LocalDateTime.now()
        );
        OrderCancelRequest request = new OrderCancelRequest(1);
        given(orderFacade.cancelOrder(ORDER_ID, MEMBER_ID, request)).willReturn(expectedResponse);

        // when
        ResponseEntity<BaseResponse<OrderCancelResponse>> response = 
                orderController.cancelOrder(studentPrincipal, ORDER_ID, request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().orderId()).isEqualTo(ORDER_ID);
        assertThat(response.getBody().message()).isEqualTo("주문 취소 요청 성공");
        verify(orderFacade).cancelOrder(ORDER_ID, MEMBER_ID, request);
    }
}
