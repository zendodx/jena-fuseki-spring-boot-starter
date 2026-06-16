package io.github.jenafuseki.starter.core;

import io.github.jenafuseki.starter.client.FusekiDatasetClient;
import io.github.jenafuseki.starter.client.SparqlClient;
import io.github.jenafuseki.starter.config.JenaFusekiProperties;
import io.github.jenafuseki.starter.core.wrapper.AskWrapper;
import io.github.jenafuseki.starter.core.wrapper.SelectWrapper;
import io.github.jenafuseki.starter.core.wrapper.UpdateWrapper;
import io.github.jenafuseki.starter.exception.FusekiException;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Jena Fuseki 高级操作模板类
 *
 * <p>类比 Spring 的 {@code JdbcTemplate} / {@code RedisTemplate}，提供面向业务的
 * SPARQL 操作 API，屏蔽底层 {@link SparqlClient} 的细节。</p>
 *
 * <p><b>原始字符串用法：</b></p>
 * <pre>{@code
 * @Autowired
 * private JenaFusekiTemplate fusekiTemplate;
 *
 * // SELECT 查询
 * List<Map<String, RDFNode>> rows = fusekiTemplate.select(
 *     "SELECT ?name WHERE { ?s foaf:name ?name }"
 * );
 *
 * // 写入数据
 * fusekiTemplate.update("INSERT DATA { <http://a> <http://b> \"c\" }");
 * }</pre>
 *
 * <p><b>Wrapper 链式构建用法（类比 MyBatis-Plus QueryWrapper）：</b></p>
 * <pre>{@code
 * // SELECT 查询
 * List<String> names = fusekiTemplate.select(
 *     new SelectWrapper()
 *         .prefixFoaf()
 *         .select("?name")
 *         .triple("?s", "a", "foaf:Person")
 *         .triple("?s", "foaf:name", "?name")
 *         .filterContains("?name", "Ali")
 *         .orderByAsc("?name")
 *         .limit(10),
 *     (row, i) -> row.getLiteral("name").getString()
 * );
 *
 * // ASK 查询
 * boolean exists = fusekiTemplate.ask(
 *     new AskWrapper()
 *         .prefixFoaf()
 *         .triple("?s", "foaf:name", "\"Alice\"")
 * );
 *
 * // INSERT DATA
 * fusekiTemplate.update(
 *     new UpdateWrapper()
 *         .insertData()
 *         .tripleLiteral("http://example.org/Alice", "http://xmlns.com/foaf/0.1/name", "Alice")
 * );
 *
 * // DELETE WHERE
 * fusekiTemplate.update(
 *     new UpdateWrapper()
 *         .prefixFoaf()
 *         .deleteWhere()
 *         .triple("?s", "foaf:name", "?name")
 *         .filterEqLiteral("?name", "Alice")
 * );
 * }</pre>
 */
public class JenaFusekiTemplate {

    private static final Logger log = LoggerFactory.getLogger(JenaFusekiTemplate.class);

    private final SparqlClient sparqlClient;
    private final FusekiDatasetClient datasetClient;
    private final JenaFusekiProperties properties;

    public JenaFusekiTemplate(SparqlClient sparqlClient,
                               FusekiDatasetClient datasetClient,
                               JenaFusekiProperties properties) {
        this.sparqlClient = sparqlClient;
        this.datasetClient = datasetClient;
        this.properties = properties;
    }

    // =====================================================================
    // P0: SPARQL SELECT
    // =====================================================================

    /**
     * 执行 SPARQL SELECT 查询，返回每行变量的原始 RDFNode 映射
     *
     * @param sparql SPARQL SELECT 语句
     * @return 结果列表，每行是变量名 -> RDFNode 的 Map
     */
    public List<Map<String, RDFNode>> select(String sparql) {
        return select(sparql, properties.getDataset());
    }

    /**
     * 执行 SPARQL SELECT 查询（指定数据集）
     *
     * @param sparql      SPARQL SELECT 语句
     * @param datasetName 目标数据集名称
     * @return 结果列表，每行是变量名 -> RDFNode 的 Map
     */
    public List<Map<String, RDFNode>> select(String sparql, String datasetName) {
        return select(sparql, datasetName, (row, i) -> {
            Map<String, RDFNode> rowMap = new HashMap<>();
            row.varNames().forEachRemaining(var -> rowMap.put(var, row.get(var)));
            return rowMap;
        });
    }

