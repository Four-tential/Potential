package four_tential.potential.infra.redis;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.redisson.Redisson;
import org.springframework.util.FileSystemUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.BindMode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@DisplayName("Redis AOF 영속성 테스트")
public class RedisAofPersistenceTest {

    private static Path tempDir;
    private static final String REDIS_IMAGE = "redis:7-alpine";

    @BeforeAll
    static void setup() throws Exception {
        // 데이터가 저장될 임시 디렉토리 생성 (Docker Volume 역할)
        tempDir = Files.createTempDirectory("redis-data-aof");
    }

    @AfterAll
    static void cleanup() {
        // 테스트 종료 후 임시 디렉토리 및 내부 파일(AOF 등) 삭제 시도
        // CI 환경(Linux)에서 Docker가 생성한 파일의 권한 문제로 AccessDeniedException이 발생할 수 있음
        if (tempDir != null) {
            try {
                FileSystemUtils.deleteRecursively(tempDir);
            } catch (IOException e) {
                log.warn("테스트 임시 디렉토리 삭제 실패 (권한 문제 등): {}. CI 환경에서는 무시될 수 있습니다.", e.getMessage());
            }
        }
    }

    @Test
    @DisplayName("Redis가 재시작되어도 AOF 파일 덕분에 선점된 정원과 대기열 데이터가 유지되어야 한다")
    void testAofPersistenceWithRestart() throws Exception {
        // given
        UUID courseId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        int initialCapacity = 50;
        String capacityKey = RedisConstants.COURSE_CAPACITY_PREFIX + courseId;
        String waitingKey = RedisConstants.WAITING_LIST_PREFIX + courseId;

        // STEP 1: 첫 번째 Redis 컨테이너 시작 (AOF 활성화 및 볼륨 바인딩)
        try (GenericContainer<?> redis1 = createRedisContainer()) {
            redis1.start();
            RedissonClient client1 = createRedissonClient(redis1.getHost(), redis1.getMappedPort(6379));

            // 데이터 선점 수행
            RAtomicLong capacity = client1.getAtomicLong(capacityKey);
            capacity.set(initialCapacity);

            RScoredSortedSet<String> waitingList = client1.getScoredSortedSet(waitingKey, StringCodec.INSTANCE);
            waitingList.add(System.currentTimeMillis(), memberId.toString());

            // AOF 버퍼를 디스크에 즉시 동기화하기 위해 appendfsync 설정을 일시적으로 always로 변경
            redis1.execInContainer("redis-cli", "config", "set", "appendfsync", "always");
            // 더미 쓰기를 수행하여 AOF fsync를 강제로 트리거
            client1.getBucket("__sync_trigger__", StringCodec.INSTANCE).set("1");
            // 다시 원래의 성능 지향 설정(everysec)으로 복구 (선택 사항)
            redis1.execInContainer("redis-cli", "config", "set", "appendfsync", "everysec");

            client1.shutdown();
            redis1.stop(); 
            // 컨테이너가 중지되어도 tempDir(호스트 경로)에는 데이터가 동기화된 AOF 파일이 남아있음
        }

        // STEP 2: 동일한 볼륨을 사용하는 새로운 Redis 컨테이너 시작
        try (GenericContainer<?> redis2 = createRedisContainer()) {
            redis2.start();
            RedissonClient client2 = createRedissonClient(redis2.getHost(), redis2.getMappedPort(6379));

            // then: 데이터가 무사히 복구되었는지 확인
            RAtomicLong capacity = client2.getAtomicLong(capacityKey);
            RScoredSortedSet<String> waitingList = client2.getScoredSortedSet(waitingKey, StringCodec.INSTANCE);

            assertThat(capacity.get())
                    .as("재시작 후에도 잔여석 수치가 유지되어야 함")
                    .isEqualTo(initialCapacity);
            
            assertThat(waitingList.contains(memberId.toString()))
                    .as("재시작 후에도 대기열에 멤버가 존재해야 함")
                    .isTrue();

            client2.shutdown();
        }
    }

    /**
     * AOF가 활성화되고 호스트 디렉토리가 매핑된 Redis 컨테이너 생성
     */
    private GenericContainer<?> createRedisContainer() {
        return new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
                .withExposedPorts(6379)
                .withCommand("redis-server", "--appendonly", "yes") // AOF 활성화
                .withFileSystemBind(tempDir.toAbsolutePath().toString(), "/data", BindMode.READ_WRITE);
    }

    /**
     * 테스트용 Redisson 클라이언트 생성
     */
    private RedissonClient createRedissonClient(String host, int port) {
        Config config = new Config();
        config.useSingleServer()
              .setAddress("redis://" + host + ":" + port)
              .setConnectionMinimumIdleSize(1)
              .setConnectionPoolSize(2);
        return Redisson.create(config);
    }
}
