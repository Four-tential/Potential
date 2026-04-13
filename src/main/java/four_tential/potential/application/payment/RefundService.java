package four_tential.potential.application.payment;

import four_tential.potential.domain.payment.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefundService {

    private final RefundRepository refundRepository;
}