    /**
     * 执行 SPARQL SELECT 查询，通过 {@link RowMapper} 映射为目标对象
     *
     * @param sparql    SPARQL SELECT 语句
     * @param rowMapper 行映射器
     * @param <T>       目标对象类型
     * @return 映射后的对象列表
     */
    public <T> List<T> select(String sparql, RowMapper<T> rowMapper) {
        return select(sparql, properties.getDataset(), rowMapper);
    }

    /**
     * 执行 SPARQL SELECT 查询（指定数据集），通过 {@link RowMapper} 映射为目标对象
     *
     * @param sparql      SPARQL SELECT 语句
     * @param datasetName 目标数据集名称
     * @param rowMapper   行映射器
     * @param <T>         目标对象类型
     * @return 映射后的对象列表
     */
    public <T> List<T> select(String sparql, String datasetName, RowMapper<T> rowMapper) {
        String endpoint = properties.buildQueryEndpoint(datasetName);
        ResultSet rs = sparqlClient.executeSelect(endpoint, sparql);
        List<T> results = new ArrayList<>();
        int rowNum = 0;
        while (rs.hasNext()) {
            QuerySolution row = rs.nextSolution();
            results.add(rowMapper.mapRow(row, rowNum++));
        }
        log.debug("SELECT 返回 {} 行", results.size());
        return results;
    }

