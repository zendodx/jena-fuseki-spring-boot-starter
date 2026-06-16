package io.github.jenafuseki.starter.health;

import io.github.jenafuseki.starter.config.JenaFusekiProperties;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Fuseki 健康检查指示器
 *
 * <p>集成 Spring Boot Actuator，通过调用 Fuseki 的 {@code /$/ping} 端点检测服务可用性。
 * 访问 {@code /actuator/health} 时将显示 fuseki 节点：</p>
 * <pre>{@code
 * {
 *   "status": "UP",
 *   "components": {
 *     "fuseki": {
 *       "status": "UP",
 *       "details": {
 *         "serverUrl": "http://localhost:3030",
 *         "dataset": "myDataset",
 *         "pingMs": 12
 *       }
 *     }
 *   }
 * }
 * }</pre>
 *
 * <p>仅在 classpath 存在 {@code spring-boot-starter-actuator} 时自动装配。</p>
 */
public class FusekiHealthIndicator extends AbstractHealthIndicator {

    private final JenaFusekiProperties properties;
    private final HttpClient httpClient;

    public FusekiHealthIndicator(JenaFusekiProperties properties, HttpClient httpClient) {
        super("Fuseki 健康检查失败");
        this.properties = properties;
        this.httpClient = httpClient;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        String pingUrl = properties.getServerUrl().replaceAll("/$", "") + "/$/ping";
        long start = System.currentTimeMillis();

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(pingUrl))
                .GET();

        // 添加 Basic Auth
        String username = properties.getUsername();
        String password = properties.getPassword();
        if (username != null && !username.isEmpty()) {
            String credentials = username + ":" + (password == null ? "" : password);
            String encoded = Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            requestBuilder.header("Authorization", "Basic " + encoded);
        }

        HttpResponse<String> response = httpClient.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        long elapsed = System.currentTimeMillis() - start;

        if (response.statusCode() == 200) {
            builder.up()
                    .withDetail("serverUrl", properties.getServerUrl())
                    .withDetail("dataset", properties.getDataset())
                    .withDetail("pingMs", elapsed);
        } else {
            builder.down()
                    .withDetail("serverUrl", properties.getServerUrl())
                    .withDetail("httpStatus", response.statusCode())
                    .withDetail("response", response.body());
        }
    }
}

