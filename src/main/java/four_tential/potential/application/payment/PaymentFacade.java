package four_tential.potential.application.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentFacade {
    private final PaymentService paymentService;
    private final WebhookService webhookService;
}
