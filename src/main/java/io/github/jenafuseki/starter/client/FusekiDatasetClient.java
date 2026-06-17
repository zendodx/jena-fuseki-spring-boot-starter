package io.github.jenafuseki.starter.client;

import io.github.jenafuseki.starter.config.JenaFusekiProperties;
import io.github.jenafuseki.starter.exception.FusekiException;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Fuseki 数据集管理客户端
 *
 * <p>通过 Fuseki 管理 API ({@code /$}) 和 Graph Store Protocol (GSP) 管理数据集与 RDF 图：</p>
 * <ul>
 *   <li>列举 / 创建 / 删除数据集（P2 功能）</li>
 *   <li>上传 / 下载 RDF Model（P1 图管理）</li>
 * </ul>
 */
public class FusekiDatasetClient {

    private static final Logger log = LoggerFactory.getLogger(FusekiDatasetClient.class);

    private final JenaFusekiProperties properties;
    private final HttpClient httpClient;

    public FusekiDatasetClient(JenaFusekiProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    // =====================================================================
    // P1: 图管理（Graph Store Protocol - GSP）
    // =====================================================================

    /**
     * 上传 RDF Model 到指定数据集（PUT 覆盖语义）
     *
     * @param datasetName 数据集名称
     * @param graphUri    命名图 URI，null 表示默认图
     * @param model       要上传的 RDF Model
     */
    public void putModel(String datasetName, String graphUri, Model model) {
        String gspUrl = buildGspUrl(datasetName, graphUri);
        log.debug("GSP PUT -> {}", gspUrl);

        // 将 Model 序列化为 Turtle 字节流
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        RDFDataMgr.write(baos, model, Lang.TURTLE);
        byte[] body = baos.toByteArray();

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(gspUrl))
                    .header("Content-Type", "text/turtle")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(body));

            addAuthHeader(builder);

