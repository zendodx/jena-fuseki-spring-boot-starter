---
layout: default
title: 快速开始
parent: 开发指南
nav_order: 0
---

# 快速开始

## 一、版本依赖

### 环境要求

| 环境                        | 要求                            |
|---------------------------|-------------------------------|
| JDK                       | **11+**                       |
| Spring Boot               | **2.7.x**                     |
| Apache Jena Fuseki Server | **4.x**（独立部署，本 starter 只做客户端） |

### 引入依赖

在业务项目的 `pom.xml` 中添加：

```xml

<dependency>
    <groupId>io.github.jena-fuseki</groupId>
    <artifactId>jena-fuseki-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

**可选依赖**（按需引入）：

```xml
<!-- 启用 /actuator/health fuseki 健康检查节点 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

        <!-- 启用 fuseki.sparql.* Micrometer 指标 -->
<dependency>
<groupId>io.micrometer</groupId>
<artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

> 本 starter 已将 Actuator 和 Micrometer 声明为 `optional`，不引入不影响正常使用。

---

## 二、接入方法

### 第一步：启动 Fuseki 服务器

本 starter **不内嵌** Fuseki 服务器，需要独立部署。推荐使用 Docker 快速启动：

```yaml
# docker-compose.yml
services:
  fuseki:
    image: stain/jena-fuseki:4.10.0
    ports:
      - "3030:3030"
    environment:
      - ADMIN_PASSWORD=admin123
      - FUSEKI_DATASET_1=myDataset
    volumes:
      - fuseki-data:/fuseki/databases
volumes:
  fuseki-data:
```

```bash
docker-compose up -d
# 访问 http://localhost:3030 验证 Fuseki 控制台是否正常
```

### 第二步：配置连接信息

在 `application.yml` 中添加：

```yaml
jena:
  fuseki:
    server-url: http://localhost:3030   # Fuseki 服务器地址
    dataset: myDataset                  # 默认操作的数据集名称
    username: admin                     # Basic Auth 用户名（未开启认证可不填）
    password: admin123                  # Basic Auth 密码
```

### 第三步：注入使用

引入依赖 + 完成配置后，**无需任何额外注解**，直接注入 `JenaFusekiTemplate` 即可：

```java

@Service
public class KnowledgeGraphService {

    @Autowired
    private JenaFusekiTemplate fusekiTemplate;

    // ... 业务方法见下一节
}
```

---

## 三、使用方法

### 3.1 SPARQL SELECT 查询

#### 返回原始 Map 列表

```java
String sparql = """
        PREFIX foaf: <http://xmlns.com/foaf/0.1/>
        SELECT ?name ?age
        WHERE { ?person foaf:name ?name ; foaf:age ?age }
        ORDER BY ?name
        """;

List<Map<String, RDFNode>> rows = fusekiTemplate.select(sparql);

for(
Map<String, RDFNode> row :rows){
String name = row.get("name").asLiteral().getString();
int age = row.get("age").asLiteral().getInt();
    System.out.

println(name +" / "+age);
}
```

#### 通过 RowMapper 映射为业务对象

```java
String sparql = """
        PREFIX foaf: <http://xmlns.com/foaf/0.1/>
        SELECT ?uri ?name ?age
        WHERE { ?uri foaf:name ?name ; foaf:age ?age }
        """;

List<Person> persons = fusekiTemplate.select(sparql, (row, i) -> {
    Person p = new Person();
    p.setUri(row.getResource("uri").getURI());
    p.setName(row.getLiteral("name").getString());
    p.setAge(row.getLiteral("age").getInt());
    return p;
});
```

#### 查询单条结果

```java
// 返回 Map（无结果返回 null）
Map<String, RDFNode> one = fusekiTemplate.selectOne(
                "SELECT ?name WHERE { <http://example.org/Alice> foaf:name ?name }"
        );

// 映射为对象（无结果返回 null）
String name = fusekiTemplate.selectOne(
        "PREFIX foaf: <http://xmlns.com/foaf/0.1/> SELECT ?name WHERE { <http://example.org/Alice> foaf:name ?name }",
        (row, i) -> row.getLiteral("name").getString()
);
```

