package io.github.jenafuseki.starter.core.wrapper;

import io.github.jenafuseki.starter.exception.FusekiException;

import java.util.ArrayList;
import java.util.List;

/**
 * SPARQL UPDATE 构建器
 *
 * <p>支持以下几种 UPDATE 模式，链式 API：</p>
 * <ul>
 *   <li><b>INSERT DATA</b>：插入固定三元组数据</li>
 *   <li><b>DELETE DATA</b>：删除固定三元组数据</li>
 *   <li><b>DELETE WHERE</b>：按条件删除（WHERE 中包含变量）</li>
 *   <li><b>INSERT / DELETE（结合 WHERE）</b>：先查询再修改</li>
 *   <li><b>CLEAR / DROP</b>：清空或删除图</li>
 *   <li><b>CREATE GRAPH</b>：创建命名图</li>
 *   <li><b>LOAD</b>：从 URL 加载 RDF 数据</li>
 *   <li><b>ADD / COPY / MOVE</b>：图数据复制/移动</li>
 * </ul>
 *
 * <p><b>INSERT DATA 用法：</b></p>
 * <pre>{@code
 * UpdateWrapper wrapper = new UpdateWrapper()
 *     .prefixFoaf()
 *     .insertData()
 *     .triple("<http://example.org/Alice>", "foaf:name", "\"Alice\"")
 *     .triple("<http://example.org/Alice>", "foaf:age", "\"30\"^^xsd:integer");
 *
 * // INSERT DATA {
 * //   <http://example.org/Alice> foaf:name "Alice" .
 * //   <http://example.org/Alice> foaf:age "30"^^xsd:integer .
 * // }
 * }</pre>
 *
 * <p><b>DELETE DATA 用法：</b></p>
 * <pre>{@code
 * UpdateWrapper wrapper = new UpdateWrapper()
 *     .deleteData()
 *     .triple("<http://example.org/Alice>", "<http://xmlns.com/foaf/0.1/name>", "\"Alice\"");
 * }</pre>
 *
 * <p><b>DELETE WHERE 用法（按条件删除）：</b></p>
 * <pre>{@code
 * UpdateWrapper wrapper = new UpdateWrapper()
 *     .prefixFoaf()
 *     .deleteWhere()
 *     .isA("?s", "foaf:Person")
 *     .triple("?s", "foaf:name", "?name")
 *     .filterEqLiteral("?name", "Alice");
 *
 * // DELETE WHERE {
 * //   ?s a foaf:Person .
 * //   ?s foaf:name ?name .
 * //   FILTER(?name = "Alice")
 * // }
 * }</pre>
 *
 * <p><b>INSERT ... WHERE（先查后写）：</b></p>
 * <pre>{@code
 * UpdateWrapper wrapper = new UpdateWrapper()
 *     .prefixFoaf()
 *     .insertTemplate("?s foaf:knows <http://example.org/Bob> .")
 *     .triple("?s", "a", "foaf:Person")
 *     .filterEqLiteral("?s", "<http://example.org/Alice>");
 * }</pre>
 *
 * <p><b>CLEAR 用法：</b></p>
 * <pre>{@code
 * UpdateWrapper wrapper = new UpdateWrapper().clearDefault();
 * UpdateWrapper wrapper = new UpdateWrapper().clearAll();
 * UpdateWrapper wrapper = new UpdateWrapper().clearGraph("http://example.org/g1");
 * }</pre>
 *
 * <p><b>LOAD 用法：</b></p>
 * <pre>{@code
 * // 加载到命名图
 * new UpdateWrapper().loadIntoGraph("http://example.org/data.ttl", "http://example.org/g1");
 * // 加载到默认图
 * new UpdateWrapper().load("http://example.org/data.ttl");
 * }</pre>
 *
 * <p><b>ADD / COPY / MOVE 用法（传 null 表示 DEFAULT 图）：</b></p>
 * <pre>{@code
 * new UpdateWrapper().add("http://example.org/src", "http://example.org/dst");
 * new UpdateWrapper().copy(null, "http://example.org/dst");  // DEFAULT TO dst
 * new UpdateWrapper().move("http://example.org/src", null);  // src TO DEFAULT
 * }</pre>
 */
