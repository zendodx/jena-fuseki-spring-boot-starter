package io.github.jenafuseki.starter;

import io.github.jenafuseki.starter.client.FusekiDatasetClient;
import io.github.jenafuseki.starter.client.SparqlClient;
import io.github.jenafuseki.starter.config.JenaFusekiProperties;
import io.github.jenafuseki.starter.core.JenaFusekiTemplate;
import io.github.jenafuseki.starter.core.wrapper.AskWrapper;
import io.github.jenafuseki.starter.core.wrapper.SelectWrapper;
import io.github.jenafuseki.starter.core.wrapper.UpdateWrapper;
import io.github.jenafuseki.starter.exception.FusekiException;
import io.github.jenafuseki.starter.test.FusekiTestServer;
import org.apache.jena.rdf.model.RDFNode;
import org.junit.jupiter.api.*;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SparqlWrapper 链式构建 API 集成测试
 * 覆盖 SelectWrapper / AskWrapper / UpdateWrapper 的主要功能
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SparqlWrapperTest {

    private static final int PORT = 3740;
    private static final String DATASET = "wrapperTest";

    private static FusekiTestServer testServer;
    private static JenaFusekiTemplate template;

    @BeforeAll
    static void setUp() {
        testServer = new FusekiTestServer(PORT, DATASET).start();

        JenaFusekiProperties props = new JenaFusekiProperties();
        props.setServerUrl(testServer.getServerUrl());
        props.setDataset(DATASET);

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        SparqlClient sparqlClient = new SparqlClient(httpClient, new JenaFusekiProperties());
        FusekiDatasetClient datasetClient = new FusekiDatasetClient(props, httpClient);

        template = new JenaFusekiTemplate(sparqlClient, datasetClient, props);
    }

    @AfterAll
    static void tearDown() {
        if (testServer != null) {
            testServer.stop();
        }
    }

    @BeforeEach
    void clearData() {
        template.clearAll();
    }

    // =====================================================================
    // SelectWrapper 构建验证（build()）
    // =====================================================================

    @Test
    @Order(1)
    @DisplayName("SelectWrapper.build() - SELECT * 无条件")
    void testSelectWrapperBuildSelectAll() {
        String sparql = new SelectWrapper()
                .selectAll()
                .triple("?s", "?p", "?o")
                .build();

        assertTrue(sparql.contains("SELECT *"));
        assertTrue(sparql.contains("WHERE {"));
        assertTrue(sparql.contains("?s ?p ?o ."));
    }

    @Test
    @Order(2)
    @DisplayName("SelectWrapper.build() - SELECT 指定变量 + PREFIX + FILTER + LIMIT + OFFSET")
    void testSelectWrapperBuildFull() {
        String sparql = new SelectWrapper()
                .prefixFoaf()
                .select("?name", "?age")
                .triple("?s", "a", "foaf:Person")
                .triple("?s", "foaf:name", "?name")
                .optional("?s foaf:age ?age .")
                .filterGt("?age", "18")
                .orderByAsc("?name")
                .limit(10)
                .offset(0)
                .build();

        assertTrue(sparql.contains("PREFIX foaf:"));
        assertTrue(sparql.contains("SELECT ?name ?age"));
        assertTrue(sparql.contains("?s a foaf:Person ."));
        assertTrue(sparql.contains("OPTIONAL { ?s foaf:age ?age . }"));
        assertTrue(sparql.contains("FILTER(?age > 18)"));
        assertTrue(sparql.contains("ORDER BY ASC(?name)"));
        assertTrue(sparql.contains("LIMIT 10"));
        assertTrue(sparql.contains("OFFSET 0"));
    }

    @Test
    @Order(3)
    @DisplayName("SelectWrapper.build() - DISTINCT")
    void testSelectWrapperBuildDistinct() {
        String sparql = new SelectWrapper()
                .selectDistinct("?name")
                .triple("?s", "?p", "?name")
                .build();

        assertTrue(sparql.contains("SELECT DISTINCT ?name"));
    }

    @Test
    @Order(4)
    @DisplayName("SelectWrapper.build() - GROUP BY + HAVING + ORDER BY DESC")
    void testSelectWrapperBuildGroupBy() {
        String sparql = new SelectWrapper()
                .prefixFoaf()
                .select("?type", "(COUNT(?s) AS ?count)")
                .triple("?s", "a", "?type")
                .groupBy("?type")
                .having("COUNT(?s) > 1")
                .orderByDesc("?count")
                .build();

        assertTrue(sparql.contains("GROUP BY ?type"));
        assertTrue(sparql.contains("HAVING(COUNT(?s) > 1)"));
        assertTrue(sparql.contains("ORDER BY DESC(?count)"));
    }

    @Test
    @Order(5)
    @DisplayName("SelectWrapper.build() - filterContains / filterRegex / filterLang")
    void testSelectWrapperBuildFilters() {
        String sparql = new SelectWrapper()
                .prefixFoaf()
                .select("?name")
                .triple("?s", "foaf:name", "?name")
                .filterContains("?name", "Ali")
                .filterLang("?name", "zh")
                .build();

        assertTrue(sparql.contains("FILTER(contains(?name, \"Ali\"))"));
        assertTrue(sparql.contains("FILTER(lang(?name) = \"zh\")"));
    }

    @Test
    @Order(6)
    @DisplayName("SelectWrapper.build() - page() 分页快捷方法")
    void testSelectWrapperBuildPage() {
        String sparql = new SelectWrapper()
                .selectAll()
                .triple("?s", "?p", "?o")
                .page(3, 20)
                .build();

        assertTrue(sparql.contains("LIMIT 20"));
        assertTrue(sparql.contains("OFFSET 40"));
    }

    // =====================================================================
    // AskWrapper 构建验证（build()）
    // =====================================================================

    @Test
    @Order(10)
    @DisplayName("AskWrapper.build() - 基础 ASK 语句")
    void testAskWrapperBuild() {
        String sparql = new AskWrapper()
                .prefixFoaf()
                .isA("?s", "foaf:Person")
                .triple("?s", "foaf:name", "?name")
                .filterEqLiteral("?name", "Alice")
                .build();

        assertTrue(sparql.contains("PREFIX foaf:"));
        assertTrue(sparql.contains("ASK"));
        assertTrue(sparql.contains("WHERE {"));
        assertTrue(sparql.contains("?s a foaf:Person ."));
        assertTrue(sparql.contains("FILTER(?name = \"Alice\")"));
    }

    // =====================================================================
    // UpdateWrapper 构建验证（build()）
    // =====================================================================

    @Test
    @Order(20)
    @DisplayName("UpdateWrapper.build() - INSERT DATA（tripleLiteral）")
    void testUpdateWrapperBuildInsertData() {
        String sparql = new UpdateWrapper()
                .insertData()
                .tripleLiteral("http://example.org/Alice", "http://xmlns.com/foaf/0.1/name", "Alice")
                .build();

        assertTrue(sparql.contains("INSERT DATA {"));
        assertTrue(sparql.contains("<http://example.org/Alice>"));
        assertTrue(sparql.contains("\"Alice\""));
    }

    @Test
    @Order(21)
    @DisplayName("UpdateWrapper.build() - DELETE DATA（tripleUris）")
    void testUpdateWrapperBuildDeleteData() {
        String sparql = new UpdateWrapper()
                .deleteData()
                .tripleUris(
                        "http://example.org/Alice",
                        "http://www.w3.org/1999/02/22-rdf-syntax-ns#type",
                        "http://xmlns.com/foaf/0.1/Person"
                )
                .build();

        assertTrue(sparql.contains("DELETE DATA {"));
        assertTrue(sparql.contains("<http://example.org/Alice>"));
        assertTrue(sparql.contains("<http://xmlns.com/foaf/0.1/Person>"));
    }

    @Test
    @Order(22)
    @DisplayName("UpdateWrapper.build() - DELETE WHERE（无 FILTER）简写形式")
    void testUpdateWrapperBuildDeleteWhere() {
        // 无 FILTER 时使用 DELETE WHERE { } 简写形式
        String sparql = new UpdateWrapper()
                .prefixFoaf()
                .deleteWhere()
                .triple("?s", "foaf:name", "?name")
                .build();

        assertTrue(sparql.contains("DELETE WHERE {"));
        assertTrue(sparql.contains("?s foaf:name ?name ."));
    }

    @Test
    @Order(22)
    @DisplayName("UpdateWrapper.build() - DELETE WHERE + FILTER（自动扩展为 DELETE...WHERE 形式）")
    void testUpdateWrapperBuildDeleteWhereWithFilter() {
        // 有 FILTER 时自动扩展为 DELETE { ... } WHERE { ... FILTER } 形式
        String sparql = new UpdateWrapper()
                .prefixFoaf()
                .deleteWhere()
                .triple("?s", "foaf:name", "?name")
                .filterEqLiteral("?name", "Alice")
                .build();

        // 应包含独立的 DELETE { } 块
        assertTrue(sparql.contains("DELETE {"));
        assertTrue(sparql.contains("?s foaf:name ?name ."));
        // 应包含 WHERE { } 块含 FILTER
        assertTrue(sparql.contains("WHERE {"));
        assertTrue(sparql.contains("FILTER(?name = \"Alice\")"));
    }

    @Test
    @Order(23)
    @DisplayName("UpdateWrapper.build() - INSERT ... WHERE")
    void testUpdateWrapperBuildInsertWhere() {
        String sparql = new UpdateWrapper()
                .prefixFoaf()
                .insertTemplate("?s foaf:knows <http://example.org/Bob> .")
                .triple("?s", "a", "foaf:Person")
                .build();

        assertTrue(sparql.contains("INSERT {"));
        assertTrue(sparql.contains("foaf:knows"));
        assertTrue(sparql.contains("WHERE {"));
        assertTrue(sparql.contains("?s a foaf:Person ."));
    }

    @Test
    @Order(24)
    @DisplayName("UpdateWrapper.build() - DELETE INSERT WHERE（更新属性值）")
    void testUpdateWrapperBuildDeleteInsertWhere() {
        String sparql = new UpdateWrapper()
                .prefixFoaf()
                .deleteInsertTemplate(
                        "?s foaf:age ?oldAge .",
                        "?s foaf:age \"31\" ."
                )
                .triple("?s", "foaf:name", "\"Alice\"")
                .triple("?s", "foaf:age", "?oldAge")
                .build();

        assertTrue(sparql.contains("DELETE {"));
        assertTrue(sparql.contains("INSERT {"));
        assertTrue(sparql.contains("?s foaf:age ?oldAge ."));
        assertTrue(sparql.contains("WHERE {"));
    }

    @Test
    @Order(25)
    @DisplayName("UpdateWrapper.build() - CLEAR DEFAULT / ALL / GRAPH")
    void testUpdateWrapperBuildClear() {
        assertEquals("CLEAR DEFAULT", new UpdateWrapper().clearDefault().build());
        assertEquals("CLEAR ALL", new UpdateWrapper().clearAll().build());
        assertTrue(new UpdateWrapper().clearGraph("http://example.org/g1").build()
                .contains("CLEAR GRAPH <http://example.org/g1>"));
        assertTrue(new UpdateWrapper().dropGraph("http://example.org/g1").build()
                .contains("DROP GRAPH <http://example.org/g1>"));
    }

    @Test
    @Order(26)
    @DisplayName("UpdateWrapper.build() - 未设置模式时抛出 FusekiException")
    void testUpdateWrapperNoModeShouldThrow() {
        assertThrows(FusekiException.class, () -> new UpdateWrapper().build());
    }

    // =====================================================================
    // 集成测试：通过 JenaFusekiTemplate 实际执行
    // =====================================================================

    @Test
    @Order(30)
    @DisplayName("集成 - UpdateWrapper insertData + SelectWrapper 查询")
    void testIntegrationInsertAndSelect() {
        // 插入数据
        template.update(
                new UpdateWrapper()
                        .insertData()
                        .tripleLiteral(
                                "http://example.org/Alice",
                                "http://xmlns.com/foaf/0.1/name",
                                "Alice"
                        )
        );

        // 用 SelectWrapper 查询
        List<Map<String, RDFNode>> results = template.select(
                new SelectWrapper()
                        .prefixFoaf()
                        .select("?name")
                        .triple("?s", "foaf:name", "?name")
        );

        assertFalse(results.isEmpty());
        assertEquals("Alice", results.get(0).get("name").asLiteral().getString());
    }

    @Test
    @Order(31)
    @DisplayName("集成 - UpdateWrapper insertData + AskWrapper 查询")
    void testIntegrationInsertAndAsk() {
        template.update(
                new UpdateWrapper()
                        .insertData()
                        .tripleUris(
                                "http://example.org/Cat",
                                "http://www.w3.org/1999/02/22-rdf-syntax-ns#type",
                                "http://example.org/Animal"
                        )
        );

        boolean exists = template.ask(
                new AskWrapper()
                        .tripleUri("http://example.org/Cat", "?p", "?o")
        );

        assertTrue(exists);
    }

    @Test
    @Order(32)
    @DisplayName("集成 - UpdateWrapper deleteWhere 按条件删除")
    void testIntegrationDeleteWhere() {
        // 先插入两条数据
        template.insertTriple("http://example.org/Alice", "http://xmlns.com/foaf/0.1/name", "Alice");
        template.insertTriple("http://example.org/Bob", "http://xmlns.com/foaf/0.1/name", "Bob");

        // 只删除 Alice
        template.update(
                new UpdateWrapper()
                        .prefixFoaf()
                        .deleteWhere()
                        .triple("?s", "foaf:name", "?name")
                        .filterEqLiteral("?name", "Alice")
        );

        // Alice 已被删除
        assertFalse(template.ask(
                new AskWrapper()
                        .triple("?s", "<http://xmlns.com/foaf/0.1/name>", "\"Alice\"")
        ));

        // Bob 仍然存在
        assertTrue(template.ask(
                new AskWrapper()
                        .triple("?s", "<http://xmlns.com/foaf/0.1/name>", "\"Bob\"")
        ));
    }

    @Test
    @Order(33)
    @DisplayName("集成 - SelectWrapper filterContains 模糊过滤")
    void testIntegrationSelectFilterContains() {
        template.insertTriple("http://example.org/Alice", "http://xmlns.com/foaf/0.1/name", "Alice");
        template.insertTriple("http://example.org/Alicia", "http://xmlns.com/foaf/0.1/name", "Alicia");
        template.insertTriple("http://example.org/Bob", "http://xmlns.com/foaf/0.1/name", "Bob");

        List<String> names = template.select(
                new SelectWrapper()
                        .prefixFoaf()
                        .select("?name")
                        .triple("?s", "foaf:name", "?name")
                        .filterContains("?name", "Ali"),
                (row, i) -> row.getLiteral("name").getString()
        );

        assertEquals(2, names.size());
        assertTrue(names.contains("Alice"));
        assertTrue(names.contains("Alicia"));
    }

    @Test
    @Order(34)
    @DisplayName("集成 - SelectWrapper limit + offset 分页")
    void testIntegrationSelectLimitOffset() {
        for (int i = 1; i <= 5; i++) {
            template.insertTriple(
                    "http://example.org/Person" + i,
                    "http://xmlns.com/foaf/0.1/name",
                    "Person" + i
            );
        }

        List<Map<String, RDFNode>> page1 = template.select(
                new SelectWrapper()
                        .prefixFoaf()
                        .select("?name")
                        .triple("?s", "foaf:name", "?name")
                        .orderByAsc("?name")
                        .limit(2)
                        .offset(0)
        );

        assertEquals(2, page1.size());
    }

    @Test
    @Order(35)
    @DisplayName("集成 - SelectWrapper selectOne 返回第一行")
    void testIntegrationSelectOne() {
        template.insertTriple("http://example.org/Only", "http://xmlns.com/foaf/0.1/name", "OnlyOne");

        Map<String, RDFNode> row = template.selectOne(
                new SelectWrapper()
                        .prefixFoaf()
                        .select("?name")
                        .triple("?s", "foaf:name", "?name")
        );

        assertNotNull(row);
        assertEquals("OnlyOne", row.get("name").asLiteral().getString());
    }

    @Test
    @Order(36)
    @DisplayName("集成 - SelectWrapper selectOne 无结果返回 null")
    void testIntegrationSelectOneNull() {
        Map<String, RDFNode> row = template.selectOne(
                new SelectWrapper()
                        .select("?x")
                        .triple("<http://notexist.org/>", "?p", "?x")
        );

        assertNull(row);
    }

    @Test
    @Order(37)
    @DisplayName("集成 - UpdateWrapper DELETE INSERT WHERE 修改属性值")
    void testIntegrationDeleteInsertWhere() {
        // 先插入旧值
        template.insertTriple("http://example.org/Alice", "http://xmlns.com/foaf/0.1/age", "30");

        // 用 DELETE INSERT WHERE 修改为 31
        template.update(
                new UpdateWrapper()
                        .prefixFoaf()
                        .deleteInsertTemplate(
                                "?s foaf:age ?oldAge .",
                                "?s foaf:age \"31\" ."
                        )
                        .triple("?s", "<http://xmlns.com/foaf/0.1/age>", "?oldAge")
        );

        // 验证旧值已被删除
        assertFalse(template.ask(
                new AskWrapper()
                        .triple("?s", "<http://xmlns.com/foaf/0.1/age>", "\"30\"")
        ));

        // 验证新值存在
        assertTrue(template.ask(
                new AskWrapper()
                        .triple("?s", "<http://xmlns.com/foaf/0.1/age>", "\"31\"")
        ));
    }
}

