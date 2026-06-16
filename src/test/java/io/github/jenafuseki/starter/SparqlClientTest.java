package io.github.jenafuseki.starter;

import io.github.jenafuseki.starter.client.SparqlClient;
import io.github.jenafuseki.starter.config.JenaFusekiProperties;
import io.github.jenafuseki.starter.test.FusekiTestServer;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.junit.jupiter.api.*;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SparqlClient 单元测试
 * 使用内嵌 Fuseki 服务器，测试结束后自动清理
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SparqlClientTest {

    private static FusekiTestServer testServer;
    private static SparqlClient sparqlClient;
    private static String queryEndpoint;
    private static String updateEndpoint;

    @BeforeAll
    static void setUp() {
        testServer = new FusekiTestServer(3737, "test").start();
        queryEndpoint = testServer.getQueryEndpoint();
        updateEndpoint = testServer.getUpdateEndpoint();

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        sparqlClient = new SparqlClient(httpClient, new JenaFusekiProperties());
    }

    @AfterAll
    static void tearDown() {
        if (testServer != null) {
            testServer.stop();
        }
    }

    // =====================================================================
    // INSERT 前置：先清空数据
    // =====================================================================

    @BeforeEach
    void clearData() {
        sparqlClient.executeUpdate(updateEndpoint, "CLEAR ALL");
    }

    // =====================================================================
    // P0: SPARQL UPDATE
    // =====================================================================

    @Test
    @Order(1)
    @DisplayName("INSERT DATA - 插入三元组")
    void testInsertData() {
        String sparql = "PREFIX foaf: <http://xmlns.com/foaf/0.1/> " +
                "INSERT DATA { <http://example.org/Alice> foaf:name \"Alice\" ; foaf:age 30 }";

        assertDoesNotThrow(() -> sparqlClient.executeUpdate(updateEndpoint, sparql));
    }

    // =====================================================================
    // P0: SPARQL SELECT
    // =====================================================================

    @Test
    @Order(2)
    @DisplayName("SELECT - 查询已插入的数据")
    void testSelect() {
        // 先插入
        sparqlClient.executeUpdate(updateEndpoint,
                "PREFIX foaf: <http://xmlns.com/foaf/0.1/> " +
                        "INSERT DATA { <http://example.org/Alice> foaf:name \"Alice\" ; foaf:age 30 }");

        // 再查询
        String sparql = "PREFIX foaf: <http://xmlns.com/foaf/0.1/> " +
                "SELECT ?name ?age WHERE { ?s foaf:name ?name ; foaf:age ?age }";
        ResultSet rs = sparqlClient.executeSelect(queryEndpoint, sparql);

        List<String> names = new ArrayList<>();
        while (rs.hasNext()) {
            QuerySolution row = rs.nextSolution();
            names.add(row.getLiteral("name").getString());
        }

        assertEquals(1, names.size());
        assertEquals("Alice", names.get(0));
    }

    // =====================================================================
    // P0: SPARQL ASK
    // =====================================================================

    @Test
    @Order(3)
    @DisplayName("ASK - 数据存在时返回 true")
    void testAskTrue() {
        sparqlClient.executeUpdate(updateEndpoint,
                "PREFIX foaf: <http://xmlns.com/foaf/0.1/> " +
                        "INSERT DATA { <http://example.org/Bob> foaf:name \"Bob\" }");

        boolean result = sparqlClient.executeAsk(queryEndpoint,
                "PREFIX foaf: <http://xmlns.com/foaf/0.1/> " +
                        "ASK { <http://example.org/Bob> foaf:name \"Bob\" }");

        assertTrue(result);
    }

    @Test
    @Order(4)
    @DisplayName("ASK - 数据不存在时返回 false")
    void testAskFalse() {
        boolean result = sparqlClient.executeAsk(queryEndpoint,
                "ASK { <http://example.org/NotExist> ?p ?o }");

        assertFalse(result);
    }

    // =====================================================================
    // P0: SPARQL CONSTRUCT
    // =====================================================================

    @Test
    @Order(5)
    @DisplayName("CONSTRUCT - 构建子图")
    void testConstruct() {
        sparqlClient.executeUpdate(updateEndpoint,
                "PREFIX foaf: <http://xmlns.com/foaf/0.1/> " +
                        "INSERT DATA { <http://example.org/Charlie> foaf:name \"Charlie\" }");

        String sparql = "PREFIX foaf: <http://xmlns.com/foaf/0.1/> " +
                "CONSTRUCT { ?s foaf:name ?name } " +
                "WHERE { ?s foaf:name ?name }";
        Model model = sparqlClient.executeConstruct(queryEndpoint, sparql);

        assertNotNull(model);
        assertTrue(model.size() > 0, "CONSTRUCT 结果不应为空");
    }

    // =====================================================================
    // P0: DELETE DATA
    // =====================================================================

    @Test
    @Order(6)
    @DisplayName("DELETE DATA - 删除三元组后 ASK 返回 false")
    void testDeleteData() {
        // 插入
        sparqlClient.executeUpdate(updateEndpoint,
                "PREFIX foaf: <http://xmlns.com/foaf/0.1/> " +
                        "INSERT DATA { <http://example.org/Dave> foaf:name \"Dave\" }");

        // 删除
        sparqlClient.executeUpdate(updateEndpoint,
                "PREFIX foaf: <http://xmlns.com/foaf/0.1/> " +
                        "DELETE DATA { <http://example.org/Dave> foaf:name \"Dave\" }");

        // 验证已删除
        boolean exists = sparqlClient.executeAsk(queryEndpoint,
                "PREFIX foaf: <http://xmlns.com/foaf/0.1/> " +
                        "ASK { <http://example.org/Dave> foaf:name \"Dave\" }");

        assertFalse(exists);
    }

    // =====================================================================
    // P0: 多条数据 SELECT
    // =====================================================================

    @Test
    @Order(7)
    @DisplayName("SELECT - 查询多条数据")
    void testSelectMultiple() {
        sparqlClient.executeUpdate(updateEndpoint,
                "PREFIX foaf: <http://xmlns.com/foaf/0.1/> " +
                        "INSERT DATA { " +
                        "  <http://example.org/P1> foaf:name \"Person1\" . " +
                        "  <http://example.org/P2> foaf:name \"Person2\" . " +
                        "  <http://example.org/P3> foaf:name \"Person3\" . " +
                        "}");

        ResultSet rs = sparqlClient.executeSelect(queryEndpoint,
                "PREFIX foaf: <http://xmlns.com/foaf/0.1/> " +
                        "SELECT ?name WHERE { ?s foaf:name ?name } ORDER BY ?name");

        List<String> names = new ArrayList<>();
        while (rs.hasNext()) {
            names.add(rs.nextSolution().getLiteral("name").getString());
        }

        assertEquals(3, names.size());
        assertEquals("Person1", names.get(0));
        assertEquals("Person2", names.get(1));
        assertEquals("Person3", names.get(2));
    }
}

