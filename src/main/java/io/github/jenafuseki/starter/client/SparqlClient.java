package io.github.jenafuseki.starter.client;

import io.github.jenafuseki.starter.config.JenaFusekiProperties;
import io.github.jenafuseki.starter.exception.FusekiException;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionRemote;
import org.apache.jena.rdfconnection.RDFConnectionRemoteBuilder;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;

/**
 * SPARQL 底层客户端
 *
 * <p>封装 Jena {@link RDFConnectionRemote}，提供 SELECT / ASK / CONSTRUCT / UPDATE 执行能力。
 * 所有方法均线程安全：每次调用创建新连接，连接底层共享 {@link HttpClient} 连接池。</p>
 *
 * <p>SPARQL 语句打印行为由 {@code jena.fuseki.show-sparql} 控制：</p>
 * <ul>
 *   <li>{@code false}（默认）：仅在 DEBUG 级别输出，通过日志框架按需开启</li>
 *   <li>{@code true}：以 INFO 级别强制输出完整 SPARQL 语句，方便开发阶段调试</li>
 * </ul>
 */
public class SparqlClient {

    private static final Logger log = LoggerFactory.getLogger(SparqlClient.class);

    private final HttpClient httpClient;
    private final JenaFusekiProperties properties;

    public SparqlClient(HttpClient httpClient, JenaFusekiProperties properties) {
        this.httpClient = httpClient;
        this.properties = properties;
    }

    /**
     * 执行 SPARQL SELECT 查询，返回深拷贝的 ResultSet（连接关闭后仍可使用）
     *
     * @param queryEndpoint SPARQL 查询端点，如 http://localhost:3030/ds/sparql
     * @param sparql        SPARQL SELECT 语句
     * @return 深拷贝的 ResultSet
     */
    public ResultSet executeSelect(String queryEndpoint, String sparql) {
        logSparql("SELECT", queryEndpoint, sparql);
        try (RDFConnection conn = buildQueryConnection(queryEndpoint)) {
            Query query = QueryFactory.create(sparql);
            try (QueryExecution qe = conn.query(query)) {
                // 必须深拷贝：连接关闭后 ResultSet 不可用
                return ResultSetFactory.copyResults(qe.execSelect());
            }
        } catch (FusekiException e) {
            throw e;
        } catch (Exception e) {
            throw new FusekiException("SPARQL SELECT 执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行 SPARQL ASK 查询
     *
     * @param queryEndpoint SPARQL 查询端点
     * @param sparql        SPARQL ASK 语句
     * @return true / false
     */
    public boolean executeAsk(String queryEndpoint, String sparql) {
        logSparql("ASK", queryEndpoint, sparql);
        try (RDFConnection conn = buildQueryConnection(queryEndpoint)) {
            Query query = QueryFactory.create(sparql);
            try (QueryExecution qe = conn.query(query)) {
                return qe.execAsk();
            }
        } catch (FusekiException e) {
            throw e;
        } catch (Exception e) {
            throw new FusekiException("SPARQL ASK 执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行 SPARQL CONSTRUCT 查询，返回 RDF Model
     *
     * @param queryEndpoint SPARQL 查询端点
     * @param sparql        SPARQL CONSTRUCT 语句
     * @return 构建的 RDF Model
     */
    public Model executeConstruct(String queryEndpoint, String sparql) {
        logSparql("CONSTRUCT", queryEndpoint, sparql);
        try (RDFConnection conn = buildQueryConnection(queryEndpoint)) {
            Query query = QueryFactory.create(sparql);
            try (QueryExecution qe = conn.query(query)) {
                return qe.execConstruct();
            }
        } catch (FusekiException e) {
            throw e;
        } catch (Exception e) {
            throw new FusekiException("SPARQL CONSTRUCT 执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行 SPARQL DESCRIBE 查询，返回 RDF Model
     *
     * @param queryEndpoint SPARQL 查询端点
     * @param sparql        SPARQL DESCRIBE 语句
     * @return 描述的 RDF Model
     */
    public Model executeDescribe(String queryEndpoint, String sparql) {
        logSparql("DESCRIBE", queryEndpoint, sparql);
        try (RDFConnection conn = buildQueryConnection(queryEndpoint)) {
            Query query = QueryFactory.create(sparql);
            try (QueryExecution qe = conn.query(query)) {
                return qe.execDescribe();
            }
        } catch (FusekiException e) {
            throw e;
        } catch (Exception e) {
            throw new FusekiException("SPARQL DESCRIBE 执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行 SPARQL UPDATE（INSERT / DELETE / CLEAR 等）
     *
     * @param updateEndpoint SPARQL 更新端点，如 http://localhost:3030/ds/update
     * @param sparql         SPARQL UPDATE 语句
     */
    public void executeUpdate(String updateEndpoint, String sparql) {
        logSparql("UPDATE", updateEndpoint, sparql);
        try (RDFConnection conn = buildUpdateConnection(updateEndpoint)) {
            UpdateRequest updateRequest = UpdateFactory.create(sparql);
            conn.update(updateRequest);
        } catch (FusekiException e) {
            throw e;
        } catch (Exception e) {
            throw new FusekiException("SPARQL UPDATE 执行失败: " + e.getMessage(), e);
        }
    }

    // ===== 私有工具方法 =====

    private RDFConnection buildQueryConnection(String queryEndpoint) {
        RDFConnectionRemoteBuilder builder = RDFConnectionRemote.newBuilder()
                .queryEndpoint(queryEndpoint)
                .httpClient(httpClient);
        return builder.build();
    }

    private RDFConnection buildUpdateConnection(String updateEndpoint) {
        RDFConnectionRemoteBuilder builder = RDFConnectionRemote.newBuilder()
                .updateEndpoint(updateEndpoint)
                .httpClient(httpClient);
        return builder.build();
    }

    /**
     * 统一的 SPARQL 语句日志输出
     *
     * <p>当 {@code jena.fuseki.show-sparql=true} 时以 INFO 级别输出；
     * 否则降级为 DEBUG，由日志框架配置决定是否可见。</p>
     *
     * @param type     SPARQL 操作类型（SELECT / ASK / CONSTRUCT / DESCRIBE / UPDATE）
     * @param endpoint 目标端点 URL
     * @param sparql   完整的 SPARQL 语句
     */
    private void logSparql(String type, String endpoint, String sparql) {
        if (properties.isShowSparql()) {
            log.info("==> SPARQL {} | endpoint: {}\n{}", type, endpoint, sparql);
        } else {
            log.debug("SPARQL {} -> {}\n{}", type, endpoint, sparql);
        }
    }
}