public class UpdateWrapper extends SparqlWrapper<UpdateWrapper> {

    /**
     * UPDATE 操作类型
     */
    private enum Mode {
        INSERT_DATA,
        DELETE_DATA,
        DELETE_WHERE,
        INSERT_WHERE,
        DELETE_INSERT_WHERE,
        CLEAR_DEFAULT,
        CLEAR_ALL,
        CLEAR_GRAPH,
        DROP_GRAPH,
        DROP_DEFAULT,
        DROP_ALL,
        CREATE_GRAPH,
        LOAD,
        ADD,
        COPY,
        MOVE
    }

    private Mode mode;

    /**
     * CLEAR/DROP/CREATE/LOAD INTO 图 URI
     */
    private String graphUri;

    /**
     * ADD/COPY/MOVE 源图 URI（null 表示 DEFAULT）
     */
    private String srcGraphUri;

    /**
     * ADD/COPY/MOVE 目标图 URI（null 表示 DEFAULT）
     */
    private String dstGraphUri;

    /**
     * LOAD 来源 URL
     */
    private String loadFromUri;

    /**
     * 操作是否加 SILENT 修饰符（失败时不抛异常）
     */
    private boolean silent = false;

    /**
     * INSERT { ... } 模板三元组（用于 INSERT ... WHERE）
     */
    private final List<String> insertTemplates = new ArrayList<>();

    /**
     * DELETE { ... } 模板三元组（用于 DELETE ... WHERE）
     */
    private final List<String> deleteTemplates = new ArrayList<>();

    // =====================================================================
    // 操作模式设置
    // =====================================================================

    /**
     * 切换到 INSERT DATA 模式
     *
     * <p>之后调用 {@link #triple} 等方法添加的内容作为 INSERT DATA 的数据体。</p>
     */
    public UpdateWrapper insertData() {
        this.mode = Mode.INSERT_DATA;
        return this;
    }

    /**
     * 切换到 DELETE DATA 模式
     *
     * <p>之后调用 {@link #triple} 等方法添加的内容作为 DELETE DATA 的数据体。</p>
     */
    public UpdateWrapper deleteData() {
        this.mode = Mode.DELETE_DATA;
        return this;
    }

    /**
     * 切换到 DELETE WHERE 模式
     *
     * <p>之后调用 {@link #triple} / {@link #filter} 等方法设置 WHERE 查询条件，
     * 匹配到的三元组将被删除。</p>
     */
    public UpdateWrapper deleteWhere() {
        this.mode = Mode.DELETE_WHERE;
        return this;
    }

    /**
     * 切换到 INSERT ... WHERE 模式，指定 INSERT 模板
     *
     * <p>先用 WHERE 查询，再将模板三元组插入。
     * 示例：{@code .insertTemplate("?s foaf:knows <http://example.org/Bob> .")}
     * 之后调用 {@link #triple} 等设置 WHERE 条件。</p>
     *
     * @param templates INSERT 模板三元组片段（可调用多次）
     */
    public UpdateWrapper insertTemplate(String... templates) {
        this.mode = Mode.INSERT_WHERE;
        for (String t : templates) {
            insertTemplates.add(t);
        }
        return this;
    }

    /**
     * 切换到 DELETE ... INSERT ... WHERE 模式，同时指定 DELETE 和 INSERT 模板
     * <p>
     * 用于"先删后写"场景，例如修改属性值：
     * <pre>{@code
     * new UpdateWrapper()
     *     .prefixFoaf()
     *     .deleteInsertTemplate(
     *         "?s foaf:age ?oldAge .",       // DELETE 模板
     *         "?s foaf:age \"31\"^^xsd:integer ." // INSERT 模板
     *     )
     *     .triple("?s", "foaf:name", "\"Alice\"")
     *     .triple("?s", "foaf:age", "?oldAge");
     * }</pre>
     *
     * @param deleteTpl DELETE 模板三元组
     * @param insertTpl INSERT 模板三元组
     */
    public UpdateWrapper deleteInsertTemplate(String deleteTpl, String insertTpl) {
        this.mode = Mode.DELETE_INSERT_WHERE;
        this.deleteTemplates.add(deleteTpl);
        this.insertTemplates.add(insertTpl);
        return this;
    }