#### 指定数据集查询

```java
// 第二个参数传入数据集名称，覆盖默认配置
List<Map<String, RDFNode>> rows = fusekiTemplate.select(sparql, "anotherDataset");
```

---

### 3.2 SPARQL ASK 查询

```java
// 判断某个节点是否存在
boolean exists = fusekiTemplate.ask(
                "ASK { <http://example.org/Alice> ?p ?o }"
        );

// 判断某条具体关系是否存在
boolean isFriend = fusekiTemplate.ask("""
        PREFIX rel: <http://example.org/rel/>
        ASK { <http://example.org/Alice> rel:knows <http://example.org/Bob> }
        """);
```

---

### 3.3 SPARQL CONSTRUCT / DESCRIBE

```java
// CONSTRUCT：构建子图
Model subGraph = fusekiTemplate.construct("""
                PREFIX foaf: <http://xmlns.com/foaf/0.1/>
                CONSTRUCT { ?s foaf:name ?name ; foaf:age ?age }
                WHERE     { ?s foaf:name ?name ; foaf:age ?age }
                """);

// DESCRIBE：描述某个节点
Model desc = fusekiTemplate.describe(
        "DESCRIBE <http://example.org/Alice>"
);

// 遍历 Model 中的三元组
subGraph.

listStatements().

forEachRemaining(stmt ->
        System.out.

println(stmt.getSubject() +" "+stmt.

getPredicate() +" "+stmt.

getObject())
        );
```

---

### 3.4 SPARQL UPDATE（写入 / 删除）

#### INSERT DATA

```java
fusekiTemplate.update("""
        PREFIX foaf: <http://xmlns.com/foaf/0.1/>
        INSERT DATA {
          <http://example.org/Alice> foaf:name "Alice" ;
                                     foaf:age  30 .
        }
        """);
```

#### DELETE DATA

```java
fusekiTemplate.update("""
        PREFIX foaf: <http://xmlns.com/foaf/0.1/>
        DELETE DATA {
          <http://example.org/Alice> foaf:age 30
        }
        """);
```

#### DELETE WHERE（条件删除）

```java
// 删除年龄大于 60 的所有节点的年龄属性
fusekiTemplate.update("""
        PREFIX foaf: <http://xmlns.com/foaf/0.1/>
        DELETE { ?s foaf:age ?age }
        WHERE  { ?s foaf:age ?age . FILTER(?age > 60) }
        """);
```

#### INSERT / DELETE 组合（原子更新）

```java
// 修改 Alice 的年龄：先删后插（原子操作）
fusekiTemplate.update("""
        PREFIX foaf: <http://xmlns.com/foaf/0.1/>
        DELETE { <http://example.org/Alice> foaf:age ?oldAge }
        INSERT { <http://example.org/Alice> foaf:age 31 }
        WHERE  { <http://example.org/Alice> foaf:age ?oldAge }
        """);
```

---

### 3.5 便捷三元组操作

不想写 SPARQL 时，可使用简化方法：

```java
// 插入三元组（字面量宾语）
fusekiTemplate.insertTriple(
    "http://example.org/Bob",            // 主语
            "http://xmlns.com/foaf/0.1/name",    // 谓语
            "Bob"                                // 宾语（字符串字面量）
);

// 插入三元组（URI 宾语）
fusekiTemplate.

insertTripleUri(
    "http://example.org/Bob",
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#type",
            "http://xmlns.com/foaf/0.1/Person"
);

// 删除单条三元组（字面量宾语）
fusekiTemplate.

deleteTriple(
    "http://example.org/Bob",
            "http://xmlns.com/foaf/0.1/name",
            "Bob"
);

// 删除某主语的全部三元组（相当于删除整个节点）
fusekiTemplate.

deleteSubject("http://example.org/Bob");
```

---

### 3.6 图（Graph）管理

#### 上传 / 下载整个 RDF Model

