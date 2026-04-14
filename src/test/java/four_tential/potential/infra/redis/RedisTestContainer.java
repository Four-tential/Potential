package four_tential.potential.infra.redis;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.utility.DockerImageName;

// 모든 Redis 통합 테스트
// Redis 컨테이너를 JVM 내에서 한 번만 기동하고 전체 테스트가 공유 (extend 해서 사용 할 것)
@SpringBootTest
@ActiveProfiles("test")
public abstract class RedisTestContainer {
    // static 블록으로 JVM 전체에서 단 한 번만 컨테이너 기동, Singleton
    static final RedisContainer redisContainer;

    static {
        redisContainer = new RedisContainer(DockerImageName.parse("redis:8.6.1"))
                .withExposedPorts(6379);
        redisContainer.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> redisContainer.getHost());
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
    }
}