    /**
     * 添加更多 DELETE 模板三元组（配合 {@link #deleteInsertTemplate} 使用）
     */
    public UpdateWrapper addDeleteTemplate(String template) {
        this.deleteTemplates.add(template);
        return this;
    }

    /**
     * 添加更多 INSERT 模板三元组（配合 {@link #insertTemplate} / {@link #deleteInsertTemplate} 使用）
     */
    public UpdateWrapper addInsertTemplate(String template) {
        this.insertTemplates.add(template);
        return this;
    }

    // =====================================================================
    // CLEAR / DROP 快捷操作
    // =====================================================================

    /**
     * 生成 {@code CLEAR DEFAULT}（清空默认图所有三元组）
     */
    public UpdateWrapper clearDefault() {
        this.mode = Mode.CLEAR_DEFAULT;
        return this;
    }

    /**
     * 生成 {@code CLEAR ALL}（清空所有图所有三元组）
     */
    public UpdateWrapper clearAll() {
        this.mode = Mode.CLEAR_ALL;
        return this;
    }

    /**
     * 生成 {@code CLEAR GRAPH <graphUri>}（清空指定命名图）
     *
     * @param graphUri 命名图 URI（不含尖括号）
     */
    public UpdateWrapper clearGraph(String graphUri) {
        this.mode = Mode.CLEAR_GRAPH;
        this.graphUri = graphUri;
        return this;
    }

    /**
     * 生成 {@code DROP GRAPH <graphUri>}（删除指定命名图及其所有三元组）
     *
     * @param graphUri 命名图 URI（不含尖括号）
     */
    public UpdateWrapper dropGraph(String graphUri) {
        this.mode = Mode.DROP_GRAPH;
        this.graphUri = graphUri;
        return this;
    }

    /**
     * 生成 {@code DROP DEFAULT}（删除默认图）
     */
    public UpdateWrapper dropDefault() {
        this.mode = Mode.DROP_DEFAULT;
        return this;
    }

    /**
     * 生成 {@code DROP ALL}（删除所有图）
     */
    public UpdateWrapper dropAll() {
        this.mode = Mode.DROP_ALL;
        return this;
    }

    // =====================================================================
    // CREATE / LOAD 操作
    // =====================================================================

    /**
     * 生成 {@code CREATE GRAPH <graphUri>}（创建命名图）
     *
     * @param graphUri 命名图 URI（不含尖括号）
     */
    public UpdateWrapper createGraph(String graphUri) {
        this.mode = Mode.CREATE_GRAPH;
        this.graphUri = graphUri;
        return this;
    }

    /**
     * 生成 {@code LOAD <fromUri> INTO GRAPH <intoUri>}（从 URL 加载 RDF 到命名图）
     *
     * @param fromUri 源 RDF 文档 URL
     * @param intoUri 目标命名图 URI（不含尖括号）
     */
    public UpdateWrapper loadIntoGraph(String fromUri, String intoUri) {
        this.mode = Mode.LOAD;
        this.loadFromUri = fromUri;
        this.graphUri = intoUri;
        return this;
    }

    /**
     * 生成 {@code LOAD <fromUri>}（从 URL 加载 RDF 到默认图）
     *
     * @param fromUri 源 RDF 文档 URL
     */
    public UpdateWrapper load(String fromUri) {
        this.mode = Mode.LOAD;
        this.loadFromUri = fromUri;
        this.graphUri = null;
        return this;
    }

    // =====================================================================
    // ADD / COPY / MOVE 操作
    // =====================================================================

    /**
     * 生成 {@code ADD <src> TO <dst>}（将源图三元组复制到目标图，保留源图）
     *
     * <p>传 {@code null} 表示 DEFAULT 图。</p>
     *
     * @param srcUri 源图 URI（不含尖括号），{@code null} 表示 DEFAULT
     * @param dstUri 目标图 URI（不含尖括号），{@code null} 表示 DEFAULT
     */
    public UpdateWrapper add(String srcUri, String dstUri) {
        this.mode = Mode.ADD;
        this.srcGraphUri = srcUri;
        this.dstGraphUri = dstUri;
        return this;
    }

