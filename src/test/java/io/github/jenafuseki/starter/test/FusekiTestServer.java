package io.github.jenafuseki.starter.test;

import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.query.DatasetFactory;

/**
 * 测试辅助类：管理内嵌 Fuseki 服务器的生命周期
 *
 * <p>单元测试中通过 {@link FusekiServer} 启动一个内存型 Fuseki 实例，
 * 测试完成后调用 {@link #stop()} 关闭。</p>
 */
public class FusekiTestServer {

    private static final int DEFAULT_PORT = 3737;
    private static final String DEFAULT_DATASET = "test";

    private final FusekiServer server;
    private final int port;
    private final String dataset;

    public FusekiTestServer() {
        this(DEFAULT_PORT, DEFAULT_DATASET);
    }

    public FusekiTestServer(int port, String dataset) {
        this.port = port;
        this.dataset = dataset;
        this.server = FusekiServer.create()
                .port(port)
                .add("/" + dataset, DatasetFactory.createTxnMem(), true)
                .build();
    }

    /**
     * 启动内嵌 Fuseki 服务器
     */
    public FusekiTestServer start() {
        server.start();
        return this;
    }

    /**
     * 停止内嵌 Fuseki 服务器
     */
    public void stop() {
        if (server != null) {
            server.stop();
        }
    }

    /**
     * 获取服务器 URL，如 http://localhost:3737
     */
    public String getServerUrl() {
        return "http://localhost:" + port;
    }

    /**
     * 获取测试数据集名称
     */
    public String getDataset() {
        return dataset;
    }

    /**
     * 获取 SPARQL 查询端点
     */
    public String getQueryEndpoint() {
        return getServerUrl() + "/" + dataset + "/sparql";
    }

    /**
     * 获取 SPARQL 更新端点
     */
    public String getUpdateEndpoint() {
        return getServerUrl() + "/" + dataset + "/update";
    }
}