            HttpResponse<String> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            checkResponse(response, "GSP PUT 上传 Model 失败");
            log.debug("GSP PUT 成功, status={}", response.statusCode());
        } catch (FusekiException e) {
            throw e;
        } catch (Exception e) {
            throw new FusekiException("GSP PUT 请求失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从指定数据集下载 RDF Model（GET 语义）
     *
     * @param datasetName 数据集名称
     * @param graphUri    命名图 URI，null 表示默认图
     * @return 下载的 RDF Model
     */
    public Model getModel(String datasetName, String graphUri) {
        String gspUrl = buildGspUrl(datasetName, graphUri);
        log.debug("GSP GET -> {}", gspUrl);

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(gspUrl))
                    .header("Accept", "text/turtle")
                    .GET();

            addAuthHeader(builder);

            HttpResponse<InputStream> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofInputStream()
            );
            checkResponseInputStream(response, "GSP GET 下载 Model 失败");

            Model model = ModelFactory.createDefaultModel();
            RDFDataMgr.read(model, response.body(), Lang.TURTLE);
            log.debug("GSP GET 成功, triples={}", model.size());
            return model;
        } catch (FusekiException e) {
            throw e;
        } catch (Exception e) {
            throw new FusekiException("GSP GET 请求失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除指定数据集中的图（DELETE 语义）
     *
     * @param datasetName 数据集名称
     * @param graphUri    命名图 URI，null 表示默认图
     */
    public void deleteGraph(String datasetName, String graphUri) {
        String gspUrl = buildGspUrl(datasetName, graphUri);
        log.debug("GSP DELETE -> {}", gspUrl);

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(gspUrl))
                    .DELETE();

            addAuthHeader(builder);

            HttpResponse<String> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            checkResponse(response, "GSP DELETE 删除图失败");
        } catch (FusekiException e) {
            throw e;
        } catch (Exception e) {
            throw new FusekiException("GSP DELETE 请求失败: " + e.getMessage(), e);
        }
    }

    // =====================================================================
    // P2: 数据集管理（Fuseki Admin API）
    // =====================================================================

    /**
     * 列举所有数据集名称
     *
     * @return 数据集名称列表
     */
    public List<String> listDatasets() {
        String adminUrl = properties.getServerUrl().replaceAll("/$", "") + "/$/datasets";
        log.debug("Admin API GET -> {}", adminUrl);

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(adminUrl))
                    .header("Accept", "application/json")
                    .GET();

            addAuthHeader(builder);

            HttpResponse<String> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            checkResponse(response, "获取数据集列表失败");
            return parseDatasetNames(response.body());
        } catch (FusekiException e) {
            throw e;
        } catch (Exception e) {
            throw new FusekiException("获取数据集列表失败: " + e.getMessage(), e);
        }
    }

    /**
     * 判断指定数据集是否存在
     *
     * @param datasetName 数据集名称
     * @return true 表示存在
     */
    public boolean datasetExists(String datasetName) {
        return listDatasets().stream().anyMatch(name -> name.equals("/" + datasetName) || name.equals(datasetName));
    }

    /**
     * 创建内存型数据集
     *
     * @param datasetName 数据集名称
     */
    public void createMemDataset(String datasetName) {
        createDataset(datasetName, "mem");
    }

    /**
     * 创建持久化 TDB2 数据集
     *
     * @param datasetName 数据集名称
     */
    public void createTdb2Dataset(String datasetName) {
        createDataset(datasetName, "tdb2");
    }

    /**
     * 创建指定类型的数据集
     *
     * @param datasetName 数据集名称
     * @param dbType      数据集类型：mem / tdb / tdb2
     */
    public void createDataset(String datasetName, String dbType) {
        String adminUrl = properties.getServerUrl().replaceAll("/$", "") + "/$/datasets";
        String formBody = "dbName=" + datasetName + "&dbType=" + dbType;
        log.debug("Admin API POST -> {} (name={}, type={})", adminUrl, datasetName, dbType);

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(adminUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8));

            addAuthHeader(builder);

            HttpResponse<String> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            checkResponse(response, "创建数据集 " + datasetName + " 失败");
            log.info("数据集 {} 创建成功 (type={})", datasetName, dbType);
        } catch (FusekiException e) {
            throw e;
        } catch (Exception e) {
            throw new FusekiException("创建数据集失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除数据集（同时删除数据）
     *
     * @param datasetName 数据集名称
     */
    public void deleteDataset(String datasetName) {
        String adminUrl = properties.getServerUrl().replaceAll("/$", "") + "/$/datasets/" + datasetName;
        log.debug("Admin API DELETE -> {}", adminUrl);

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(adminUrl))
                    .DELETE();

            addAuthHeader(builder);

            HttpResponse<String> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            checkResponse(response, "删除数据集 " + datasetName + " 失败");
            log.info("数据集 {} 已删除", datasetName);
        } catch (FusekiException e) {
            throw e;
        } catch (Exception e) {
            throw new FusekiException("删除数据集失败: " + e.getMessage(), e);
        }
    }

    // =====================================================================
    // 私有工具方法
    // =====================================================================

    /**
     * 构建 GSP 端点 URL
     * 默认图：{serverUrl}/{dataset}/data?default
     * 命名图：{serverUrl}/{dataset}/data?graph={graphUri}
     */
    private String buildGspUrl(String datasetName, String graphUri) {
        String base = properties.getServerUrl().replaceAll("/$", "")
                + "/" + datasetName + "/data";
        if (graphUri == null || graphUri.isEmpty()) {
            return base + "?default";
        }
        try {
            return base + "?graph=" + java.net.URLEncoder.encode(graphUri, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return base + "?graph=" + graphUri;
        }
    }

    /**
     * 添加 Basic Auth 请求头（如果配置了账号密码）
     */
    private void addAuthHeader(HttpRequest.Builder builder) {
        String username = properties.getUsername();
        String password = properties.getPassword();
        if (username != null && !username.isEmpty()) {
            String credentials = username + ":" + (password == null ? "" : password);
            String encoded = Base64.getEncoder().encodeToString(
                    credentials.getBytes(StandardCharsets.UTF_8)
            );
            builder.header("Authorization", "Basic " + encoded);
        }
    }

    /**
     * 检查 HTTP 响应状态码（String body 版本）
     */
    private void checkResponse(HttpResponse<String> response, String errorMessage) {
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new FusekiException(
                    errorMessage + ", HTTP " + status + ": " + response.body()
            );
        }
    }

    /**
     * 检查 HTTP 响应状态码（InputStream body 版本）
     */
    private void checkResponseInputStream(HttpResponse<InputStream> response, String errorMessage) {
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new FusekiException(errorMessage + ", HTTP " + status);
        }
    }

    /**
     * 简单解析 Fuseki 返回的 JSON 数据集列表
     * 格式：{"datasets":[{"ds.name":"/myDataset",...}, ...]}
     * 使用手工解析，避免引入 JSON 库依赖
     */
    private List<String> parseDatasetNames(String json) {
        List<String> names = new ArrayList<>();
        String marker = "\"ds.name\"";
        int pos = 0;
        while ((pos = json.indexOf(marker, pos)) >= 0) {
            int colon = json.indexOf(':', pos);
            int start = json.indexOf('"', colon + 1);
            int end = json.indexOf('"', start + 1);
            if (start >= 0 && end > start) {
                names.add(json.substring(start + 1, end));
            }
            pos = end + 1;
        }
        return names;
    }
}

