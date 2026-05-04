package four_tential.potential;

import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(exclude = PgVectorStoreAutoConfiguration.class)  // AiConfig에서 직접 설정
public class PotentialApplication {

    public static void main(String[] args) {
        SpringApplication.run(PotentialApplication.class, args);
    }

}