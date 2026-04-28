package four_tential.potential.infra.ai.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * PGVector 전용 DataSource / JdbcTemplate 설정
 *
 * 메인 DB(MySQL)와 완전히 분리된 PostgreSQL + pgvector 커넥션.
 * Spring AI PgVectorStore는 @Qualifier("pgVectorJdbcTemplate")로 주입받음.
 *
 * 로컬 실행: docker-compose up pgvector
 * dev/prod : AWS Parameter Store에서 주입
 */
@Configuration
@Profile("!test")  // 테스트 환경에서는 제외
public class PgVectorConfig {

    @Value("${pgvector.datasource.url}")
    private String url;

    @Value("${pgvector.datasource.username}")
    private String username;

    @Value("${pgvector.datasource.password}")
    private String password;

    @Bean("pgVectorDataSource")
    public DataSource pgVectorDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
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