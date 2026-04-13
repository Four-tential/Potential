package four_tential.potential.application.payment;

import four_tential.potential.domain.payment.repository.WebhookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookRepository webhookRepository;
}
