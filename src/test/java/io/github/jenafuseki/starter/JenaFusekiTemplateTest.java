package io.github.jenafuseki.starter;

import io.github.jenafuseki.starter.client.FusekiDatasetClient;
import io.github.jenafuseki.starter.client.SparqlClient;
import io.github.jenafuseki.starter.config.JenaFusekiProperties;
import io.github.jenafuseki.starter.core.JenaFusekiTemplate;
import io.github.jenafuseki.starter.test.FusekiTestServer;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.*;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JenaFusekiTemplate 集成测试
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JenaFusekiTemplateTest {

    private static final int PORT = 3738;
    private static final String DATASET = "templateTest";

    private static FusekiTestServer testServer;
    private static JenaFusekiTemplate template;

    @BeforeAll
    static void setUp() {
        testServer = new FusekiTestServer(PORT, DATASET).start();

        // 配置属性
        JenaFusekiProperties props = new JenaFusekiProperties();
        props.setServerUrl(testServer.getServerUrl());
        props.setDataset(DATASET);

        // 构建依赖
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
    // P0: SELECT
    // =====================================================================

    @Test
    @Order(1)
    @DisplayName("select() - 返回 Map 列表")
    void testSelectReturnsMapList() {
        template.insertTriple(
                "http://example.org/Alice",
                "http://xmlns.com/foaf/0.1/name",
                "Alice"
        );

        List<Map<String, org.apache.jena.rdf.model.RDFNode>> results = template.select(
                "PREFIX foaf: <http://xmlns.com/foaf/0.1/> " +
                        "SELECT ?name WHERE { ?s foaf:name ?name }"
        );

        assertFalse(results.isEmpty());
        assertEquals("Alice", results.get(0).get("name").asLiteral().getString());
    }

    @Test
    @Order(2)
    @DisplayName("select() - RowMapper 映射为 String")
    void testSelectWithRowMapper() {
        template.insertTriple("http://example.org/Bob", "http://xmlns.com/foaf/0.1/name", "Bob");

        List<String> names = template.select(
                "PREFIX foaf: <http://xmlns.com/foaf/0.1/> SELECT ?name WHERE { ?s foaf:name ?name }",
                (row, i) -> row.getLiteral("name").getString()
        );

        assertEquals(1, names.size());
        assertEquals("Bob", names.get(0));
    }

    @Test
    @Order(3)
    @DisplayName("selectOne() - 无结果返回 null")
    void testSelectOneReturnsNull() {
        var result = template.selectOne(
                "SELECT ?x WHERE { <http://example.org/NotExist> ?p ?x }"
        );
        assertNull(result);
    }

    // =====================================================================
    // P0: ASK
    // =====================================================================

    @Test
    @Order(4)
    @DisplayName("ask() - 三元组存在返回 true")
    void testAskTrue() {
        template.insertTripleUri(
                "http://example.org/Cat",
                "http://www.w3.org/1999/02/22-rdf-syntax-ns#type",
                "http://example.org/Animal"
        );

        boolean result = template.ask(
                "ASK { <http://example.org/Cat> a <http://example.org/Animal> }"
        );
        assertTrue(result);
    }

    // =====================================================================
    // P0: UPDATE
    // =====================================================================

    @Test
    @Order(5)
    @DisplayName("update() - 执行 SPARQL DELETE WHERE")
    void testUpdateDeleteWhere() {
        template.insertTriple("http://example.org/X", "http://xmlns.com/foaf/0.1/name", "X");
        template.update("DELETE WHERE { <http://example.org/X> ?p ?o }");
        assertFalse(template.ask("ASK { <http://example.org/X> ?p ?o }"));
    }

    // =====================================================================
    // P0: deleteSubject
    // =====================================================================

    @Test
    @Order(6)
    @DisplayName("deleteSubject() - 删除主语的所有三元组")
    void testDeleteSubject() {
        template.insertTriple("http://example.org/Y", "http://xmlns.com/foaf/0.1/name", "Y");
        template.insertTriple("http://example.org/Y", "http://xmlns.com/foaf/0.1/age", "25");
        template.deleteSubject("http://example.org/Y");
        assertFalse(template.ask("ASK { <http://example.org/Y> ?p ?o }"));
    }

    // =====================================================================
    // P0: CONSTRUCT
    // =====================================================================

    @Test
    @Order(7)
    @DisplayName("construct() - 返回非空 Model")
    void testConstruct() {
        template.insertTriple("http://example.org/Z", "http://xmlns.com/foaf/0.1/name", "Z");

        Model subGraph = template.construct(
                "PREFIX foaf: <http://xmlns.com/foaf/0.1/> " +
                        "CONSTRUCT { ?s foaf:name ?n } WHERE { ?s foaf:name ?n }"
        );

        assertNotNull(subGraph);
        assertTrue(subGraph.size() > 0);
    }

    // =====================================================================
    // P1: 图管理
    // =====================================================================

    @Test
    @Order(8)
    @DisplayName("uploadModel() / downloadDefaultGraph() - 图上传下载")
    void testUploadAndDownloadModel() {
        // 构造一个 Model
        Model uploadModel = ModelFactory.createDefaultModel();
        uploadModel.add(
                ResourceFactory.createResource("http://example.org/GraphTest"),
                RDFS.label,
                ResourceFactory.createStringLiteral("GraphTestLabel")
        );

        // 上传
        template.uploadModel(uploadModel);

        // 下载
        Model downloadedModel = template.downloadDefaultGraph();
        assertNotNull(downloadedModel);
        assertTrue(downloadedModel.size() > 0, "下载的 Model 不应为空");
    }

    @Test
    @Order(9)
    @DisplayName("clearDefaultGraph() - 清空默认图后 ASK 返回 false")
    void testClearDefaultGraph() {
        template.insertTriple("http://example.org/Clear", "http://xmlns.com/foaf/0.1/name", "Clear");
        assertTrue(template.ask("ASK { <http://example.org/Clear> ?p ?o }"));

        template.clearDefaultGraph();

        assertFalse(template.ask("ASK { <http://example.org/Clear> ?p ?o }"));
    }

    @Test
    @Order(10)
    @DisplayName("uploadNamedGraph() / downloadNamedGraph() - 命名图操作")
    void testNamedGraph() {
        String graphUri = "http://example.org/namedGraph1";

        Model model = ModelFactory.createDefaultModel();
        model.add(
                ResourceFactory.createResource("http://example.org/NG"),
                RDFS.label,
                ResourceFactory.createStringLiteral("NamedGraphLabel")
        );

        template.uploadNamedGraph(graphUri, model);

        Model downloaded = template.downloadNamedGraph(graphUri);
        assertNotNull(downloaded);
        assertTrue(downloaded.size() > 0);
    }
}

