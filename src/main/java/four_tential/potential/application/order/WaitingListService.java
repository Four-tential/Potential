package four_tential.potential.application.order;

import four_tential.potential.domain.order.WaitingListRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class
WaitingListService {

    private final WaitingListRepository waitingListRepository;

}
