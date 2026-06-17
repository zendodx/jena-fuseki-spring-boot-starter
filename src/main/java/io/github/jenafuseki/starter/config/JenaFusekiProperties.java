package io.github.jenafuseki.starter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Jena Fuseki 配置属性
 *
 * <p>在 application.yml / application.properties 中以 {@code jena.fuseki} 为前缀进行配置：
 * <pre>{@code
 * jena:
 *   fuseki:
 *     server-url: http://localhost:3030
 *     dataset: myDataset
 *     username: admin
 *     password: admin123
 * }</pre>
 */
@ConfigurationProperties(prefix = "jena.fuseki")
public class JenaFusekiProperties {

    /**
     * 是否启用 Jena Fuseki 自动配置，默认 true
     */
    private boolean enabled = true;

    /**
     * Fuseki 服务器地址，例如 http://localhost:3030
     */
    private String serverUrl = "http://localhost:3030";

    /**
     * 默认操作的数据集名称，例如 myDataset
     * 最终 SPARQL 端点为 {serverUrl}/{dataset}/sparql
     */
    private String dataset;

    /**
     * Basic Auth 用户名（Fuseki 未开启认证时可不填）
     */
    private String username;

    /**
     * Basic Auth 密码
     */
    private String password;

    /**
     * 连接超时（毫秒），默认 5000ms
     */
    private int connectTimeout = 5000;

    /**
     * 读取超时（毫秒），默认 30000ms
     */
    private int readTimeout = 30000;

    /**
     * 最大连接数（连接池大小），默认 20
     */
    private int maxConnections = 20;

    /**
     * 是否在 INFO 级别打印最终执行的 SPARQL 语句，默认 false。
     *
     * <p>类比 MyBatis 的 {@code mybatis.configuration.log-impl}，开启后每条
     * SPARQL 执行前会以 {@code INFO} 级别输出完整语句，方便开发调试。
     * 生产环境建议保持默认关闭，通过调整日志级别
     * ({@code logging.level.io.github.jenafuseki.starter.client.SparqlClient=DEBUG})
     * 来按需查看。</p>
     */
    private boolean showSparql = false;

    /**
     * 数据初始化配置
     */
    private Init init = new Init();

    // ===== Getters & Setters =====

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getDataset() {
        return dataset;
    }

    public void setDataset(String dataset) {
        this.dataset = dataset;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public boolean isShowSparql() {
        return showSparql;
    }

    public void setShowSparql(boolean showSparql) {
        this.showSparql = showSparql;
    }

    public Init getInit() {
        return init;
    }

    public void setInit(Init init) {
        this.init = init;
    }

    /**
     * 构建默认数据集的 SPARQL 查询端点 URL
     */
    public String buildQueryEndpoint() {
        return buildQueryEndpoint(this.dataset);
    }

    /**
     * 构建指定数据集的 SPARQL 查询端点 URL
     */
    public String buildQueryEndpoint(String datasetName) {
        return serverUrl.replaceAll("/$", "") + "/" + datasetName + "/sparql";
    }

    /**
     * 构建默认数据集的 SPARQL 更新端点 URL
     */
    public String buildUpdateEndpoint() {
        return buildUpdateEndpoint(this.dataset);
    }

    /**
     * 构建指定数据集的 SPARQL 更新端点 URL
     */
    public String buildUpdateEndpoint(String datasetName) {
        return serverUrl.replaceAll("/$", "") + "/" + datasetName + "/update";
    }

    /**
     * 构建 Graph Store Protocol (GSP) 端点 URL
     */
    public String buildGspEndpoint() {
        return buildGspEndpoint(this.dataset);
    }

    /**
     * 构建指定数据集的 GSP 端点 URL
     */
    public String buildGspEndpoint(String datasetName) {
        return serverUrl.replaceAll("/$", "") + "/" + datasetName + "/data";
    }

    // ===== 内部类 =====

    /**
     * 数据集初始化配置（P2 功能）
     */
    public static class Init {

        /**
         * 是否开启启动时初始化，默认 false
         */
        private boolean enabled = false;

        /**
         * 初始化 RDF 数据文件路径列表
         * 支持 classpath: / file: / http: 前缀
         * 例如：classpath:rdf/base-ontology.ttl
         */
        private List<String> dataLocations = new ArrayList<>();

        /**
         * 加载到哪个命名图（Named Graph URI）
         * 不填则加载到默认图
         */
        private String graphUri;

        /**
         * 加载模式：
         * always  - 每次启动都加载（会先清空）
         * if-empty - 仅当数据集为空时加载
         */
        private String mode = "if-empty";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getDataLocations() {
            return dataLocations;
        }

        public void setDataLocations(List<String> dataLocations) {
            this.dataLocations = dataLocations;
        }

        public String getGraphUri() {
            return graphUri;
        }

        public void setGraphUri(String graphUri) {
            this.graphUri = graphUri;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }
    }
}