```java
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDFS;

// 构建一个 Model
Model model = ModelFactory.createDefaultModel();
model.

        add(
                ResourceFactory.createResource("http://example.org/MyThing"),

        RDFS.label,
        ResourceFactory.

        createStringLiteral("My Thing Label")
);

// 上传到默认图（覆盖）
        fusekiTemplate.

        uploadModel(model);

        // 下载默认图
        Model downloaded = fusekiTemplate.downloadDefaultGraph();
System.out.

        println("三元组数量: "+downloaded.size());
```

#### 命名图操作

```java
String graphUri = "http://example.org/graphs/ontology";

// 上传到命名图
fusekiTemplate.

uploadNamedGraph(graphUri, model);

// 下载命名图
Model namedGraph = fusekiTemplate.downloadNamedGraph(graphUri);

// 清空命名图（保留图结构，删除三元组）
fusekiTemplate.

clearGraph(graphUri);

// 删除命名图（同时删除图结构和三元组）
fusekiTemplate.

dropNamedGraph(graphUri);
```

#### 清空操作

```java
// 清空默认图
fusekiTemplate.clearDefaultGraph();

// 清空整个数据集（所有图）
fusekiTemplate.

clearAll();
```

---

### 3.7 完整配置项参考

```yaml
jena:
  fuseki:
    enabled: true                        # 是否启用，默认 true
    server-url: http://localhost:3030    # Fuseki 服务器地址
    dataset: myDataset                   # 默认数据集名称
    username: admin                      # Basic Auth 用户名（无认证可不填）
    password: admin123                   # Basic Auth 密码
    connect-timeout: 5000                # 连接超时，单位毫秒，默认 5000
    read-timeout: 30000                  # 读取超时，单位毫秒，默认 30000
    max-connections: 20                  # 连接池最大连接数，默认 20

    # P2 可选：启动时自动加载 RDF 数据文件
    init:
      enabled: false                     # 默认关闭
      data-locations:
        - classpath:rdf/ontology.ttl     # 支持 classpath: / file: / http:
        - classpath:rdf/data.n3
      graph-uri: http://example.org/base # 加载到哪个命名图，不填则加载到默认图
      mode: if-empty                     # always（每次启动覆盖）| if-empty（仅空数据集时加载）
```

---

### 3.8 健康检查（需引入 Actuator）

引入 `spring-boot-starter-actuator` 后，访问 `/actuator/health` 自动包含 Fuseki 节点：

```json
{
  "status": "UP",
  "components": {
    "fuseki": {
      "status": "UP",
      "details": {
        "serverUrl": "http://localhost:3030",
        "dataset": "myDataset",
        "pingMs": 8
      }
    }
  }
}
```

Fuseki 不可达时 `status` 变为 `DOWN`，可配合 K8s readinessProbe 使用。

---

## 四、常见问题

**Q：连接 Fuseki 报 401 Unauthorized？**

> 检查 `jena.fuseki.username` 和 `jena.fuseki.password` 是否与 Fuseki 服务器配置一致。未开启认证的 Fuseki 不需要填写这两项。

**Q：查询超时报错？**

> 调大 `jena.fuseki.read-timeout`（默认 30000ms）。对于复杂推理查询，可设置为 120000（2 分钟）。

**Q：想操作多个数据集？**

> 所有查询方法都有带 `datasetName` 参数的重载版本，例如 `fusekiTemplate.select(sparql, "dataset2")`，不受默认 `dataset`
> 配置限制。

**Q：想自定义 HttpClient（如配置代理、mTLS）？**

> 由于所有 Bean 均加了 `@ConditionalOnMissingBean`，只需在业务项目中自定义一个 `HttpClient` Bean 即可覆盖默认配置：
> ```java
> @Bean
> public HttpClient fusekiHttpClient() {
>     return HttpClient.newBuilder()
>         .proxy(ProxySelector.of(new InetSocketAddress("proxy.corp", 8080)))
>         .connectTimeout(Duration.ofSeconds(10))
>         .build();
> }
> ```