    /**
     * 生成 {@code COPY <src> TO <dst>}（将源图内容复制到目标图，目标图先被清空）
     *
     * @param srcUri 源图 URI，{@code null} 表示 DEFAULT
     * @param dstUri 目标图 URI，{@code null} 表示 DEFAULT
     */
    public UpdateWrapper copy(String srcUri, String dstUri) {
        this.mode = Mode.COPY;
        this.srcGraphUri = srcUri;
        this.dstGraphUri = dstUri;
        return this;
    }

    /**
     * 生成 {@code MOVE <src> TO <dst>}（将源图移动到目标图，源图被删除）
     *
     * @param srcUri 源图 URI，{@code null} 表示 DEFAULT
     * @param dstUri 目标图 URI，{@code null} 表示 DEFAULT
     */
    public UpdateWrapper move(String srcUri, String dstUri) {
        this.mode = Mode.MOVE;
        this.srcGraphUri = srcUri;
        this.dstGraphUri = dstUri;
        return this;
    }

    /**
     * 开启 SILENT 修饰符：操作失败时不抛出异常
     *
     * <p>适用于 CLEAR / DROP / CREATE / LOAD / ADD / COPY / MOVE 操作。</p>
     */
    public UpdateWrapper silent() {
        this.silent = true;
        return this;
    }

    // =====================================================================
    // 构建
    // =====================================================================

    /**
     * 构建完整的 SPARQL UPDATE 语句
     *
     * @return SPARQL UPDATE 字符串
     * @throws FusekiException 若未指定 UPDATE 模式
     */
    @Override
    public String build() {
        if (mode == null) {
            throw new FusekiException(
                    "UpdateWrapper 未指定操作模式，请先调用 insertData() / deleteData() / " +
                            "deleteWhere() / insertTemplate() / deleteInsertTemplate() / clearDefault() 等方法");
        }

        StringBuilder sb = new StringBuilder();

        // PREFIX 块
        sb.append(buildPrefixBlock());

        switch (mode) {
            case INSERT_DATA:
                sb.append("INSERT DATA {\n");
                for (String p : patterns) {
                    sb.append("  ").append(p).append("\n");
                }
                sb.append("}");
                break;

            case DELETE_DATA:
                sb.append("DELETE DATA {\n");
                for (String p : patterns) {
                    sb.append("  ").append(p).append("\n");
                }
                sb.append("}");
                break;

            case DELETE_WHERE:
                // SPARQL 1.1 的 DELETE WHERE { } 简写形式不支持 FILTER/OPTIONAL 等
                // 当存在 FILTER 时，自动扩展为完整的 DELETE { ... } WHERE { ... } 形式：
                //   DELETE 块 = WHERE 块中的三元组模式（不含 FILTER）
                //   WHERE 块  = 三元组模式 + FILTER
                if (!filters.isEmpty()) {
                    sb.append("DELETE {\n");
                    for (String p : patterns) {
                        sb.append("  ").append(p).append("\n");
                    }
                    sb.append("}\n");
                    sb.append(buildWhereBlock());
                } else {
                    sb.append("DELETE ").append(buildWhereBlock());
                }
                break;

            case INSERT_WHERE:
                sb.append("INSERT {\n");
                for (String t : insertTemplates) {
                    sb.append("  ").append(t).append("\n");
                }
                sb.append("}\n");
                sb.append(buildWhereBlock());
                break;

            case DELETE_INSERT_WHERE:
                sb.append("DELETE {\n");
                for (String t : deleteTemplates) {
                    sb.append("  ").append(t).append("\n");
                }
                sb.append("}\n");
                sb.append("INSERT {\n");
                for (String t : insertTemplates) {
                    sb.append("  ").append(t).append("\n");
                }
                sb.append("}\n");
                sb.append(buildWhereBlock());
                break;

            case CLEAR_DEFAULT:
                sb.append("CLEAR ");
                if (silent) sb.append("SILENT ");
                sb.append("DEFAULT");
                break;

            case CLEAR_ALL:
                sb.append("CLEAR ");
                if (silent) sb.append("SILENT ");
                sb.append("ALL");
                break;

            case CLEAR_GRAPH:
                sb.append("CLEAR ");
                if (silent) sb.append("SILENT ");
                sb.append("GRAPH <").append(graphUri).append(">");
                break;

            case DROP_GRAPH:
                sb.append("DROP ");
                if (silent) sb.append("SILENT ");
                sb.append("GRAPH <").append(graphUri).append(">");
                break;

            case DROP_DEFAULT:
                sb.append("DROP ");
                if (silent) sb.append("SILENT ");
                sb.append("DEFAULT");
                break;

            case DROP_ALL:
                sb.append("DROP ");
                if (silent) sb.append("SILENT ");
                sb.append("ALL");
                break;

            case CREATE_GRAPH:
                sb.append("CREATE ");
                if (silent) sb.append("SILENT ");
                sb.append("GRAPH <").append(graphUri).append(">");
                break;

            case LOAD:
                sb.append("LOAD ");
                if (silent) sb.append("SILENT ");
                sb.append("<").append(loadFromUri).append(">");
                if (graphUri != null) {
                    sb.append(" INTO GRAPH <").append(graphUri).append(">");
                }
                break;

            case ADD:
                sb.append("ADD ");
                if (silent) sb.append("SILENT ");
                sb.append(graphRef(srcGraphUri)).append(" TO ").append(graphRef(dstGraphUri));
                break;

            case COPY:
                sb.append("COPY ");
                if (silent) sb.append("SILENT ");
                sb.append(graphRef(srcGraphUri)).append(" TO ").append(graphRef(dstGraphUri));
                break;

            case MOVE:
                sb.append("MOVE ");
                if (silent) sb.append("SILENT ");
                sb.append(graphRef(srcGraphUri)).append(" TO ").append(graphRef(dstGraphUri));
                break;

            default:
                throw new FusekiException("未知的 UpdateWrapper 操作模式: " + mode);
        }

        return sb.toString();
    }

