---
layout: default
title: starter架构总结
parent: 开发指南
nav_order: 2
---

# jena-fuseki-spring-boot-starter 架构总结

## 一、项目定位

> 让 Spring Boot 项目能像使用 `JdbcTemplate` 操作数据库一样，方便地操作 Apache Jena Fuseki 三元组数据库（知识图谱 / RDF
> 图库）。

业务方只需在 `application.yml` 配置 Fuseki 地址，`@Autowired` 注入 `JenaFusekiTemplate` 即可使用，无需关心底层连接管理、序列化、认证等细节。

---

## 二、技术栈

| 组件                        | 版本                | 说明                                          |
|---------------------------|-------------------|---------------------------------------------|
| JDK                       | 11                | 使用内置 `java.net.http.HttpClient`，无需额外 HTTP 库 |
| Spring Boot               | 2.7.18            | 自动配置、Actuator、配置处理器                         |
| Apache Jena ARQ           | 4.10.0            | SPARQL 查询引擎（核心）                             |
| Apache Jena RDFConnection | 4.10.0            | 远程连接 Fuseki                                 |
| Micrometer                | 随 Spring Boot BOM | 可选指标采集                                      |
| Apache Jena Fuseki Main   | 4.10.0            | **仅测试用**，内嵌 Fuseki 服务器                      |

---

## 三、整体分层架构

```
┌──────────────────────────────────────────────────────────┐
│                     业务应用层                            │
│   @Autowired JenaFusekiTemplate                          │
└──────────────────────┬───────────────────────────────────┘
                       │ 调用
┌──────────────────────▼───────────────────────────────────┐
│              JenaFusekiTemplate（模板层）                  │
│  SELECT / ASK / CONSTRUCT / UPDATE                       │
│  insertTriple / deleteSubject                            │
│  uploadModel / downloadGraph / clearGraph                │
└──────────┬───────────────────────┬───────────────────────┘
           │ SPARQL 操作            │ 图/数据集管理
┌──────────▼──────────┐  ┌─────────▼───────────────────────┐
│   SparqlClient      │  │   FusekiDatasetClient           │
│ (SPARQL Protocol)   │  │ (GSP + Admin API)               │
└──────────┬──────────┘  └─────────┬───────────────────────┘
           │                       │
           └──────────┬────────────┘
                      │ 共享
┌─────────────────────▼────────────────────────────────────┐
│           java.net.http.HttpClient（连接池层）             │
│   Basic Auth · 超时控制 · HTTP/1.1 Keep-Alive            │
└──────────────────────┬───────────────────────────────────┘
                       │ HTTP
┌──────────────────────▼───────────────────────────────────┐
│              Apache Jena Fuseki 服务器                    │
│              （独立部署，不由本 Starter 管理）              │
└──────────────────────────────────────────────────────────┘
```

---

## 四、模块与文件说明

### 4.1 源码结构

```
src/main/java/io/github/jenafuseki/starter/
│
├── autoconfigure/
│   └── JenaFusekiAutoConfiguration.java   ★ 自动配置入口
│
├── config/
│   └── JenaFusekiProperties.java           配置属性（jena.fuseki.*）
│
├── core/
│   ├── JenaFusekiTemplate.java             ★ 业务核心模板类
│   └── RowMapper.java                      SELECT 结果行映射接口
│
├── client/
│   ├── SparqlClient.java                   底层 SPARQL 执行
│   └── FusekiDatasetClient.java            图管理 + 数据集管理
│
├── health/
│   └── FusekiHealthIndicator.java          Actuator 健康检查
│
├── metrics/
│   └── FusekiMetricsInterceptor.java       Micrometer 指标采集
│
├── init/
│   └── FusekiDataInitializer.java          启动时自动加载 RDF 数据
│
└── exception/
    └── FusekiException.java                统一异常（RuntimeException）
```

### 4.2 自动装配注册

```
src/main/resources/META-INF/
├── spring.factories                                       Spring Boot 2.x 注册
└── spring/
    └── org.springframework.boot.autoconfigure
        .AutoConfiguration.imports                         Spring Boot 3.x 注册
```

### 4.3 测试结构

```
src/test/java/io/github/jenafuseki/starter/
├── test/
│   └── FusekiTestServer.java               内嵌 Fuseki 测试服务器
├── SparqlClientTest.java                   7 个 SPARQL 底层操作测试
└── JenaFusekiTemplateTest.java             10 个模板层操作测试
```

---

## 五、各模块职责详解

### 5.1 JenaFusekiAutoConfiguration（自动配置核心）

条件装配逻辑：

