package four_tential.potential;

import org.junit.jupiter.api.Test;
import org.redisson.client.RedisClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class PotentialApplicationTests {
    @MockitoBean
    RedisClient redisClient;

    @Test
    void contextLoads() {
    }
}