    /**
     * 执行 SPARQL SELECT 查询，返回第一行结果（无结果返回 null）
     *
     * @param sparql SPARQL SELECT 语句
     * @return 第一行变量映射，或 null
     */
    public Map<String, RDFNode> selectOne(String sparql) {
        List<Map<String, RDFNode>> results = select(sparql);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * 执行 SPARQL SELECT 查询，映射第一行结果为目标对象（无结果返回 null）
     *
     * @param sparql    SPARQL SELECT 语句
     * @param rowMapper 行映射器
     * @param <T>       目标对象类型
     * @return 第一行映射对象，或 null
     */
    public <T> T selectOne(String sparql, RowMapper<T> rowMapper) {
        List<T> results = select(sparql, rowMapper);
        return results.isEmpty() ? null : results.get(0);
    }

    // =====================================================================
    // P0: SPARQL SELECT（Wrapper 重载）
    // =====================================================================

    /**
     * 使用 {@link SelectWrapper} 构建并执行 SPARQL SELECT 查询，返回原始 RDFNode 映射
     *
     * <pre>{@code
     * List<Map<String, RDFNode>> rows = fusekiTemplate.select(
     *     new SelectWrapper()
     *         .prefixFoaf()
     *         .select("?name", "?age")
     *         .triple("?s", "a", "foaf:Person")
     *         .triple("?s", "foaf:name", "?name")
     *         .limit(10)
     * );
     * }</pre>
     *
     * @param wrapper SELECT 构建器
     * @return 结果列表，每行是变量名 -> RDFNode 的 Map
     */
    public List<Map<String, RDFNode>> select(SelectWrapper wrapper) {
        return select(wrapper.build(), properties.getDataset());
    }

    /**
     * 使用 {@link SelectWrapper} 构建并执行 SPARQL SELECT 查询（指定数据集）
     *
     * @param wrapper     SELECT 构建器
     * @param datasetName 目标数据集名称
     * @return 结果列表，每行是变量名 -> RDFNode 的 Map
     */
    public List<Map<String, RDFNode>> select(SelectWrapper wrapper, String datasetName) {
        return select(wrapper.build(), datasetName);
    }

    /**
     * 使用 {@link SelectWrapper} 构建并执行 SPARQL SELECT 查询，通过 {@link RowMapper} 映射为目标对象
     *
     * <pre>{@code
     * List<String> names = fusekiTemplate.select(
     *     new SelectWrapper()
     *         .prefixFoaf()
     *         .select("?name")
     *         .triple("?s", "foaf:name", "?name")
     *         .filterContains("?name", "Ali")
     *         .orderByAsc("?name"),
     *     (row, i) -> row.getLiteral("name").getString()
     * );
     * }</pre>
     *
     * @param wrapper   SELECT 构建器
     * @param rowMapper 行映射器
     * @param <T>       目标对象类型
     * @return 映射后的对象列表
     */
    public <T> List<T> select(SelectWrapper wrapper, RowMapper<T> rowMapper) {
        return select(wrapper.build(), properties.getDataset(), rowMapper);
    }

    /**
     * 使用 {@link SelectWrapper} 构建并执行 SPARQL SELECT 查询（指定数据集），通过 {@link RowMapper} 映射
     *
     * @param wrapper     SELECT 构建器
     * @param datasetName 目标数据集名称
     * @param rowMapper   行映射器
     * @param <T>         目标对象类型
     * @return 映射后的对象列表
     */
    public <T> List<T> select(SelectWrapper wrapper, String datasetName, RowMapper<T> rowMapper) {
        return select(wrapper.build(), datasetName, rowMapper);
    }

    /**
     * 使用 {@link SelectWrapper} 构建并执行 SPARQL SELECT 查询，返回第一行结果（无结果返回 null）
     *
     * @param wrapper SELECT 构建器
     * @return 第一行变量映射，或 null
     */
    public Map<String, RDFNode> selectOne(SelectWrapper wrapper) {
        return selectOne(wrapper.build());
    }

    /**
     * 使用 {@link SelectWrapper} 构建并执行 SPARQL SELECT 查询，映射第一行为目标对象（无结果返回 null）
     *
     * @param wrapper   SELECT 构建器
     * @param rowMapper 行映射器
     * @param <T>       目标对象类型
     * @return 第一行映射对象，或 null
     */
    public <T> T selectOne(SelectWrapper wrapper, RowMapper<T> rowMapper) {
        return selectOne(wrapper.build(), rowMapper);
    }

    // =====================================================================
    // P0: SPARQL ASK
    // =====================================================================

    /**
     * 执行 SPARQL ASK 查询
     *
     * @param sparql SPARQL ASK 语句
     * @return true 表示模式匹配，false 表示无匹配
     */
    public boolean ask(String sparql) {
        return ask(sparql, properties.getDataset());
    }

    /**
     * 执行 SPARQL ASK 查询（指定数据集）
     *
     * @param sparql      SPARQL ASK 语句
     * @param datasetName 目标数据集名称
     * @return true / false
     */
    public boolean ask(String sparql, String datasetName) {
        String endpoint = properties.buildQueryEndpoint(datasetName);
        return sparqlClient.executeAsk(endpoint, sparql);
    }

    /**
     * 使用 {@link AskWrapper} 构建并执行 SPARQL ASK 查询
     *
     * <pre>{@code
     * boolean exists = fusekiTemplate.ask(
     *     new AskWrapper()
     *         .prefixFoaf()
     *         .isA("?s", "foaf:Person")
     *         .triple("?s", "foaf:name", "\"Alice\"")
     * );
     * }</pre>
     *
     * @param wrapper ASK 构建器
     * @return true 表示匹配，false 表示无匹配
     */
    public boolean ask(AskWrapper wrapper) {
        return ask(wrapper.build(), properties.getDataset());
    }

    /**
     * 使用 {@link AskWrapper} 构建并执行 SPARQL ASK 查询（指定数据集）
     *
     * @param wrapper     ASK 构建器
     * @param datasetName 目标数据集名称
     * @return true / false
     */
    public boolean ask(AskWrapper wrapper, String datasetName) {
        return ask(wrapper.build(), datasetName);
    }

    // =====================================================================
    // P0: SPARQL CONSTRUCT / DESCRIBE
    // =====================================================================

    /**
     * 执行 SPARQL CONSTRUCT 查询，返回 RDF Model
     *
     * @param sparql SPARQL CONSTRUCT 语句
     * @return 构建的 RDF Model
     */
    public Model construct(String sparql) {
        return construct(sparql, properties.getDataset());
    }

    /**
     * 执行 SPARQL CONSTRUCT 查询（指定数据集），返回 RDF Model
     *
     * @param sparql      SPARQL CONSTRUCT 语句
     * @param datasetName 目标数据集名称
     * @return 构建的 RDF Model
     */
    public Model construct(String sparql, String datasetName) {
        String endpoint = properties.buildQueryEndpoint(datasetName);
        return sparqlClient.executeConstruct(endpoint, sparql);
    }

    /**
     * 执行 SPARQL DESCRIBE 查询，返回 RDF Model
     *
     * @param sparql SPARQL DESCRIBE 语句
     * @return 描述的 RDF Model
     */
    public Model describe(String sparql) {
        return describe(sparql, properties.getDataset());
    }

    /**
     * 执行 SPARQL DESCRIBE 查询（指定数据集），返回 RDF Model
     *
     * @param sparql      SPARQL DESCRIBE 语句
     * @param datasetName 目标数据集名称
     * @return 描述的 RDF Model
     */
    public Model describe(String sparql, String datasetName) {
        String endpoint = properties.buildQueryEndpoint(datasetName);
        return sparqlClient.executeDescribe(endpoint, sparql);
    }

    // =====================================================================
    // P0: SPARQL UPDATE (INSERT / DELETE / CLEAR)
    // =====================================================================

    /**
     * 执行 SPARQL UPDATE（INSERT DATA / DELETE DATA / CLEAR 等）
     *
     * @param sparql SPARQL UPDATE 语句
     */
    public void update(String sparql) {
        update(sparql, properties.getDataset());
    }

    /**
     * 执行 SPARQL UPDATE（指定数据集）
     *
     * @param sparql      SPARQL UPDATE 语句
     * @param datasetName 目标数据集名称
     */
    public void update(String sparql, String datasetName) {
        String endpoint = properties.buildUpdateEndpoint(datasetName);
        sparqlClient.executeUpdate(endpoint, sparql);
    }

    /**
     * 使用 {@link UpdateWrapper} 构建并执行 SPARQL UPDATE
     *
     * <pre>{@code
     * // INSERT DATA
     * fusekiTemplate.update(
     *     new UpdateWrapper()
     *         .insertData()
     *         .tripleLiteral("http://example.org/Alice", "http://xmlns.com/foaf/0.1/name", "Alice")
     * );
     *
     * // DELETE WHERE
     * fusekiTemplate.update(
     *     new UpdateWrapper()
     *         .prefixFoaf()
     *         .deleteWhere()
     *         .triple("?s", "foaf:name", "?name")
     *         .filterEqLiteral("?name", "Alice")
     * );
     * }</pre>
     *
     * @param wrapper UPDATE 构建器
     */
    public void update(UpdateWrapper wrapper) {
        update(wrapper.build(), properties.getDataset());
    }

    /**
     * 使用 {@link UpdateWrapper} 构建并执行 SPARQL UPDATE（指定数据集）
     *
     * @param wrapper     UPDATE 构建器
     * @param datasetName 目标数据集名称
     */
    public void update(UpdateWrapper wrapper, String datasetName) {
        update(wrapper.build(), datasetName);
    }

    // =====================================================================
    // P0: 便捷三元组操作
    // =====================================================================

    /**
     * 插入单条三元组
     *
     * @param subject   主语 URI，如 http://example.org/Alice
     * @param predicate 谓语 URI，如 http://xmlns.com/foaf/0.1/name
     * @param object    宾语（字符串字面量）
     */
    public void insertTriple(String subject, String predicate, String object) {
        String sparql = String.format(
                "INSERT DATA { <%s> <%s> \"%s\" }",
                subject, predicate, escapeLiteral(object)
        );
        update(sparql);
    }

    /**
     * 插入单条三元组（宾语为 URI）
     *
     * @param subject   主语 URI
     * @param predicate 谓语 URI
     * @param objectUri 宾语 URI
     */
    public void insertTripleUri(String subject, String predicate, String objectUri) {
        String sparql = String.format(
                "INSERT DATA { <%s> <%s> <%s> }",
                subject, predicate, objectUri
        );
        update(sparql);
    }

    /**
     * 删除单条三元组
     *
     * @param subject   主语 URI
     * @param predicate 谓语 URI
     * @param object    宾语（字符串字面量）
     */
    public void deleteTriple(String subject, String predicate, String object) {
        String sparql = String.format(
                "DELETE DATA { <%s> <%s> \"%s\" }",
                subject, predicate, escapeLiteral(object)
        );
        update(sparql);
    }

    /**
     * 删除某个主语的所有三元组
     *
     * @param subject 主语 URI
     */
    public void deleteSubject(String subject) {
        String sparql = String.format(
                "DELETE WHERE { <%s> ?p ?o }",
                subject
        );
        update(sparql);
    }

    // =====================================================================
    // P1: 图（Graph）管理
    // =====================================================================

    /**
     * 上传 RDF Model 到默认图（覆盖已有内容）
     *
     * @param model RDF Model
     */
    public void uploadModel(Model model) {
        uploadModel(model, properties.getDataset());
    }

    /**
     * 上传 RDF Model 到指定数据集的默认图（覆盖已有内容）
     *
     * @param model       RDF Model
     * @param datasetName 目标数据集名称
     */
    public void uploadModel(Model model, String datasetName) {
        datasetClient.putModel(datasetName, null, model);
    }

    /**
     * 上传 RDF Model 到命名图（覆盖已有内容）
     *
     * @param graphUri 命名图 URI
     * @param model    RDF Model
     */
    public void uploadNamedGraph(String graphUri, Model model) {
        uploadNamedGraph(graphUri, model, properties.getDataset());
    }

    /**
     * 上传 RDF Model 到指定数据集的命名图（覆盖已有内容）
     *
     * @param graphUri    命名图 URI
     * @param model       RDF Model
     * @param datasetName 目标数据集名称
     */
    public void uploadNamedGraph(String graphUri, Model model, String datasetName) {
        if (graphUri == null || graphUri.isEmpty()) {
            throw new FusekiException("graphUri 不能为空，如需操作默认图请使用 uploadModel()");
        }
        datasetClient.putModel(datasetName, graphUri, model);
    }

    /**
     * 下载默认图的 RDF Model
     *
     * @return 默认图的 RDF Model
     */
    public Model downloadDefaultGraph() {
        return downloadDefaultGraph(properties.getDataset());
    }

    /**
     * 下载指定数据集默认图的 RDF Model
     *
     * @param datasetName 目标数据集名称
     * @return 默认图的 RDF Model
     */
    public Model downloadDefaultGraph(String datasetName) {
        return datasetClient.getModel(datasetName, null);
    }

    /**
     * 下载命名图的 RDF Model
     *
     * @param graphUri 命名图 URI
     * @return 命名图的 RDF Model
     */
    public Model downloadNamedGraph(String graphUri) {
        return downloadNamedGraph(graphUri, properties.getDataset());
    }

    /**
     * 下载指定数据集中命名图的 RDF Model
     *
     * @param graphUri    命名图 URI
     * @param datasetName 目标数据集名称
     * @return 命名图的 RDF Model
     */
    public Model downloadNamedGraph(String graphUri, String datasetName) {
        return datasetClient.getModel(datasetName, graphUri);
    }

    /**
     * 清空默认图（删除所有三元组，保留图本身）
     */
    public void clearDefaultGraph() {
        update("CLEAR DEFAULT");
    }

    /**
     * 清空命名图（删除所有三元组，保留图本身）
     *
     * @param graphUri 命名图 URI
     */
    public void clearGraph(String graphUri) {
        String sparql = String.format("CLEAR GRAPH <%s>", graphUri);
        update(sparql);
    }

    /**
     * 删除命名图（删除图及其所有三元组）
     *
     * @param graphUri 命名图 URI
     */
    public void dropNamedGraph(String graphUri) {
        String sparql = String.format("DROP GRAPH <%s>", graphUri);
        update(sparql);
    }

    /**
     * 清空整个数据集（所有图）
     */
    public void clearAll() {
        update("CLEAR ALL");
    }

    // =====================================================================
    // 私有工具方法
    // =====================================================================

    /**
     * 转义 SPARQL 字符串字面量中的特殊字符
     */
    private String escapeLiteral(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

