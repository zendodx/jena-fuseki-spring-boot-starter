---
layout: default
title: API文档
parent: 开发指南
nav_order: 4
---


# API 文档

本文档涵盖 `jena-fuseki-spring-boot-starter` 对外暴露的全部公开 API，包括配置属性、核心模板、底层客户端及扩展接口。

---

## 目录

- [配置属性（JenaFusekiProperties）](#配置属性jenaFusekiproperties)
- [核心模板（JenaFusekiTemplate）](#核心模板jenafusekitemplate)
    - [SPARQL SELECT 查询](#sparql-select-查询)
    - [SPARQL ASK 查询](#sparql-ask-查询)
    - [SPARQL CONSTRUCT / DESCRIBE 查询](#sparql-construct--describe-查询)
    - [SPARQL UPDATE 写入](#sparql-update-写入)
    - [便捷三元组操作](#便捷三元组操作)
    - [图（Graph）管理](#图graph管理)
- [SPARQL 构建器（Wrapper）](#sparql-构建器wrapper)
    - [SparqlWrapper 公共 API](#sparqlwrapper-公共-api)
    - [SelectWrapper](#selectwrapper)
    - [AskWrapper](#askwrapper)
    - [UpdateWrapper](#updatewrapper)
- [数据集管理客户端（FusekiDatasetClient）](#数据集管理客户端fusekidatasetclient)
    - [图管理（Graph Store Protocol）](#图管理graph-store-protocol)
    - [数据集管理（Admin API）](#数据集管理admin-api)
- [底层 SPARQL 客户端（SparqlClient）](#底层-sparql-客户端sparqlclient)
- [行映射器（RowMapper）](#行映射器rowmapper)
- [异常（FusekiException）](#异常fusekiexception)
- [健康检查（FusekiHealthIndicator）](#健康检查fusekihealthindicator)
- [指标采集（FusekiMetricsInterceptor）](#指标采集fusekimetricsinterceptor)

---

## 配置属性（JenaFusekiProperties）

配置前缀：`jena.fuseki`

```yaml
jena:
  fuseki:
    enabled: true                          # 是否启用自动配置，默认 true
    server-url: http://localhost:3030      # Fuseki 服务器地址
    dataset: myDataset                     # 默认操作的数据集名称
    username: admin                        # Basic Auth 用户名（可选）
    password: admin123                     # Basic Auth 密码（可选）
    connect-timeout: 5000                  # 连接超时（毫秒），默认 5000
    read-timeout: 30000                    # 读取超时（毫秒），默认 30000
    max-connections: 20                    # 最大连接数，默认 20
    show-sparql: false                     # 是否以 INFO 级别打印执行的 SPARQL 语句，默认 false
    init:
      enabled: false                       # 是否开启启动时数据初始化，默认 false
      data-locations: # RDF 数据文件路径，支持 classpath:/file:/http: 前缀
        - classpath:rdf/base-ontology.ttl
        - classpath:rdf/initial-data.n3
      graph-uri: http://example.org/base  # 加载到指定命名图，不填则加载到默认图
      mode: if-empty                       # 加载模式：always（每次启动）/ if-empty（仅数据集为空时）
```

### 属性说明

| 属性                                | 类型             | 默认值                     | 说明                                                |
|-----------------------------------|----------------|-------------------------|---------------------------------------------------|
| `jena.fuseki.enabled`             | `boolean`      | `true`                  | 是否启用 Fuseki 自动配置                                  |
| `jena.fuseki.server-url`          | `String`       | `http://localhost:3030` | Fuseki 服务器地址                                      |
| `jena.fuseki.dataset`             | `String`       | —                       | 默认数据集名称，SPARQL 端点为 `{serverUrl}/{dataset}/sparql` |
| `jena.fuseki.username`            | `String`       | —                       | Basic Auth 用户名                                    |
| `jena.fuseki.password`            | `String`       | —                       | Basic Auth 密码                                     |
| `jena.fuseki.connect-timeout`     | `int`          | `5000`                  | 连接超时（ms）                                          |
| `jena.fuseki.read-timeout`        | `int`          | `30000`                 | 读取超时（ms）                                          |
| `jena.fuseki.max-connections`     | `int`          | `20`                    | 最大连接数（连接池大小）                                      |
| `jena.fuseki.show-sparql`         | `boolean`      | `false`                 | 是否以 `INFO` 级别打印每条 SPARQL 语句，用于开发调试；生产环境建议保持 `false` |
| `jena.fuseki.init.enabled`        | `boolean`      | `false`                 | 是否启用启动时数据初始化                                      |
| `jena.fuseki.init.data-locations` | `List<String>` | —                       | RDF 文件路径列表                                        |
| `jena.fuseki.init.graph-uri`      | `String`       | —                       | 数据加载目标命名图 URI                                     |
| `jena.fuseki.init.mode`           | `String`       | `if-empty`              | 加载模式：`always` / `if-empty`                        |

---

## 核心模板（JenaFusekiTemplate）

`JenaFusekiTemplate` 是面向业务的核心操作入口，类比 Spring 的 `JdbcTemplate`，自动注入后即可使用：

```java

@Autowired
private JenaFusekiTemplate fusekiTemplate;
```

---

### SPARQL SELECT 查询

#### `select(String sparql)`

执行 SPARQL SELECT 查询，使用默认数据集，返回原始 `RDFNode` 映射。

```java
List<Map<String, RDFNode>> rows = fusekiTemplate.select(
        "SELECT ?name ?age WHERE { ?s foaf:name ?name ; foaf:age ?age }"
);
```

**参数：**

- `sparql` — SPARQL SELECT 语句

**返回：** `List<Map<String, RDFNode>>`，每行是变量名到 `RDFNode` 的映射。

---

#### `select(String sparql, String datasetName)`

执行 SPARQL SELECT 查询，指定目标数据集。

```java
List<Map<String, RDFNode>> rows = fusekiTemplate.select(sparql, "otherDataset");
```

**参数：**

- `sparql` — SPARQL SELECT 语句
- `datasetName` — 目标数据集名称

**返回：** `List<Map<String, RDFNode>>`

---

#### `select(String sparql, RowMapper<T> rowMapper)`

执行 SPARQL SELECT 查询，通过 `RowMapper` 将每行结果映射为目标对象，使用默认数据集。

```java
List<String> names = fusekiTemplate.select(
        "SELECT ?name WHERE { ?s foaf:name ?name }",
        (row, i) -> row.getLiteral("name").getString()
);
```

**参数：**

- `sparql` — SPARQL SELECT 语句
- `rowMapper` — 行映射器 `RowMapper<T>`

**返回：** `List<T>`

---

#### `select(String sparql, String datasetName, RowMapper<T> rowMapper)`

执行 SPARQL SELECT 查询，指定数据集和行映射器。

**参数：**

- `sparql` — SPARQL SELECT 语句
- `datasetName` — 目标数据集名称
- `rowMapper` — 行映射器

**返回：** `List<T>`

---

#### `selectOne(String sparql)`

执行 SPARQL SELECT 查询，仅返回第一行，无结果时返回 `null`，使用默认数据集。

```java
Map<String, RDFNode> row = fusekiTemplate.selectOne(
        "SELECT ?name WHERE { <http://example.org/Alice> foaf:name ?name }"
);
```

**参数：**

- `sparql` — SPARQL SELECT 语句

**返回：** `Map<String, RDFNode>` 或 `null`

---

#### `selectOne(String sparql, RowMapper<T> rowMapper)`

执行 SPARQL SELECT 查询，映射第一行为目标对象，无结果时返回 `null`。

```java
String name = fusekiTemplate.selectOne(sparql,
        (row, i) -> row.getLiteral("name").getString()
);
```

**参数：**

- `sparql` — SPARQL SELECT 语句
- `rowMapper` — 行映射器

**返回：** `T` 或 `null`

---

### SPARQL ASK 查询

#### `ask(String sparql)`

执行 SPARQL ASK 查询，使用默认数据集。

```java
boolean exists = fusekiTemplate.ask(
        "ASK { <http://example.org/Alice> a foaf:Person }"
);
```

**参数：**

- `sparql` — SPARQL ASK 语句

**返回：** `boolean`，`true` 表示模式匹配。

---

#### `ask(String sparql, String datasetName)`

执行 SPARQL ASK 查询，指定目标数据集。

**参数：**

- `sparql` — SPARQL ASK 语句
- `datasetName` — 目标数据集名称

**返回：** `boolean`

---

### SPARQL CONSTRUCT / DESCRIBE 查询

#### `construct(String sparql)`

执行 SPARQL CONSTRUCT 查询，返回构建的 RDF Model，使用默认数据集。

```java
Model model = fusekiTemplate.construct(
        "CONSTRUCT { ?s ?p ?o } WHERE { ?s a foaf:Person ; ?p ?o }"
);
```

**参数：**

- `sparql` — SPARQL CONSTRUCT 语句

**返回：** `org.apache.jena.rdf.model.Model`

---

#### `construct(String sparql, String datasetName)`

执行 SPARQL CONSTRUCT 查询，指定目标数据集。

**参数：**

- `sparql` — SPARQL CONSTRUCT 语句
- `datasetName` — 目标数据集名称

**返回：** `Model`

---

#### `describe(String sparql)`

执行 SPARQL DESCRIBE 查询，返回描述的 RDF Model，使用默认数据集。

```java
Model model = fusekiTemplate.describe(
        "DESCRIBE <http://example.org/Alice>"
);
```

**参数：**

- `sparql` — SPARQL DESCRIBE 语句

**返回：** `Model`

---

#### `describe(String sparql, String datasetName)`

执行 SPARQL DESCRIBE 查询，指定目标数据集。

**参数：**

- `sparql` — SPARQL DESCRIBE 语句
- `datasetName` — 目标数据集名称

**返回：** `Model`

---

### SPARQL UPDATE 写入

#### `update(String sparql)`

执行 SPARQL UPDATE（支持 `INSERT DATA`、`DELETE DATA`、`CLEAR` 等），使用默认数据集。

```java
fusekiTemplate.update(
    "INSERT DATA { <http://example.org/Alice> foaf:name \"Alice\" }"
);
```

**参数：**

- `sparql` — SPARQL UPDATE 语句

**返回：** `void`

---

#### `update(String sparql, String datasetName)`

执行 SPARQL UPDATE，指定目标数据集。

**参数：**

- `sparql` — SPARQL UPDATE 语句
- `datasetName` — 目标数据集名称

**返回：** `void`

---

### 便捷三元组操作

#### `insertTriple(String subject, String predicate, String object)`

插入单条三元组，宾语为字符串字面量。

```java
fusekiTemplate.insertTriple(
    "http://example.org/Alice",
            "http://xmlns.com/foaf/0.1/name",
            "Alice"
);
```

**参数：**

- `subject` — 主语 URI
- `predicate` — 谓语 URI
- `object` — 宾语字符串字面量

**返回：** `void`

---

#### `insertTripleUri(String subject, String predicate, String objectUri)`

插入单条三元组，宾语为 URI。

```java
fusekiTemplate.insertTripleUri(
    "http://example.org/Alice",
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#type",
            "http://xmlns.com/foaf/0.1/Person"
);
```

**参数：**

- `subject` — 主语 URI
- `predicate` — 谓语 URI
- `objectUri` — 宾语 URI

**返回：** `void`

---

#### `deleteTriple(String subject, String predicate, String object)`

删除单条三元组，宾语为字符串字面量。

```java
fusekiTemplate.deleteTriple(
    "http://example.org/Alice",
            "http://xmlns.com/foaf/0.1/name",
            "Alice"
);
```

**参数：**

- `subject` — 主语 URI
- `predicate` — 谓语 URI
- `object` — 宾语字符串字面量

**返回：** `void`

---

#### `deleteSubject(String subject)`

删除某个主语的所有三元组（`DELETE WHERE { <subject> ?p ?o }`）。

```java
fusekiTemplate.deleteSubject("http://example.org/Alice");
```

**参数：**

- `subject` — 主语 URI

**返回：** `void`

---

### 图（Graph）管理

#### `uploadModel(Model model)`

将 RDF Model 上传到默认数据集的默认图（PUT 覆盖语义）。

```java
Model model = ModelFactory.createDefaultModel();
// ... 构建 model ...
fusekiTemplate.

uploadModel(model);
```

**参数：**

- `model` — 要上传的 RDF Model

**返回：** `void`

---

#### `uploadModel(Model model, String datasetName)`

将 RDF Model 上传到指定数据集的默认图（PUT 覆盖语义）。

**参数：**

- `model` — 要上传的 RDF Model
- `datasetName` — 目标数据集名称

**返回：** `void`

---

#### `uploadNamedGraph(String graphUri, Model model)`

将 RDF Model 上传到默认数据集的命名图（PUT 覆盖语义）。

```java
fusekiTemplate.uploadNamedGraph("http://example.org/graph1",model);
```

**参数：**

- `graphUri` — 命名图 URI（不能为空）
- `model` — 要上传的 RDF Model

**返回：** `void`

**异常：** `graphUri` 为空时抛出 `FusekiException`

---

#### `uploadNamedGraph(String graphUri, Model model, String datasetName)`

将 RDF Model 上传到指定数据集的命名图（PUT 覆盖语义）。

**参数：**

- `graphUri` — 命名图 URI（不能为空）
- `model` — 要上传的 RDF Model
- `datasetName` — 目标数据集名称

**返回：** `void`

---

#### `downloadDefaultGraph()`

下载默认数据集默认图的 RDF Model。

```java
Model model = fusekiTemplate.downloadDefaultGraph();
```

**返回：** `Model`

---

#### `downloadDefaultGraph(String datasetName)`

下载指定数据集默认图的 RDF Model。

**参数：**

- `datasetName` — 目标数据集名称

**返回：** `Model`

---

#### `downloadNamedGraph(String graphUri)`

下载默认数据集中命名图的 RDF Model。

```java
Model model = fusekiTemplate.downloadNamedGraph("http://example.org/graph1");
```

**参数：**

- `graphUri` — 命名图 URI

**返回：** `Model`

---

#### `downloadNamedGraph(String graphUri, String datasetName)`

下载指定数据集中命名图的 RDF Model。

**参数：**

- `graphUri` — 命名图 URI
- `datasetName` — 目标数据集名称

**返回：** `Model`

---

#### `clearDefaultGraph()`

清空默认图（执行 `CLEAR DEFAULT`，删除所有三元组但保留图结构）。

```java
fusekiTemplate.clearDefaultGraph();
```

**返回：** `void`

---

#### `clearGraph(String graphUri)`

清空命名图（执行 `CLEAR GRAPH <graphUri>`，删除所有三元组但保留图结构）。

```java
fusekiTemplate.clearGraph("http://example.org/graph1");
```

**参数：**

- `graphUri` — 命名图 URI

**返回：** `void`

---

#### `dropNamedGraph(String graphUri)`

删除命名图（执行 `DROP GRAPH <graphUri>`，删除图及其所有三元组）。

```java
fusekiTemplate.dropNamedGraph("http://example.org/graph1");
```

**参数：**

- `graphUri` — 命名图 URI

**返回：** `void`

---

#### `clearAll()`

清空整个数据集的所有图（执行 `CLEAR ALL`）。

```java
fusekiTemplate.clearAll();
```

**返回：** `void`

---

---

## SPARQL 构建器（Wrapper）

Wrapper 提供类似 MyBatis-Plus `QueryWrapper` 的链式 API，避免手动拼接原始 SPARQL 字符串。三个构建器继承同一抽象基类 `SparqlWrapper`，构建完成后通过 `JenaFusekiTemplate` 的重载方法直接执行。

```java
// SELECT 示例
List<Map<String, RDFNode>> rows = fusekiTemplate.select(
    new SelectWrapper()
        .prefixFoaf()
        .select("?name", "?age")
        .triple("?s", "a", "foaf:Person")
        .triple("?s", "foaf:name", "?name")
        .filterGt("?age", "18")
        .orderByAsc("?name")
        .limit(10)
);

// ASK 示例
boolean exists = fusekiTemplate.ask(
    new AskWrapper()
        .prefixFoaf()
        .isA("?s", "foaf:Person")
        .filterEqLiteral("?s", "http://example.org/Alice")
);

// UPDATE 示例
fusekiTemplate.update(
    new UpdateWrapper()
        .prefixFoaf()
        .insertData()
        .triple("<http://example.org/Alice>", "foaf:name", "\"Alice\"")
);
```

---

### SparqlWrapper 公共 API

所有 Wrapper 均继承以下方法（链式返回 `this`）：

#### 前缀声明

| 方法 | 生成语句 |
|------|----------|
| `prefixRdf()` | `PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>` |
| `prefixRdfs()` | `PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>` |
| `prefixOwl()` | `PREFIX owl: <http://www.w3.org/2002/07/owl#>` |
| `prefixXsd()` | `PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>` |
| `prefixFoaf()` | `PREFIX foaf: <http://xmlns.com/foaf/0.1/>` |
| `prefix(alias, uri)` | `PREFIX alias: <uri>` — 自定义前缀 |

#### WHERE 子句 — 图模式

| 方法 | 说明 | 生成示例 |
|------|------|----------|
| `triple(s, p, o)` | 基础三元组 | `?s foaf:name ?name .` |
| `tripleUri(subjectUri, p, o)` | 主语自动包裹 `<>` | `<http://...> foaf:name ?name .` |
| `isA(subject, type)` | `rdf:type` 简写 | `?s a foaf:Person .` |
| `optional(pattern)` | 可选块 | `OPTIONAL { ?s foaf:age ?age . }` |
| `union(left, right)` | 联合块 | `{ ... } UNION { ... }` |
| `graph(graphUri, pattern)` | 命名图块 | `GRAPH <uri> { ... }` |
| `minus(pattern)` | 差集块 | `MINUS { ... }` |
| `bind(expr, asVar)` | 变量绑定 | `BIND(STRLEN(?name) AS ?len)` |
| `values(var, ...vals)` | 单变量内联数据 | `VALUES ?x { v1 v2 }` |
| `valuesMulti(vars[], rows[][])` | 多变量内联数据表 | `VALUES (?x ?y) { (v1 v2) }` |
| `pattern(rawPattern)` | 原始片段兜底（属性路径、SERVICE、子查询等） | — |

**`bind` 示例：**

```java
new SelectWrapper()
    .prefixXsd()
    .select("?name", "?len")
    .triple("?s", "foaf:name", "?name")
    .bind("STRLEN(?name)", "?len");  // BIND(STRLEN(?name) AS ?len)
```

**`values` 示例：**

```java
new SelectWrapper()
    .prefixFoaf()
    .select("?s", "?name")
    .values("?type", "foaf:Person", "foaf:Agent")  // VALUES ?type { foaf:Person foaf:Agent }
    .triple("?s", "a", "?type")
    .triple("?s", "foaf:name", "?name");
```

**`pattern` 兜底示例（属性路径、联邦查询、子查询）：**

```java
// 属性路径
new SelectWrapper().pattern("?s foaf:knows+ ?friend .");
// 联邦查询
new SelectWrapper().pattern("SERVICE <http://other.endpoint/sparql> { ?s ?p ?o }");
// 子查询
new SelectWrapper().pattern("{ SELECT ?s (COUNT(?o) AS ?cnt) WHERE { ?s ?p ?o } GROUP BY ?s }");
```

#### FILTER 条件

| 方法 | 说明 | 生成示例 |
|------|------|----------|
| `filter(expr)` | 原始 FILTER 表达式 | `FILTER(?age > 18)` |
| `filterEq(var, val)` | 等于 | `FILTER(?x = val)` |
| `filterNe(var, val)` | 不等于 | `FILTER(?x != val)` |
| `filterGt(var, val)` | 大于 | `FILTER(?x > val)` |
| `filterGe(var, val)` | 大于等于 | `FILTER(?x >= val)` |
| `filterLt(var, val)` | 小于 | `FILTER(?x < val)` |
| `filterLe(var, val)` | 小于等于 | `FILTER(?x <= val)` |
| `filterEqLiteral(var, str)` | 等于字符串字面量（自动加引号） | `FILTER(?name = "Alice")` |
| `filterContains(var, sub)` | 字符串包含 | `FILTER(contains(?name, "Ali"))` |
| `filterRegex(var, regex, flags)` | 正则匹配 | `FILTER(regex(?name, "^A", "i"))` |
| `filterStrStarts(var, prefix)` | 前缀匹配 | `FILTER(strstarts(?name, "Ali"))` |
| `filterStrEnds(var, suffix)` | 后缀匹配 | `FILTER(strends(?email, ".com"))` |
| `filterIn(var, ...vals)` | IN 列表 | `FILTER(?x IN (v1, v2))` |
| `filterNotIn(var, ...vals)` | NOT IN 列表 | `FILTER(?x NOT IN (v1, v2))` |
| `filterBound(var)` | 变量有值 | `FILTER(bound(?age))` |
| `filterIsIri(var)` | 是 IRI | `FILTER(isIRI(?s))` |
| `filterIsLiteral(var)` | 是字面量 | `FILTER(isLiteral(?o))` |
| `filterIsBlank(var)` | 是空白节点 | `FILTER(isBlank(?s))` |
| `filterLang(var, lang)` | 语言标签 | `FILTER(lang(?name) = "zh")` |
| `filterDatatype(var, type)` | 数据类型 | `FILTER(datatype(?age) = xsd:integer)` |
| `filterExists(pattern)` | EXISTS 子图匹配 | `FILTER EXISTS { ... }` |
| `filterNotExists(pattern)` | NOT EXISTS 子图不匹配 | `FILTER NOT EXISTS { ... }` |

---

### SelectWrapper

用于构建 `SELECT` 查询，继承所有 `SparqlWrapper` 公共方法。

#### SELECT 投影

| 方法 | 说明 |
|------|------|
| `select(String... variables)` | 指定投影变量，如 `select("?name", "?age")` |
| `selectAll()` | 生成 `SELECT *` |
| `selectDistinct(String... variables)` | 生成 `SELECT DISTINCT ...` |
| `distinct()` | 单独开启 DISTINCT 标志 |
| `selectReduced(String... variables)` | 生成 `SELECT REDUCED ...`（允许部分去重，性能优于 DISTINCT） |
| `reduced()` | 单独开启 REDUCED 标志 |
| `aggregate(expr, asVar)` | 在投影中添加聚合表达式，如 `aggregate("COUNT(?s)", "?count")` → `(COUNT(?s) AS ?count)` |

**聚合查询示例：**

```java
new SelectWrapper()
    .prefixFoaf()
    .select("?type")
    .aggregate("COUNT(?s)", "?count")  // SELECT ?type (COUNT(?s) AS ?count)
    .triple("?s", "a", "?type")
    .groupBy("?type")
    .having("COUNT(?s) > 5");
```

#### ORDER BY

| 方法 | 说明 |
|------|------|
| `orderByAsc(variable)` | 升序，生成 `ORDER BY ASC(?var)` |
| `orderByDesc(variable)` | 降序，生成 `ORDER BY DESC(?var)` |
| `orderBy(expression)` | 原始排序表达式 |

#### LIMIT / OFFSET / 分页

| 方法 | 说明 |
|------|------|
| `limit(n)` | 生成 `LIMIT n` |
| `offset(n)` | 生成 `OFFSET n` |
| `page(page, pageSize)` | 分页快捷方法，等价于 `limit(pageSize).offset((page-1)*pageSize)` |

#### GROUP BY / HAVING

| 方法 | 说明 |
|------|------|
| `groupBy(String... variables)` | 生成 `GROUP BY ?v1 ?v2` |
| `having(expression)` | 生成 `HAVING(expr)` |

#### JenaFusekiTemplate 重载方法（SelectWrapper）

| 方法签名 | 说明 |
|----------|------|
| `select(SelectWrapper wrapper)` | 执行查询，返回 `List<Map<String, RDFNode>>`，使用默认数据集 |
| `select(SelectWrapper wrapper, String datasetName)` | 执行查询，指定数据集 |
| `select(SelectWrapper wrapper, RowMapper<T> rowMapper)` | 执行查询并映射，使用默认数据集 |
| `select(SelectWrapper wrapper, String datasetName, RowMapper<T> rowMapper)` | 执行查询并映射，指定数据集 |
| `selectOne(SelectWrapper wrapper)` | 返回第一行，无结果返回 `null` |
| `selectOne(SelectWrapper wrapper, RowMapper<T> rowMapper)` | 映射第一行，无结果返回 `null` |

---

### AskWrapper

用于构建 `ASK` 查询，继承所有 `SparqlWrapper` 公共方法，无需额外字段，直接链式添加 WHERE 条件后调用 `build()`。

```java
boolean exists = fusekiTemplate.ask(
    new AskWrapper()
        .prefixFoaf()
        .isA("?s", "foaf:Person")
        .triple("?s", "foaf:name", "?name")
        .filterEqLiteral("?name", "Alice")
);
// ASK {
//   ?s a foaf:Person .
//   ?s foaf:name ?name .
//   FILTER(?name = "Alice")
// }
```

#### JenaFusekiTemplate 重载方法（AskWrapper）

| 方法签名 | 说明 |
|----------|------|
| `ask(AskWrapper wrapper)` | 执行 ASK 查询，使用默认数据集 |
| `ask(AskWrapper wrapper, String datasetName)` | 执行 ASK 查询，指定数据集 |

---

### UpdateWrapper

用于构建各类 SPARQL UPDATE 语句，继承所有 `SparqlWrapper` 公共方法。必须先调用模式方法切换操作类型，再链式添加数据/条件。

#### 操作模式方法

| 方法 | 对应 SPARQL 语句 | 说明 |
|------|-----------------|------|
| `insertData()` | `INSERT DATA { ... }` | 插入固定三元组 |
| `deleteData()` | `DELETE DATA { ... }` | 删除固定三元组 |
| `deleteWhere()` | `DELETE WHERE { ... }` / `DELETE { } WHERE { }` | 按条件删除（含 FILTER 时自动扩展为完整形式） |
| `insertTemplate(String... tpls)` | `INSERT { tpl } WHERE { ... }` | 先查询后插入 |
| `deleteInsertTemplate(deleteTpl, insertTpl)` | `DELETE { } INSERT { } WHERE { ... }` | 先删后写（修改属性值） |
| `addDeleteTemplate(tpl)` | — | 追加 DELETE 模板 |
| `addInsertTemplate(tpl)` | — | 追加 INSERT 模板 |

**INSERT DATA 示例：**

```java
new UpdateWrapper()
    .prefixFoaf()
    .insertData()
    .triple("<http://example.org/Alice>", "foaf:name", "\"Alice\"")
    .triple("<http://example.org/Alice>", "foaf:age", "\"30\"^^xsd:integer");
// INSERT DATA {
//   <http://example.org/Alice> foaf:name "Alice" .
//   <http://example.org/Alice> foaf:age "30"^^xsd:integer .
// }
```

**DELETE WHERE（含 FILTER）示例：**

```java
new UpdateWrapper()
    .prefixFoaf()
    .deleteWhere()
    .triple("?s", "foaf:name", "?name")
    .filterEqLiteral("?name", "Alice");
// 自动扩展为：
// DELETE { ?s foaf:name ?name . }
// WHERE { ?s foaf:name ?name . FILTER(?name = "Alice") }
```

**DELETE + INSERT（修改属性值）示例：**

```java
new UpdateWrapper()
    .prefixFoaf()
    .deleteInsertTemplate(
        "?s foaf:age ?oldAge .",             // DELETE 模板
        "?s foaf:age \"31\"^^xsd:integer ."   // INSERT 模板
    )
    .triple("?s", "foaf:name", "\"Alice\"")
    .triple("?s", "foaf:age", "?oldAge");
```

#### 便捷数据写入方法

| 方法 | 说明 |
|------|------|
| `tripleLiteral(subjectUri, predicateUri, literal)` | 主语/谓语自动包裹 `<>`，宾语为字符串字面量（自动加引号转义） |
| `tripleUris(subjectUri, predicateUri, objectUri)` | 三者均为 URI，自动包裹 `<>` |

#### 图管理操作

| 方法 | 生成语句 | 说明 |
|------|---------|------|
| `clearDefault()` | `CLEAR DEFAULT` | 清空默认图所有三元组 |
| `clearAll()` | `CLEAR ALL` | 清空所有图所有三元组 |
| `clearGraph(uri)` | `CLEAR GRAPH <uri>` | 清空指定命名图 |
| `dropGraph(uri)` | `DROP GRAPH <uri>` | 删除命名图及其三元组 |
| `dropDefault()` | `DROP DEFAULT` | 删除默认图 |
| `dropAll()` | `DROP ALL` | 删除所有图 |
| `createGraph(uri)` | `CREATE GRAPH <uri>` | 创建命名图 |
| `load(fromUri)` | `LOAD <fromUri>` | 从 URL 加载 RDF 到默认图 |
| `loadIntoGraph(fromUri, intoUri)` | `LOAD <fromUri> INTO GRAPH <intoUri>` | 从 URL 加载 RDF 到命名图 |
| `add(srcUri, dstUri)` | `ADD GRAPH <src> TO GRAPH <dst>` | 复制源图三元组到目标图（保留源图），`null` 表示 DEFAULT |
| `copy(srcUri, dstUri)` | `COPY GRAPH <src> TO GRAPH <dst>` | 将源图内容复制到目标图（目标图先被清空） |
| `move(srcUri, dstUri)` | `MOVE GRAPH <src> TO GRAPH <dst>` | 将源图移动到目标图（源图被删除） |
| `silent()` | 在以上图操作前加 `SILENT` | 操作失败时不抛出异常 |

**图操作示例：**

```java
// 加载远程 RDF 数据到命名图
fusekiTemplate.update(
    new UpdateWrapper().loadIntoGraph("http://example.org/data.ttl", "http://example.org/g1")
);

// 复制图（目标不存在时静默不报错）
fusekiTemplate.update(
    new UpdateWrapper()
        .copy("http://example.org/src", "http://example.org/dst")
        .silent()
);

// DEFAULT 图移动到命名图
fusekiTemplate.update(
    new UpdateWrapper().move(null, "http://example.org/archive")
);
```

#### JenaFusekiTemplate 重载方法（UpdateWrapper）

| 方法签名 | 说明 |
|----------|------|
| `update(UpdateWrapper wrapper)` | 执行 UPDATE，使用默认数据集 |
| `update(UpdateWrapper wrapper, String datasetName)` | 执行 UPDATE，指定数据集 |

---

## 数据集管理客户端（FusekiDatasetClient）

`FusekiDatasetClient` 提供对 Fuseki 管理 API（`/$`）和 Graph Store Protocol（GSP）的底层访问能力，适合需要精细控制数据集与图的场景：

```java

@Autowired
private FusekiDatasetClient fusekiDatasetClient;
```

---

### 图管理（Graph Store Protocol）

#### `putModel(String datasetName, String graphUri, Model model)`

将 RDF Model 上传到指定数据集的图（PUT 覆盖语义，序列化为 Turtle 格式）。

```java
// 上传到命名图
fusekiDatasetClient.putModel("myDataset","http://example.org/graph1",model);
// 上传到默认图（graphUri 传 null）
fusekiDatasetClient.

putModel("myDataset",null,model);
```

**参数：**

- `datasetName` — 数据集名称
- `graphUri` — 命名图 URI，`null` 或空字符串表示默认图
- `model` — 要上传的 RDF Model

**返回：** `void`

---

#### `getModel(String datasetName, String graphUri)`

从指定数据集下载图的 RDF Model（GET 语义）。

```java
Model model = fusekiDatasetClient.getModel("myDataset", "http://example.org/graph1");
```

**参数：**

- `datasetName` — 数据集名称
- `graphUri` — 命名图 URI，`null` 或空字符串表示默认图

**返回：** `Model`

---

#### `deleteGraph(String datasetName, String graphUri)`

删除指定数据集中的图（DELETE 语义）。

```java
fusekiDatasetClient.deleteGraph("myDataset","http://example.org/graph1");
```

**参数：**

- `datasetName` — 数据集名称
- `graphUri` — 命名图 URI，`null` 或空字符串表示默认图

**返回：** `void`

---

### 数据集管理（Admin API）

#### `listDatasets()`

列举 Fuseki 服务器上所有数据集名称。

```java
List<String> datasets = fusekiDatasetClient.listDatasets();
// 返回示例：["/myDataset", "/otherDataset"]
```

**返回：** `List<String>`，数据集名称列表（含 `/` 前缀）

---

#### `datasetExists(String datasetName)`

判断指定数据集是否存在。

```java
boolean exists = fusekiDatasetClient.datasetExists("myDataset");
```

**参数：**

- `datasetName` — 数据集名称（有无 `/` 前缀均可）

**返回：** `boolean`

---

#### `createMemDataset(String datasetName)`

创建内存型数据集（`mem` 类型，服务重启后数据丢失）。

```java
fusekiDatasetClient.createMemDataset("myDataset");
```

**参数：**

- `datasetName` — 数据集名称

**返回：** `void`

---

#### `createTdb2Dataset(String datasetName)`

创建持久化 TDB2 数据集（数据持久保存在磁盘）。

```java
fusekiDatasetClient.createTdb2Dataset("myDataset");
```

**参数：**

- `datasetName` — 数据集名称

**返回：** `void`

---

#### `createDataset(String datasetName, String dbType)`

创建指定类型的数据集。

```java
fusekiDatasetClient.createDataset("myDataset","tdb2");
```

**参数：**

- `datasetName` — 数据集名称
- `dbType` — 数据集类型：`mem`（内存） / `tdb`（TDB1） / `tdb2`（TDB2 持久化）

**返回：** `void`

---

#### `deleteDataset(String datasetName)`

删除数据集，同时删除其中的所有数据。

```java
fusekiDatasetClient.deleteDataset("myDataset");
```

**参数：**

- `datasetName` — 数据集名称

**返回：** `void`

---

## 底层 SPARQL 客户端（SparqlClient）

`SparqlClient` 是 SPARQL 执行的底层封装，直接操作端点 URL。通常无需直接使用，推荐使用 `JenaFusekiTemplate`。

```java

@Autowired
private SparqlClient sparqlClient;
```

#### `executeSelect(String queryEndpoint, String sparql)`

执行 SPARQL SELECT 查询，返回深拷贝的 `ResultSet`（连接关闭后仍可安全使用）。

**参数：**

- `queryEndpoint` — SPARQL 查询端点，如 `http://localhost:3030/ds/sparql`
- `sparql` — SPARQL SELECT 语句

**返回：** `org.apache.jena.query.ResultSet`

---

#### `executeAsk(String queryEndpoint, String sparql)`

执行 SPARQL ASK 查询。

**参数：**

- `queryEndpoint` — SPARQL 查询端点
- `sparql` — SPARQL ASK 语句

**返回：** `boolean`

---

#### `executeConstruct(String queryEndpoint, String sparql)`

执行 SPARQL CONSTRUCT 查询，返回 RDF Model。

**参数：**

- `queryEndpoint` — SPARQL 查询端点
- `sparql` — SPARQL CONSTRUCT 语句

**返回：** `Model`

---

#### `executeDescribe(String queryEndpoint, String sparql)`

执行 SPARQL DESCRIBE 查询，返回 RDF Model。

**参数：**

- `queryEndpoint` — SPARQL 查询端点
- `sparql` — SPARQL DESCRIBE 语句

**返回：** `Model`

---

#### `executeUpdate(String updateEndpoint, String sparql)`

执行 SPARQL UPDATE。

**参数：**

- `updateEndpoint` — SPARQL 更新端点，如 `http://localhost:3030/ds/update`
- `sparql` — SPARQL UPDATE 语句

**返回：** `void`

---

## 行映射器（RowMapper）

`RowMapper<T>` 是函数式接口，用于将 SPARQL SELECT 的每行结果映射为业务对象，类比 Spring JDBC 的 `RowMapper`。

```java

@FunctionalInterface
public interface RowMapper<T> {
    T mapRow(QuerySolution row, int rowNum);
}
```

**用法示例：**

```java
// Lambda 写法
List<Person> persons = fusekiTemplate.select(sparql, (row, i) -> {
            Person p = new Person();
            p.setName(row.getLiteral("name").getString());
            p.setAge(row.getLiteral("age").getInt());
            p.setUri(row.getResource("person").getURI());
            return p;
        });
```

**`mapRow` 参数说明：**

| 参数       | 类型              | 说明                                                  |
|----------|-----------------|-----------------------------------------------------|
| `row`    | `QuerySolution` | 当前行，通过变量名获取 `RDFNode`（`getLiteral` / `getResource`） |
| `rowNum` | `int`           | 当前行号，从 0 开始                                         |

---

## 异常（FusekiException）

`FusekiException` 是 Starter 的统一运行时异常，封装了底层 HTTP 通信、SPARQL 解析、连接等所有异常。

```java
public class FusekiException extends RuntimeException {
    public FusekiException(String message) { ...}

    public FusekiException(String message, Throwable cause) { ...}

    public FusekiException(Throwable cause) { ...}
}
```

**典型触发场景：**

| 场景            | 异常消息示例                                   |
|---------------|------------------------------------------|
| HTTP 响应非 2xx  | `GSP PUT 上传 Model 失败, HTTP 403: ...`     |
| SPARQL 语法错误   | `SPARQL SELECT 执行失败: ...`                |
| 网络连接超时        | `GSP GET 请求失败: ...`                      |
| graphUri 参数为空 | `graphUri 不能为空，如需操作默认图请使用 uploadModel()` |
| 初始化文件不存在      | `RDF 数据文件不存在: classpath:rdf/xxx.ttl`     |

---

## 健康检查（FusekiHealthIndicator）

**依赖条件：** 引入 `spring-boot-starter-actuator` 时自动装配。

通过 Fuseki 的 `/$/ping` 端点检测服务可用性，结果展示在 `/actuator/health` 的 `fuseki` 节点：

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

**DOWN 时的响应示例：**

```json
{
  "fuseki": {
    "status": "DOWN",
    "details": {
      "serverUrl": "http://localhost:3030",
      "httpStatus": 503,
      "response": "Service Unavailable"
    }
  }
}
```

无需任何额外配置，引入 Actuator 依赖后即自动生效。

---

## 指标采集（FusekiMetricsInterceptor）

**依赖条件：** 引入 `micrometer-core` 时自动装配（也可通过 `spring-boot-starter-actuator` 间接引入）。

### 暴露的 Micrometer 指标

| 指标名                             | 类型      | 说明             |
|---------------------------------|---------|----------------|
| `fuseki.sparql.select.count`    | Counter | SELECT 执行次数    |
| `fuseki.sparql.select.duration` | Timer   | SELECT 执行耗时    |
| `fuseki.sparql.ask.count`       | Counter | ASK 执行次数       |
| `fuseki.sparql.construct.count` | Counter | CONSTRUCT 执行次数 |
| `fuseki.sparql.update.count`    | Counter | UPDATE 执行次数    |
| `fuseki.sparql.update.duration` | Timer   | UPDATE 执行耗时    |
| `fuseki.sparql.error.count`     | Counter | 执行异常次数         |

### 编程使用

```java

@Autowired
private FusekiMetricsInterceptor metricsInterceptor;

// 包装 SELECT 操作，自动计数 + 计时
List<T> results = metricsInterceptor.recordSelect(() ->
        fusekiTemplate.select(sparql, rowMapper)
);

// 包装 ASK 操作，自动计数
boolean exists = metricsInterceptor.recordAsk(() ->
        fusekiTemplate.ask(sparql)
);

// 包装 CONSTRUCT 操作，自动计数
Model model = metricsInterceptor.recordConstruct(() ->
        fusekiTemplate.construct(sparql)
);

// 包装 UPDATE 操作，自动计数 + 计时
metricsInterceptor.

recordUpdate(() ->
        fusekiTemplate.

update(sparql)
);

// 获取底层 MeterRegistry，用于自定义指标扩展
MeterRegistry registry = metricsInterceptor.getMeterRegistry();
```

### 可用的包装方法

| 方法签名                                             | 说明                   |
|--------------------------------------------------|----------------------|
| `<T> T recordSelect(Supplier<T> operation)`      | 包装 SELECT 操作，计数 + 计时 |
| `boolean recordAsk(Supplier<Boolean> operation)` | 包装 ASK 操作，计数         |
| `<T> T recordConstruct(Supplier<T> operation)`   | 包装 CONSTRUCT 操作，计数   |
| `void recordUpdate(Runnable operation)`          | 包装 UPDATE 操作，计数 + 计时 |
| `MeterRegistry getMeterRegistry()`               | 获取底层 MeterRegistry   |