    /**
     * 将 URI 或 null（DEFAULT）转换为 SPARQL 图引用字符串
     */
    private static String graphRef(String uri) {
        return uri == null ? "DEFAULT" : "GRAPH <" + uri + ">";
    }

    // =====================================================================
    // 便捷工厂方法：直接插入/删除单条字面量三元组
    // =====================================================================

    /**
     * 快捷：将 &lt;subject&gt; &lt;predicate&gt; "object" 加入 INSERT DATA 或 DELETE DATA 数据体
     *
     * <p>主语/谓语自动包裹尖括号，宾语为字符串字面量（自动加引号并转义）。</p>
     *
     * <pre>{@code
     * new UpdateWrapper()
     *     .insertData()
     *     .tripleLiteral("http://example.org/Alice", "http://xmlns.com/foaf/0.1/name", "Alice");
     * }</pre>
     *
     * @param subjectUri   主语 URI
     * @param predicateUri 谓语 URI
     * @param literal      宾语字符串字面量
     */
    public UpdateWrapper tripleLiteral(String subjectUri, String predicateUri, String literal) {
        patterns.add("<" + subjectUri + "> <" + predicateUri + "> \"" + escapeLiteral(literal) + "\" .");
        return this;
    }

    /**
     * 快捷：将 &lt;subject&gt; &lt;predicate&gt; &lt;objectUri&gt; 加入 INSERT DATA 或 DELETE DATA 数据体
     *
     * <p>主语/谓语/宾语全部为 URI，自动包裹尖括号。</p>
     *
     * <pre>{@code
     * new UpdateWrapper()
     *     .insertData()
     *     .tripleUris("http://example.org/Alice",
     *                 "http://www.w3.org/1999/02/22-rdf-syntax-ns#type",
     *                 "http://xmlns.com/foaf/0.1/Person");
     * }</pre>
     */
    public UpdateWrapper tripleUris(String subjectUri, String predicateUri, String objectUri) {
        patterns.add("<" + subjectUri + "> <" + predicateUri + "> <" + objectUri + "> .");
        return this;
    }
}

