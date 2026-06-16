package io.github.jenafuseki.starter.init;

import io.github.jenafuseki.starter.client.FusekiDatasetClient;
import io.github.jenafuseki.starter.client.SparqlClient;
import io.github.jenafuseki.starter.config.JenaFusekiProperties;
import io.github.jenafuseki.starter.exception.FusekiException;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Fuseki 数据初始化器
 *
 * <p>在 Spring 容器启动完成后（{@link ContextRefreshedEvent}），自动将配置的 RDF 数据文件
 * 加载到 Fuseki 数据集中。</p>
 *
 * <p>配置示例：</p>
 * <pre>{@code
 * jena:
 *   fuseki:
 *     init:
 *       enabled: true
 *       data-locations:
 *         - classpath:rdf/base-ontology.ttl
 *         - classpath:rdf/initial-data.n3
 *       graph-uri: http://example.org/base
 *       mode: if-empty   # always | if-empty
 * }</pre>
 */
public class FusekiDataInitializer implements ApplicationListener<ContextRefreshedEvent> {

    private static final Logger log = LoggerFactory.getLogger(FusekiDataInitializer.class);

    private final JenaFusekiProperties properties;
    private final FusekiDatasetClient datasetClient;
    private final SparqlClient sparqlClient;
    private final ResourceLoader resourceLoader;

    /**
     * 防止多次初始化（Spring 可能发布多次 ContextRefreshedEvent）
     */
    private volatile boolean initialized = false;

    public FusekiDataInitializer(JenaFusekiProperties properties,
                                  FusekiDatasetClient datasetClient,
                                  SparqlClient sparqlClient,
                                  ResourceLoader resourceLoader) {
        this.properties = properties;
        this.datasetClient = datasetClient;
        this.sparqlClient = sparqlClient;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (initialized) {
            return;
        }
        initialized = true;

        JenaFusekiProperties.Init initConfig = properties.getInit();
        if (!initConfig.isEnabled()) {
            return;
        }

        List<String> dataLocations = initConfig.getDataLocations();
        if (dataLocations == null || dataLocations.isEmpty()) {
            log.warn("jena.fuseki.init.enabled=true 但未配置 data-locations，跳过初始化");
            return;
        }

        String mode = initConfig.getMode();
        String datasetName = properties.getDataset();

        // if-empty 模式：检查数据集是否已有数据
        if ("if-empty".equals(mode)) {
            if (isDatasetNotEmpty(datasetName, initConfig.getGraphUri())) {
                log.info("Fuseki 数据集 {} 已有数据，跳过初始化 (mode=if-empty)", datasetName);
                return;
            }
        }

        log.info("开始初始化 Fuseki 数据集 {}，共 {} 个数据文件",
                datasetName, dataLocations.size());

        for (String location : dataLocations) {
            loadDataFile(location, datasetName, initConfig.getGraphUri());
        }

        log.info("Fuseki 数据集 {} 初始化完成", datasetName);
    }

    // =====================================================================
    // 私有方法
    // =====================================================================

    /**
     * 判断数据集（或指定命名图）是否已有数据
     */
    private boolean isDatasetNotEmpty(String datasetName, String graphUri) {
        try {
            String askSparql;
            if (graphUri != null && !graphUri.isEmpty()) {
                askSparql = "ASK { GRAPH <" + graphUri + "> { ?s ?p ?o } }";
            } else {
                askSparql = "ASK { ?s ?p ?o }";
            }
            String queryEndpoint = properties.buildQueryEndpoint(datasetName);
            return sparqlClient.executeAsk(queryEndpoint, askSparql);
        } catch (Exception e) {
            log.warn("检查数据集是否为空时发生异常，将继续初始化: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 加载单个 RDF 数据文件到指定数据集
     */
    private void loadDataFile(String location, String datasetName, String graphUri) {
        log.debug("加载 RDF 数据文件: {}", location);
        try {
            Resource resource = resourceLoader.getResource(location);
            if (!resource.exists()) {
                throw new FusekiException("RDF 数据文件不存在: " + location);
            }

            Model model = ModelFactory.createDefaultModel();
            try (InputStream is = resource.getInputStream()) {
                // Jena 根据文件扩展名自动识别格式（.ttl → Turtle, .n3 → N3, .rdf → RDF/XML 等）
                String filename = resource.getFilename();
                if (filename != null) {
                    RDFDataMgr.read(model, is, guessBaseUri(location), guessLang(filename));
                } else {
                    RDFDataMgr.read(model, is, guessBaseUri(location), null);
                }
            }

            datasetClient.putModel(datasetName, graphUri, model);
            log.info("已加载 RDF 文件 {} ({} 条三元组) -> 数据集 {}",
                    location, model.size(), datasetName);

        } catch (FusekiException e) {
            throw e;
        } catch (IOException e) {
            throw new FusekiException("读取 RDF 数据文件失败: " + location, e);
        } catch (Exception e) {
            throw new FusekiException("加载 RDF 数据文件失败: " + location + " -> " + e.getMessage(), e);
        }
    }

    /**
     * 根据文件名推断 RDF 格式
     */
    private org.apache.jena.riot.Lang guessLang(String filename) {
        if (filename == null) return org.apache.jena.riot.Lang.TURTLE;
        String lower = filename.toLowerCase();
        if (lower.endsWith(".ttl"))  return org.apache.jena.riot.Lang.TURTLE;
        if (lower.endsWith(".n3"))   return org.apache.jena.riot.Lang.N3;
        if (lower.endsWith(".rdf"))  return org.apache.jena.riot.Lang.RDFXML;
        if (lower.endsWith(".xml"))  return org.apache.jena.riot.Lang.RDFXML;
        if (lower.endsWith(".nt"))   return org.apache.jena.riot.Lang.NTRIPLES;
        if (lower.endsWith(".nq"))   return org.apache.jena.riot.Lang.NQUADS;
        if (lower.endsWith(".trig")) return org.apache.jena.riot.Lang.TRIG;
        if (lower.endsWith(".jsonld")) return org.apache.jena.riot.Lang.JSONLD;
        return org.apache.jena.riot.Lang.TURTLE; // 默认
    }

    /**
     * 生成加载时使用的 base URI
     */
    private String guessBaseUri(String location) {
        return "urn:base:" + location.replaceAll("[^a-zA-Z0-9]", "_");
    }
}

