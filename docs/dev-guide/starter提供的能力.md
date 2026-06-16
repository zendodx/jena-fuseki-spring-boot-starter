---
layout: default
title: starter提供能力
parent: 开发指南
nav_order: 3
---

## 本 Starter 提供的能力

---

### 能力全景图

```
jena-fuseki-spring-boot-starter
│
├── 1. 连接管理          自动配置连接池、认证、超时
├── 2. SPARQL 操作       SELECT / ASK / CONSTRUCT / UPDATE
├── 3. RDF 图管理        上传/下载/清空整图、命名图操作
├── 4. 数据集管理        创建/删除/列举数据集
├── 5. 初始化支持        启动时自动加载初始 RDF 数据
├── 6. 测试支持          内嵌 Fuseki，单测零依赖
└── 7. 运维监控          健康检查、Metrics 指标
```

---

### 一、连接管理

| 能力            | 说明                                  |
|---------------|-------------------------------------|
| 自动创建连接池       | 基于 `HttpClient` 连接池，复用连接，避免每次重建     |
| Basic Auth 认证 | 配置账号密码，自动加入请求头                      |
| 超时控制          | 连接超时 / 读取超时 / 写入超时分别配置              |
| 多数据集支持        | 配置多个 dataset，通过 `@Qualifier` 注入不同实例 |
| 优雅关闭          | Spring 容器关闭时自动释放连接资源                |

```yaml
jena:
  fuseki:
    server-url: http://localhost:3030
    dataset: myDataset
    username: admin
    password: secret
    connect-timeout: 5000
    read-timeout: 30000
```

---

### 二、SPARQL 操作（核心）

```java
// SELECT —— 返回结果集
List<Map<String, RDFNode>> results = template.select("""
                    SELECT ?name ?age WHERE { ?p foaf:name ?name ; foaf:age ?age }
                """);

// SELECT + 对象映射
List<Person> persons = template.select(sparql, row -> {
    Person p = new Person();
    p.setName(row.get("name").toString());
    return p;
});

// ASK —— 判断是否存在
boolean exists = template.ask("""
            ASK { <http://example.org/Bob> foaf:name "Bob" }
        """);

// CONSTRUCT —— 构建子图
Model subGraph = template.construct("""
            CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }
        """);

// INSERT —— 写入数据
template.

update("""
    INSERT DATA {
      <http://example.org/Alice> foaf:name "Alice" ; foaf:age 30
    }
""");

// DELETE —— 删除数据
template.

update("""
    DELETE DATA {
      <http://example.org/Alice> foaf:age 30
    }
""");
```

---

### 三、RDF 图管理

```java
// 上传整个 Model 到默认图
template.uploadModel(model);

// 上传到命名图
template.

uploadNamedGraph("http://example.org/graph1",model);

// 下载整个图
Model graph = template.downloadGraph("http://example.org/graph1");

// 清空命名图
template.

clearGraph("http://example.org/graph1");

// 删除命名图
template.

dropNamedGraph("http://example.org/graph1");

// 便捷三元组操作
template.

insertTriple(subject, predicate, object);
template.

deleteTriple(subject, predicate, object);
```

---

### 四、数据集管理

```java
// 列举所有数据集
List<String> datasets = datasetClient.listDatasets();

// 创建数据集（持久化 / 内存型）
datasetClient.

createDataset("newDataset",DatasetType.TDB2);

// 删除数据集
datasetClient.

deleteDataset("oldDataset");

// 判断是否存在
boolean exists = datasetClient.datasetExists("myDataset");
```

---

### 五、启动初始化

类比 `spring.sql.init`，支持启动时自动加载 RDF 数据：

```yaml
jena:
  fuseki:
    init:
      enabled: true
      # 支持 classpath、file://、http:// 路径
      data-locations:
        - classpath:rdf/base-ontology.ttl
        - classpath:rdf/initial-data.n3
      graph-uri: http://example.org/base   # 加载到哪个命名图
      mode: always   # always | if-empty（仅数据集为空时加载）
```

---

### 六、测试支持

引入 test 包后，单测无需真实 Fuseki 服务器：

```java

@SpringBootTest
@AutoConfigureTestFuseki   // 自动启动内嵌 Fuseki，测完自动销毁
class KnowledgeServiceTest {

    @Autowired
    JenaFusekiTemplate template;

    @Test
    void testInsertAndQuery() {
        template.update("INSERT DATA { <http://a> <http://b> <http://c> }");
        boolean exists = template.ask("ASK { <http://a> <http://b> <http://c> }");
        assertTrue(exists);
    }
}
```

---

### 七、运维监控

**健康检查**（Actuator）：

```json
// GET /actuator/health
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

**Metrics 指标**（Micrometer）：

```
fuseki.query.select.count       # SELECT 执行次数
fuseki.query.select.duration    # SELECT 耗时分布
fuseki.update.count             # UPDATE 执行次数
fuseki.connection.pool.active   # 活跃连接数
```

---

### 能力优先级建议

```
P0 必须做（MVP）:  连接管理 + SPARQL 操作
P1 重要:          图管理 + 健康检查
P2 锦上添花:       数据集管理 + 初始化 + Metrics
P3 可选扩展:       测试支持模块（单独 test-autoconfigure jar）
```