```
@ConditionalOnClass(RDFConnection.class)         → classpath 有 Jena 才生效
@ConditionalOnProperty(jena.fuseki.enabled=true) → 配置未关闭才生效（默认开启）

Bean 装配顺序：
  HttpClient（连接池）
      ↓
  SparqlClient + FusekiDatasetClient（共用 HttpClient）
      ↓
  JenaFusekiTemplate（组合上面两个 + Properties）
      ↓
  FusekiHealthIndicator（@ConditionalOnClass HealthIndicator）
  FusekiMetricsInterceptor（@ConditionalOnClass MeterRegistry）
  FusekiDataInitializer（@ConditionalOnProperty jena.fuseki.init.enabled=true）
```

所有 Bean 均加 `@ConditionalOnMissingBean`，**业务方可自定义覆盖任意 Bean**。

### 5.2 JenaFusekiProperties（配置属性）

前缀：`jena.fuseki`

| 属性                    | 类型             | 默认值                   | 说明                    |
|-----------------------|----------------|-----------------------|-----------------------|
| `enabled`             | boolean        | true                  | 是否启用                  |
| `server-url`          | String         | http://localhost:3030 | Fuseki 服务器地址          |
| `dataset`             | String         | myDataset             | 默认数据集名称               |
| `username`            | String         | -                     | Basic Auth 用户名        |
| `password`            | String         | -                     | Basic Auth 密码         |
| `connect-timeout`     | int            | 5000                  | 连接超时（ms）              |
| `read-timeout`        | int            | 30000                 | 读取超时（ms）              |
| `max-connections`     | int            | 20                    | 连接池最大连接数              |
| `init.enabled`        | boolean        | false                 | 是否启用启动初始化             |
| `init.data-locations` | List\<String\> | -                     | RDF 文件路径列表            |
| `init.graph-uri`      | String         | -                     | 加载目标命名图 URI           |
| `init.mode`           | String         | if-empty              | 加载模式（always/if-empty） |

内置 URL 构建方法：

- `buildQueryEndpoint()` → `{serverUrl}/{dataset}/sparql`
- `buildUpdateEndpoint()` → `{serverUrl}/{dataset}/update`
- `buildGspEndpoint()` → `{serverUrl}/{dataset}/data`

### 5.3 SparqlClient（SPARQL 底层执行）

基于 Jena `RDFConnectionRemote`，每次调用新建连接（底层 `HttpClient` 复用连接池）：

| 方法                                   | SPARQL 类型 | 返回值              |
|--------------------------------------|-----------|------------------|
| `executeSelect(endpoint, sparql)`    | SELECT    | `ResultSet`（深拷贝） |
| `executeAsk(endpoint, sparql)`       | ASK       | `boolean`        |
| `executeConstruct(endpoint, sparql)` | CONSTRUCT | `Model`          |
| `executeDescribe(endpoint, sparql)`  | DESCRIBE  | `Model`          |
| `executeUpdate(endpoint, sparql)`    | UPDATE    | void             |

### 5.4 JenaFusekiTemplate（业务模板层）

面向业务的高级 API，屏蔽 endpoint 拼接、ResultSet 遍历等细节：

**SELECT 系列**

- `select(sparql)` → `List<Map<String, RDFNode>>`
- `select(sparql, RowMapper<T>)` → `List<T>`
- `selectOne(sparql)` / `selectOne(sparql, RowMapper<T>)` → 单行
- 以上均支持 `(sparql, datasetName, ...)` 指定数据集

**ASK**

- `ask(sparql)` → `boolean`

**CONSTRUCT / DESCRIBE**

- `construct(sparql)` → `Model`
- `describe(sparql)` → `Model`

**UPDATE**

- `update(sparql)` → void

**便捷三元组操作**

- `insertTriple(subject, predicate, object)` — 字面量宾语
- `insertTripleUri(subject, predicate, objectUri)` — URI 宾语
- `deleteTriple(subject, predicate, object)`
- `deleteSubject(subject)` — 删除某主语的所有三元组

**图管理（P1）**

- `uploadModel(model)` / `uploadNamedGraph(graphUri, model)`
- `downloadDefaultGraph()` / `downloadNamedGraph(graphUri)`
- `clearDefaultGraph()` / `clearGraph(graphUri)` / `dropNamedGraph(graphUri)`
- `clearAll()` — 清空整个数据集

### 5.5 FusekiDatasetClient（图/数据集管理）

通过两种 HTTP API 操作 Fuseki：

**Graph Store Protocol（GSP）— 图管理**
| 操作 | HTTP 方法 | URL 规则 |
|------|----------|---------|
| 上传图 | PUT | `/dataset/data?default` 或 `?graph=URI` |
| 下载图 | GET | 同上 |
| 删除图 | DELETE | 同上 |

