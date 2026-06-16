package io.github.jenafuseki.starter.autoconfigure;

import io.github.jenafuseki.starter.client.FusekiDatasetClient;
import io.github.jenafuseki.starter.client.SparqlClient;
import io.github.jenafuseki.starter.config.JenaFusekiProperties;
import io.github.jenafuseki.starter.core.JenaFusekiTemplate;
import io.github.jenafuseki.starter.health.FusekiHealthIndicator;
import io.github.jenafuseki.starter.init.FusekiDataInitializer;
import io.github.jenafuseki.starter.metrics.FusekiMetricsInterceptor;
import org.apache.jena.rdfconnection.RDFConnection;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Jena Fuseki Spring Boot 自动配置
 *
 * <p>条件：</p>
 * <ul>
 *   <li>classpath 存在 {@link RDFConnection}（即引入了 jena-rdfconnection 依赖）</li>
 *   <li>{@code jena.fuseki.enabled} 不为 false（默认开启）</li>
 * </ul>
 *
 * <p>所有 Bean 均添加 {@link ConditionalOnMissingBean}，业务方可自定义覆盖。</p>
 *
 * <p>可选功能（Health / Metrics）通过独立内部配置类 + 类级 {@link ConditionalOnClass} 控制，
 * 避免主类 import 可选依赖类导致 ClassNotFoundException。</p>
 */
@Configuration
@ConditionalOnClass(RDFConnection.class)
@ConditionalOnProperty(
        prefix = "jena.fuseki",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@EnableConfigurationProperties(JenaFusekiProperties.class)
public class JenaFusekiAutoConfiguration {

    // =====================================================================
    // P0: 连接管理
    // =====================================================================

    /**
     * 创建共享的 HttpClient（带连接池、超时控制）
     *
     * <p>使用 Java 11 内置 {@link HttpClient}，默认连接池行为由 JVM 管理。</p>
     * <p>使用 bean name 作为条件判断，避免按 JDK 内置类型扫描引发条件处理异常。</p>
     */
    @Bean("fusekiHttpClient")
    @ConditionalOnMissingBean(name = "fusekiHttpClient")
    public HttpClient fusekiHttpClient(JenaFusekiProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeout()))
                // Java HttpClient 内置连接复用（HTTP/1.1 keep-alive / HTTP/2 多路复用）
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * 创建 SPARQL 底层客户端
     */
    @Bean
    @ConditionalOnMissingBean(SparqlClient.class)
    public SparqlClient sparqlClient(HttpClient fusekiHttpClient, JenaFusekiProperties properties) {
        return new SparqlClient(fusekiHttpClient, properties);
    }

    /**
     * 创建数据集管理客户端（P1 图管理 + P2 数据集管理）
     */
    @Bean
    @ConditionalOnMissingBean(FusekiDatasetClient.class)
    public FusekiDatasetClient fusekiDatasetClient(JenaFusekiProperties properties,
                                                   HttpClient fusekiHttpClient) {
        return new FusekiDatasetClient(properties, fusekiHttpClient);
    }

    /**
     * 创建高级模板类（核心入口，业务代码直接使用）
     */
    @Bean
    @ConditionalOnMissingBean(JenaFusekiTemplate.class)
    public JenaFusekiTemplate jenaFusekiTemplate(SparqlClient sparqlClient,
                                                 FusekiDatasetClient fusekiDatasetClient,
                                                 JenaFusekiProperties properties) {
        return new JenaFusekiTemplate(sparqlClient, fusekiDatasetClient, properties);
    }

    // =====================================================================
    // P2: 启动数据初始化
    // =====================================================================

    /**
     * 注册 Fuseki 数据初始化器
     *
     * <p>仅当 {@code jena.fuseki.init.enabled=true} 时生效。</p>
     */
    @Bean
    @ConditionalOnProperty(prefix = "jena.fuseki.init", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(FusekiDataInitializer.class)
    public FusekiDataInitializer fusekiDataInitializer(JenaFusekiProperties properties,
                                                       FusekiDatasetClient fusekiDatasetClient,
                                                       SparqlClient sparqlClient,
                                                       ResourceLoader resourceLoader) {
        return new FusekiDataInitializer(properties, fusekiDatasetClient, sparqlClient, resourceLoader);
    }

    // =====================================================================
    // P1: 健康检查（可选，依赖 spring-boot-starter-actuator）
    // =====================================================================

    /**
     * Actuator 健康检查配置，独立内部类确保 classpath 无 Actuator 时整个类不被加载。
     */
    @Configuration
    @ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
    static class FusekiHealthConfiguration {

        @Bean
        @ConditionalOnMissingBean(FusekiHealthIndicator.class)
        public FusekiHealthIndicator fusekiHealthIndicator(JenaFusekiProperties properties,
                                                           HttpClient fusekiHttpClient) {
            return new FusekiHealthIndicator(properties, fusekiHttpClient);
        }
    }

    // =====================================================================
    // P2: Metrics（可选，依赖 micrometer-core）
    // =====================================================================

    /**
     * Micrometer Metrics 配置，独立内部类确保 classpath 无 Micrometer 时整个类不被加载。
     */
    @Configuration
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    static class FusekiMetricsConfiguration {

        @Bean
        @ConditionalOnMissingBean(FusekiMetricsInterceptor.class)
        public FusekiMetricsInterceptor fusekiMetricsInterceptor(
                io.micrometer.core.instrument.MeterRegistry meterRegistry) {
            return new FusekiMetricsInterceptor(meterRegistry);
        }
    }
}

