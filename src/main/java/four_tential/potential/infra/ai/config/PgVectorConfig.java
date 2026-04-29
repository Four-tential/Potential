package four_tential.potential.infra.ai.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * PGVector 전용 DataSource / JdbcTemplate 설정
 *
 * pgVectorDataSource Bean이 추가되면 Spring이 DataSource가 2개라고 판단해
 * JPA/Flyway가 어느 DataSource를 써야 할지 혼란이 생김.
 * -> MySQL DataSource를 @Primary로 명시해서 JPA/Flyway가 항상 MySQL을 바라보도록 고정.
 *
 * 로컬 실행: docker-compose up pgvector
 * dev/prod : AWS Parameter Store에서 주입
 */
@Configuration
@Profile("!test")
public class PgVectorConfig {

    // ─────────────────────────────────────────
    //  MySQL DataSource — @Primary 명시
    //  JPA, Flyway 등 자동 설정이 이 Bean을 우선 사용
    // ─────────────────────────────────────────

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource dataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    // ─────────────────────────────────────────
    //  PGVector 전용 DataSource — Secondary
    //  AiConfig의 VectorStore Bean만 이 DataSource를 사용
    // ─────────────────────────────────────────

    @Value("${pgvector.datasource.url}")
    private String pgUrl;

    @Value("${pgvector.datasource.username}")
    private String pgUsername;

    @Value("${pgvector.datasource.password}")
    private String pgPassword;

    @Bean("pgVectorDataSource")
    public DataSource pgVectorDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(pgUrl);
        ds.setUsername(pgUsername);
        ds.setPassword(pgPassword);
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setMaximumPoolSize(5);
        ds.setMinimumIdle(1);
        ds.setConnectionTimeout(30_000);
        ds.setPoolName("pgvector-pool");
        return ds;
    }

    @Bean("pgVectorJdbcTemplate")
    public JdbcTemplate pgVectorJdbcTemplate() {
        return new JdbcTemplate(pgVectorDataSource());
    }
}