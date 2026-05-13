package four_tential.potential.application.order.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RQueue;
import org.redisson.api.RedissonClient;

import static four_tential.potential.application.order.OrderConstants.ORDER_EXPIRATION_QUEUE;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderEventListenerTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RQueue<String> destinationQueue;

    @Mock
    private RDelayedQueue<String> delayedQueue;

    @InjectMocks
    private OrderEventListener orderEventListener;

    @Test
    @DisplayName("destroy() 호출 시 RDelayedQueue.destroy()가 실행되어야 함")
    @SuppressWarnings("unchecked")
    void destroy() {
        // given
        doReturn(destinationQueue).when(redissonClient).getQueue(ORDER_EXPIRATION_QUEUE);
        given(redissonClient.getDelayedQueue(destinationQueue)).willReturn(delayedQueue);
        
        // PostConstruct 수동 호출 (delayedQueue 필드 초기화)
        orderEventListener.init();

        // when
        orderEventListener.destroy();

        // then
        verify(delayedQueue).destroy();
    }
}
