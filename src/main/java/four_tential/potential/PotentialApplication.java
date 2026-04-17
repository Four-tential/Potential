package four_tential.potential;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class PotentialApplication {

    public static void main(String[] args) {
        SpringApplication.run(PotentialApplication.class, args);
    }

}
