package io.github.jenafuseki.starter.core.wrapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SPARQL 构建器抽象基类
 *
 * <p>提供所有 Wrapper 共用的 PREFIX 声明与 WHERE 子句的构建逻辑，
 * 子类继承并实现各自特定的构建方式（SELECT / ASK / INSERT / DELETE 等）。</p>
 *
 * <p>类比 MyBatis-Plus 的 {@code AbstractWrapper}。</p>
 *
 * @param <W> 子类类型，用于链式调用返回 this
 */
@SuppressWarnings("unchecked")
public abstract class SparqlWrapper<W extends SparqlWrapper<W>> {

    /** PREFIX 声明：前缀别名 -> 完整 URI */
    protected final Map<String, String> prefixes = new LinkedHashMap<>();

    /** WHERE 子句中的三元组模式列表（最终用空格拼接） */
    protected final List<String> patterns = new ArrayList<>();

    /** FILTER 表达式列表 */
    protected final List<String> filters = new ArrayList<>();

    // =====================================================================
    // 常用前缀快捷方法
    // =====================================================================

    /**
     * 声明 RDF 标准前缀（rdf:）
     * <pre>{@code PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>}</pre>
     */
    public W prefixRdf() {
        return prefix("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
    }

    /**
     * 声明 RDFS 前缀（rdfs:）
     * <pre>{@code PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>}</pre>
     */
    public W prefixRdfs() {
        return prefix("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
    }

    /**
     * 声明 OWL 前缀（owl:）
     * <pre>{@code PREFIX owl: <http://www.w3.org/2002/07/owl#>}</pre>
     */
    public W prefixOwl() {
        return prefix("owl", "http://www.w3.org/2002/07/owl#");
    }

    /**
     * 声明 XSD 前缀（xsd:）
     * <pre>{@code PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>}</pre>
     */
    public W prefixXsd() {
        return prefix("xsd", "http://www.w3.org/2001/XMLSchema#");
    }

    /**
     * 声明 FOAF 前缀（foaf:）
     * <pre>{@code PREFIX foaf: <http://xmlns.com/foaf/0.1/>}</pre>
     */
    public W prefixFoaf() {
        return prefix("foaf", "http://xmlns.com/foaf/0.1/");
    }

    /**
     * 声明自定义前缀
     *
     * @param alias 前缀别名（不含冒号），如 {@code "ex"}
     * @param uri   对应的完整命名空间 URI，如 {@code "http://example.org/"}
     */
    public W prefix(String alias, String uri) {
        prefixes.put(alias, uri);
        return (W) this;
    }

    // =====================================================================
    // WHERE 子句 - 三元组模式
    // =====================================================================

    /**
     * 添加三元组模式，主语/谓语/宾语使用已声明的前缀缩写形式
     *
     * <p>示例：{@code .triple("?s", "foaf:name", "?name")}
     * 生成 {@code ?s foaf:name ?name .}</p>
     *
     * @param subject   主语，如 {@code "?s"} 或 {@code "<http://...>"} 或 {@code "ex:Alice"}
     * @param predicate 谓语，如 {@code "foaf:name"} 或 {@code "?p"}
     * @param object    宾语，如 {@code "?name"} 或字面量字符串（需自行加引号，如 {@code "\"Alice\""}）
     */
    public W triple(String subject, String predicate, String object) {
        patterns.add(subject + " " + predicate + " " + object + " .");
        return (W) this;
    }

    /**
     * 添加三元组模式，主语为全 URI（自动包裹尖括号）
     *
     * <p>示例：{@code .tripleUri("http://example.org/Alice", "foaf:name", "?name")}
     * 生成 {@code <http://example.org/Alice> foaf:name ?name .}</p>
     */
    public W tripleUri(String subjectUri, String predicate, String object) {
        return triple("<" + subjectUri + ">", predicate, object);
    }

    /**
     * 添加类型三元组（rdf:type / a）
     *
     * <p>示例：{@code .isA("?s", "foaf:Person")} 生成 {@code ?s a foaf:Person .}</p>
     *
     * @param subject 主语
     * @param type    类型，如 {@code "foaf:Person"} 或 {@code "<http://...>"}
     */
    public W isA(String subject, String type) {
        patterns.add(subject + " a " + type + " .");
        return (W) this;
    }

    /**
     * 添加 OPTIONAL 可选块
     *
     * <p>示例：{@code .optional("?s foaf:age ?age .")} 生成 {@code OPTIONAL { ?s foaf:age ?age . }}</p>
     */
    public W optional(String pattern) {
        patterns.add("OPTIONAL { " + pattern + " }");
        return (W) this;
    }

    /**
     * 添加 UNION 联合块
     *
     * <p>示例：{@code .union("{ ?s foaf:name ?n }", "{ ?s rdfs:label ?n }")}
     * 生成 {@code { ?s foaf:name ?n } UNION { ?s rdfs:label ?n }}</p>
     */
    public W union(String left, String right) {
        patterns.add(left + " UNION " + right);
        return (W) this;
    }

    /**
     * 添加 GRAPH 命名图块
     *
     * <p>示例：{@code .graph("http://example.org/g1", "?s ?p ?o .")}
     * 生成 {@code GRAPH <http://example.org/g1> { ?s ?p ?o . }}</p>
     *
     * @param graphUri 命名图 URI（不含尖括号）
     * @param pattern  图内的三元组模式
     */
    public W graph(String graphUri, String pattern) {
        patterns.add("GRAPH <" + graphUri + "> { " + pattern + " }");
        return (W) this;
    }

    /**
     * 添加 MINUS 差集块
     *
     * <p>示例：{@code .minus("?s foaf:age ?age .")} 生成 {@code MINUS { ?s foaf:age ?age . }}</p>
     *
     * @param pattern MINUS 块内的三元组模式
     */
    public W minus(String pattern) {
        patterns.add("MINUS { " + pattern + " }");
        return (W) this;
    }

    /**
     * 添加 BIND 变量绑定
     *
     * <p>示例：{@code .bind("STRLEN(?name)", "?len")} 生成 {@code BIND(STRLEN(?name) AS ?len)}</p>
     *
     * @param expression 计算表达式，如 {@code "STRLEN(?name)"} 或 {@code "?price * 0.9"}
     * @param asVar      绑定到的变量名，如 {@code "?len"}
     */
    public W bind(String expression, String asVar) {
        patterns.add("BIND(" + expression + " AS " + asVar + ")");
        return (W) this;
    }

    /**
     * 添加单变量 VALUES 内联数据
     *
     * <p>示例：{@code .values("?type", "foaf:Person", "foaf:Agent")}
     * 生成 {@code VALUES ?type { foaf:Person foaf:Agent }}</p>
     *
     * @param variable 变量名，如 {@code "?type"}
     * @param values   值列表（已格式化），如 {@code "foaf:Person"} 或 {@code "\"Alice\""} 或 {@code "<http://...>"}
     */
    public W values(String variable, String... values) {
        StringBuilder sb = new StringBuilder("VALUES ").append(variable).append(" { ");
        for (String v : values) {
            sb.append(v).append(" ");
        }
        sb.append("}");
        patterns.add(sb.toString());
        return (W) this;
    }

    /**
     * 添加多变量 VALUES 内联数据表
     *
     * <p>示例：
     * <pre>{@code
     * .valuesMulti(
     *     new String[]{"?name", "?age"},
     *     new String[][]{  {"\"Alice\"", "30"},  {"\"Bob\"", "25"}  }
     * )
     * // VALUES (?name ?age) { ("Alice" 30) ("Bob" 25) }
     * }</pre>
     *
     * @param variables 变量名数组
     * @param rows      每行的值数组
     */
    public W valuesMulti(String[] variables, String[][] rows) {
        StringBuilder sb = new StringBuilder("VALUES (");
        sb.append(String.join(" ", variables)).append(") {\n");
        for (String[] row : rows) {
            sb.append("    (");
            for (int i = 0; i < row.length; i++) {
                if (i > 0) sb.append(" ");
                sb.append(row[i]);
            }
            sb.append(")\n");
        }
        sb.append("  }");
        patterns.add(sb.toString());
        return (W) this;
    }

    /**
     * 追加原始 WHERE 模式字符串（兜底方法，支持复杂手写片段）
     *
     * <p>可用于属性路径、SERVICE 联邦查询、子查询等 Wrapper 尚不支持的语法：
     * <pre>{@code
     * .pattern("?s foaf:knows+ ?friend .")                    // 属性路径
     * .pattern("SERVICE <http://other.endpoint/sparql> { ... }") // 联邦查询
     * .pattern("{ SELECT ?s (COUNT(?o) AS ?cnt) WHERE { ?s ?p ?o } GROUP BY ?s }") // 子查询
     * }</pre>
     *
     * @param rawPattern 原始 SPARQL 片段
     */
    public W pattern(String rawPattern) {
        patterns.add(rawPattern);
        return (W) this;
    }

    // =====================================================================
    // FILTER 条件
    // =====================================================================

    /**
     * 添加 FILTER 表达式
     *
     * <p>示例：{@code .filter("?age > 18")} 生成 {@code FILTER(?age > 18)}</p>
     */
    public W filter(String expression) {
        filters.add("FILTER(" + expression + ")");
        return (W) this;
    }

    /**
     * 字符串包含过滤（FILTER contains）
     *
     * <p>示例：{@code .filterContains("?name", "Alice")} 生成 {@code FILTER(contains(?name, "Alice"))}</p>
     *
     * @param variable  变量名，如 {@code "?name"}
     * @param substring 子串
     */
    public W filterContains(String variable, String substring) {
        filters.add("FILTER(contains(" + variable + ", \"" + escapeLiteral(substring) + "\"))");
        return (W) this;
    }

    /**
     * 字符串正则过滤（FILTER regex）
     *
     * <p>示例：{@code .filterRegex("?name", "^Alice", "i")} 生成
     * {@code FILTER(regex(?name, "^Alice", "i"))}</p>
     *
     * @param variable 变量名，如 {@code "?name"}
     * @param regex    正则表达式
     * @param flags    修饰符，如 {@code "i"}（忽略大小写），传 {@code null} 或空字符串则不添加
     */
    public W filterRegex(String variable, String regex, String flags) {
        if (flags == null || flags.isEmpty()) {
            filters.add("FILTER(regex(" + variable + ", \"" + escapeLiteral(regex) + "\"))");
        } else {
            filters.add("FILTER(regex(" + variable + ", \"" + escapeLiteral(regex) + "\", \"" + flags + "\"))");
        }
        return (W) this;
    }

    /**
     * 大于比较过滤
     *
     * <p>示例：{@code .filterGt("?age", "18")} 生成 {@code FILTER(?age > 18)}</p>
     *
     * @param variable 变量名，如 {@code "?age"}
     * @param value    比较值，如 {@code "18"} 或 {@code "\"2024-01-01\"^^xsd:date"}
     */
    public W filterGt(String variable, String value) {
        filters.add("FILTER(" + variable + " > " + value + ")");
        return (W) this;
    }

    /**
     * 大于等于比较过滤
     */
    public W filterGe(String variable, String value) {
        filters.add("FILTER(" + variable + " >= " + value + ")");
        return (W) this;
    }

    /**
     * 小于比较过滤
     */
    public W filterLt(String variable, String value) {
        filters.add("FILTER(" + variable + " < " + value + ")");
        return (W) this;
    }

    /**
     * 小于等于比较过滤
     */
    public W filterLe(String variable, String value) {
        filters.add("FILTER(" + variable + " <= " + value + ")");
        return (W) this;
    }

    /**
     * 等于比较过滤
     *
     * <p>示例：{@code .filterEq("?name", "\"Alice\"")} 生成 {@code FILTER(?name = "Alice")}</p>
     */
    public W filterEq(String variable, String value) {
        filters.add("FILTER(" + variable + " = " + value + ")");
        return (W) this;
    }

    /**
     * 不等于比较过滤
     */
    public W filterNe(String variable, String value) {
        filters.add("FILTER(" + variable + " != " + value + ")");
        return (W) this;
    }

    /**
     * 字符串字面量等于过滤（自动加引号）
     *
     * <p>示例：{@code .filterEqLiteral("?name", "Alice")} 生成 {@code FILTER(?name = "Alice")}</p>
     */
    public W filterEqLiteral(String variable, String literal) {
        filters.add("FILTER(" + variable + " = \"" + escapeLiteral(literal) + "\")");
        return (W) this;
    }

    /**
     * BOUND 过滤：变量有值
     *
     * <p>示例：{@code .filterBound("?age")} 生成 {@code FILTER(bound(?age))}</p>
     */
    public W filterBound(String variable) {
        filters.add("FILTER(bound(" + variable + "))");
        return (W) this;
    }

    /**
     * isIRI 过滤：变量为 IRI 节点
     *
     * <p>示例：{@code .filterIsIri("?s")} 生成 {@code FILTER(isIRI(?s))}</p>
     */
    public W filterIsIri(String variable) {
        filters.add("FILTER(isIRI(" + variable + "))");
        return (W) this;
    }

    /**
     * isLiteral 过滤：变量为字面量
     */
    public W filterIsLiteral(String variable) {
        filters.add("FILTER(isLiteral(" + variable + "))");
        return (W) this;
    }

    /**
     * isBlank 过滤：变量为空白节点
     *
     * <p>示例：{@code .filterIsBlank("?s")} 生成 {@code FILTER(isBlank(?s))}</p>
     */
    public W filterIsBlank(String variable) {
        filters.add("FILTER(isBlank(" + variable + "))");
        return (W) this;
    }

    /**
     * NOT EXISTS 过滤：图模式不存在时匹配
     *
     * <p>示例：{@code .filterNotExists("?s foaf:age ?age .")} 生成
     * {@code FILTER NOT EXISTS { ?s foaf:age ?age . }}</p>
     *
     * @param pattern 图模式字符串
     */
    public W filterNotExists(String pattern) {
        filters.add("FILTER NOT EXISTS { " + pattern + " }");
        return (W) this;
    }

    /**
     * EXISTS 过滤：图模式存在时匹配
     *
     * <p>示例：{@code .filterExists("?s foaf:age ?age .")} 生成
     * {@code FILTER EXISTS { ?s foaf:age ?age . }}</p>
     *
     * @param pattern 图模式字符串
     */
    public W filterExists(String pattern) {
        filters.add("FILTER EXISTS { " + pattern + " }");
        return (W) this;
    }

    /**
     * 语言标签过滤
     *
     * <p>示例：{@code .filterLang("?name", "zh")} 生成 {@code FILTER(lang(?name) = "zh")}</p>
     */
    public W filterLang(String variable, String lang) {
        filters.add("FILTER(lang(" + variable + ") = \"" + lang + "\")");
        return (W) this;
    }

    /**
     * datatype 过滤：变量的数据类型与指定类型匹配
     *
     * <p>示例：{@code .filterDatatype("?age", "xsd:integer")} 生成
     * {@code FILTER(datatype(?age) = xsd:integer)}</p>
     *
     * @param variable 变量名
     * @param datatype 数据类型，如 {@code "xsd:integer"} 或 {@code "<http://www.w3.org/2001/XMLSchema#integer>"}
     */
    public W filterDatatype(String variable, String datatype) {
        filters.add("FILTER(datatype(" + variable + ") = " + datatype + ")");
        return (W) this;
    }

    /**
     * IN 过滤：变量值在给定列表中
     *
     * <p>示例：{@code .filterIn("?status", "\"active\"", "\"pending\"")} 生成
     * {@code FILTER(?status IN ("active", "pending"))}</p>
     *
     * @param variable 变量名
     * @param values   值列表（已格式化的 SPARQL 字面量或 URI）
     */
    public W filterIn(String variable, String... values) {
        filters.add("FILTER(" + variable + " IN (" + String.join(", ", values) + "))");
        return (W) this;
    }

    /**
     * NOT IN 过滤：变量值不在给定列表中
     *
     * <p>示例：{@code .filterNotIn("?status", "\"deleted\"", "\"banned\"")} 生成
     * {@code FILTER(?status NOT IN ("deleted", "banned"))}</p>
     */
    public W filterNotIn(String variable, String... values) {
        filters.add("FILTER(" + variable + " NOT IN (" + String.join(", ", values) + "))");
        return (W) this;
    }

    /**
     * strstarts 过滤：字符串以指定前缀开头
     *
     * <p>示例：{@code .filterStrStarts("?name", "Ali")} 生成
     * {@code FILTER(strstarts(?name, "Ali"))}</p>
     */
    public W filterStrStarts(String variable, String prefix) {
        filters.add("FILTER(strstarts(" + variable + ", \"" + escapeLiteral(prefix) + "\"))");
        return (W) this;
    }

    /**
     * strends 过滤：字符串以指定后缀结尾
     *
     * <p>示例：{@code .filterStrEnds("?email", "@example.com")} 生成
     * {@code FILTER(strends(?email, "@example.com"))}</p>
     */
    public W filterStrEnds(String variable, String suffix) {
        filters.add("FILTER(strends(" + variable + ", \"" + escapeLiteral(suffix) + "\"))");
        return (W) this;
    }

    // =====================================================================
    // 构建辅助
    // =====================================================================

    /**
     * 构建 PREFIX 声明块
     */
    protected String buildPrefixBlock() {
        if (prefixes.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        prefixes.forEach((alias, uri) ->
                sb.append("PREFIX ").append(alias).append(": <").append(uri).append("> \n"));
        return sb.toString();
    }

    /**
     * 构建 WHERE { ... } 块
     */
    protected String buildWhereBlock() {
        StringBuilder sb = new StringBuilder("WHERE {\n");
        for (String p : patterns) {
            sb.append("  ").append(p).append("\n");
        }
        for (String f : filters) {
            sb.append("  ").append(f).append("\n");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * 构建完整的 SPARQL 语句
     */
    public abstract String build();

    /**
     * 转义字面量中的特殊字符
     */
    protected static String escapeLiteral(String value) {
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

