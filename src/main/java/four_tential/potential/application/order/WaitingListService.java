package four_tential.potential.application.order;

import four_tential.potential.domain.order.WaitingListRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class
WaitingListService {

    private final WaitingListRepository waitingListRepository;

}