**Fuseki Admin API（/$/）— 数据集管理（P2）**
| 方法 | API 端点 | 说明 |
|------|---------|------|
| `listDatasets()` | GET `/$/datasets` | 列举所有数据集 |
| `createMemDataset(name)` | POST `/$/datasets` | 创建内存型数据集 |
| `createTdb2Dataset(name)` | POST `/$/datasets` | 创建持久化数据集 |
| `deleteDataset(name)` | DELETE `/$/datasets/{name}` | 删除数据集 |
| `datasetExists(name)` | 复用 listDatasets | 判断是否存在 |

### 5.6 FusekiHealthIndicator（P1）

继承 `AbstractHealthIndicator`，调用 `GET /$/ping` 检测 Fuseki 可用性：

```json
{
  "status": "UP",
  "components": {
    "fuseki": {
      "status": "UP",
      "details": {
        "serverUrl": "http://localhost:3030",
        "dataset": "myDataset",
        "pingMs": 12
      }
    }
  }
}
```

仅在 classpath 存在 `spring-boot-starter-actuator` 时自动注册（`@ConditionalOnClass`）。

### 5.7 FusekiMetricsInterceptor（P2）

基于 Micrometer 暴露以下指标：

| 指标名                             | 类型      | 说明             |
|---------------------------------|---------|----------------|
| `fuseki.sparql.select.count`    | Counter | SELECT 执行次数    |
| `fuseki.sparql.select.duration` | Timer   | SELECT 执行耗时分布  |
| `fuseki.sparql.ask.count`       | Counter | ASK 执行次数       |
| `fuseki.sparql.construct.count` | Counter | CONSTRUCT 执行次数 |
| `fuseki.sparql.update.count`    | Counter | UPDATE 执行次数    |
| `fuseki.sparql.update.duration` | Timer   | UPDATE 执行耗时分布  |
| `fuseki.sparql.error.count`     | Counter | 执行异常次数         |

仅在 classpath 存在 `micrometer-core` 时自动注册。

### 5.8 FusekiDataInitializer（P2）

监听 `ContextRefreshedEvent`，容器启动完成后按配置自动加载 RDF 文件：

- 支持路径前缀：`classpath:` / `file:` / `http:`
- 根据文件扩展名自动识别格式：`.ttl` / `.n3` / `.rdf` / `.nt` / `.nq` / `.trig` / `.jsonld`
- 加载模式：
    - `always`：每次启动都加载（先清空目标图）
    - `if-empty`：仅当目标图为空时加载（默认）
- 防重入：`volatile boolean initialized` 确保多次 `ContextRefreshedEvent` 只执行一次

---

## 六、关键设计原则

| 原则           | 实现方式                                                                        |
|--------------|-----------------------------------------------------------------------------|
| **零侵入自动配置**  | 引入依赖即生效，无需任何 `@Enable` 注解或 XML                                              |
| **业务方可覆盖**   | 所有 Bean 加 `@ConditionalOnMissingBean`，自定义 Bean 优先                           |
| **可选功能按需激活** | Actuator / Micrometer 用 `@ConditionalOnClass`；初始化用 `@ConditionalOnProperty` |
| **连接复用**     | 全局共享单个 `HttpClient`，底层 HTTP/1.1 Keep-Alive 复用连接                             |
| **统一异常**     | 所有底层异常包装为 `FusekiException`（RuntimeException），不强制 try-catch                 |
| **IDE 友好**   | `spring-boot-configuration-processor` 生成元数据，YAML 配置有自动补全                    |
| **测试友好**     | 测试依赖 `jena-fuseki-main`（scope=test），单测零外部依赖                                 |

---

## 七、依赖关系图（Bean 依赖）

```
JenaFusekiProperties ←── @EnableConfigurationProperties
        │
        ├──→ HttpClient（fusekiHttpClient）
        │           │
        │           ├──→ SparqlClient
        │           │           │
        │           │           └──→ JenaFusekiTemplate ←── FusekiDatasetClient
        │           │
        │           ├──→ FusekiDatasetClient
        │           │
        │           └──→ FusekiHealthIndicator（可选）
        │
        └──→ FusekiDataInitializer（可选，需 jena.fuseki.init.enabled=true）

MeterRegistry ──→ FusekiMetricsInterceptor（可选）
```

---

## 八、测试策略

| 测试类                      | 覆盖场景                                               | 使用端口 |
|--------------------------|----------------------------------------------------|------|
| `SparqlClientTest`       | INSERT / SELECT / ASK / CONSTRUCT / DELETE（底层 API） | 3737 |
| `JenaFusekiTemplateTest` | Template 全量方法 + 图管理（上传/下载/清空/命名图）                  | 3738 |

两个测试类各自独立使用内嵌 Fuseki 实例（`FusekiTestServer`），互不干扰，`@BeforeEach` 清空数据保证测试幂等性。

构建结果：**17 个测试，Failures: 0，Errors: 0**

