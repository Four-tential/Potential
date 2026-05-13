package four_tential.potential.presentation.order.dto;

public sealed interface OrderPlaceResult
    permits OrderCreateResponse, OrderWaitingResponse{
}
