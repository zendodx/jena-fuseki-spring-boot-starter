package io.github.jenafuseki.starter.core.wrapper;

import java.util.ArrayList;
import java.util.List;

/**
 * SPARQL SELECT 查询构建器
 *
 * <p>链式 API，类比 MyBatis-Plus 的 {@code QueryWrapper}，用于构建 SPARQL SELECT 语句。</p>
 *
 * <p><b>基本用法：</b></p>
 * <pre>{@code
 * SelectWrapper wrapper = new SelectWrapper()
 *     .prefixFoaf()
 *     .select("?name", "?age")
 *     .triple("?s", "a", "foaf:Person")
 *     .triple("?s", "foaf:name", "?name")
 *     .optional("?s foaf:age ?age .")
 *     .filterGt("?age", "18")
 *     .orderByAsc("?name")
 *     .limit(10)
 *     .offset(0);
 *
 * // SELECT ?name ?age
 * // WHERE {
 * //   ?s a foaf:Person .
 * //   ?s foaf:name ?name .
 * //   OPTIONAL { ?s foaf:age ?age . }
 * //   FILTER(?age > 18)
 * // }
 * // ORDER BY ASC(?name)
 * // LIMIT 10
 * // OFFSET 0
 *
 * List<Map<String, RDFNode>> results = fusekiTemplate.select(wrapper);
 * }</pre>
 *
 * <p><b>DISTINCT 用法：</b></p>
 * <pre>{@code
 * SelectWrapper wrapper = new SelectWrapper()
 *     .selectDistinct("?name")
 *     .triple("?s", "foaf:name", "?name");
 * }</pre>
 *
 * <p><b>查询所有变量（SELECT *）：</b></p>
 * <pre>{@code
 * SelectWrapper wrapper = new SelectWrapper()
 *     .selectAll()
 *     .triple("?s", "?p", "?o");
 * }</pre>
 */
public class SelectWrapper extends SparqlWrapper<SelectWrapper> {

    /** SELECT 投影变量列表，为空时等价于 SELECT * */
    private final List<String> selectVars = new ArrayList<>();

    /** 是否使用 DISTINCT */
    private boolean distinct = false;

    /** 是否使用 REDUCED */
    private boolean reduced = false;

    /** ORDER BY 子句列表 */
    private final List<String> orderByClauses = new ArrayList<>();

    /** LIMIT，-1 表示不设置 */
    private int limit = -1;

    /** OFFSET，-1 表示不设置 */
    private int offset = -1;

    /** GROUP BY 子句 */
    private final List<String> groupByClauses = new ArrayList<>();

    /** HAVING 子句 */
    private final List<String> havingClauses = new ArrayList<>();

    // =====================================================================
    // SELECT 投影
    // =====================================================================

    /**
     * 指定 SELECT 查询的投影变量
     *
     * <p>示例：{@code .select("?name", "?age")} 生成 {@code SELECT ?name ?age}</p>
     *
     * @param variables 变量名，如 {@code "?name"}，支持表达式如 {@code "(COUNT(?s) AS ?count)"}
     */
    public SelectWrapper select(String... variables) {
        for (String v : variables) {
            selectVars.add(v);
        }
        return this;
    }

    /**
     * 生成 {@code SELECT *}（查询所有变量）
     */
    public SelectWrapper selectAll() {
        selectVars.clear();
        return this;
    }

    /**
     * 生成 {@code SELECT DISTINCT ...}，消除重复结果行
     *
     * @param variables 变量名列表
     */
    public SelectWrapper selectDistinct(String... variables) {
        this.distinct = true;
        return select(variables);
    }

    /**
     * 开启 DISTINCT（与 {@link #select(String...)} 配合使用）
     */
    public SelectWrapper distinct() {
        this.distinct = true;
        return this;
    }

    /**
     * 生成 {@code SELECT REDUCED ...}，允许引擎对重复行进行部分去重（性能优于 DISTINCT）
     *
     * @param variables 变量名列表
     */
    public SelectWrapper selectReduced(String... variables) {
        this.reduced = true;
        return select(variables);
    }

    /**
     * 开启 REDUCED（与 {@link #select(String...)} 配合使用）
     */
    public SelectWrapper reduced() {
        this.reduced = true;
        return this;
    }

    /**
     * 在 SELECT 投影中添加聚合表达式
     *
     * <p>示例：
     * <pre>{@code
     * .select("?type")
     * .aggregate("COUNT(?s)", "?count")   // (COUNT(?s) AS ?count)
     * .aggregate("SUM(?price)", "?total") // (SUM(?price) AS ?total)
     * }</pre>
     *
     * @param expression 聚合表达式，如 {@code "COUNT(?s)"} / {@code "AVG(?age)"} / {@code "MAX(?score)"}
     * @param asVar      绑定变量名，如 {@code "?count"}
     */
    public SelectWrapper aggregate(String expression, String asVar) {
        selectVars.add("(" + expression + " AS " + asVar + ")");
        return this;
    }

    // =====================================================================
    // ORDER BY
    // =====================================================================

    /**
     * 升序排序
     *
     * <p>示例：{@code .orderByAsc("?name")} 生成 {@code ORDER BY ASC(?name)}</p>
     */
    public SelectWrapper orderByAsc(String variable) {
        orderByClauses.add("ASC(" + variable + ")");
        return this;
    }

    /**
     * 降序排序
     *
     * <p>示例：{@code .orderByDesc("?age")} 生成 {@code ORDER BY DESC(?age)}</p>
     */
    public SelectWrapper orderByDesc(String variable) {
        orderByClauses.add("DESC(" + variable + ")");
        return this;
    }

    /**
     * 原始 ORDER BY 表达式（支持多字段组合排序）
     *
     * <p>示例：{@code .orderBy("?name")} 生成 {@code ORDER BY ?name}</p>
     */
    public SelectWrapper orderBy(String expression) {
        orderByClauses.add(expression);
        return this;
    }

    // =====================================================================
    // LIMIT / OFFSET
    // =====================================================================

    /**
     * 限制结果条数
     *
     * <p>示例：{@code .limit(10)} 生成 {@code LIMIT 10}</p>
     */
    public SelectWrapper limit(int n) {
        this.limit = n;
        return this;
    }

    /**
     * 结果偏移量（分页用）
     *
     * <p>示例：{@code .offset(20)} 生成 {@code OFFSET 20}</p>
     */
    public SelectWrapper offset(int n) {
        this.offset = n;
        return this;
    }

    /**
     * 分页快捷方法，等价于同时设置 LIMIT 和 OFFSET
     *
     * @param page     页码（从 1 开始）
     * @param pageSize 每页条数
     */
    public SelectWrapper page(int page, int pageSize) {
        this.limit = pageSize;
        this.offset = (page - 1) * pageSize;
        return this;
    }

    // =====================================================================
    // GROUP BY / HAVING
    // =====================================================================

    /**
     * GROUP BY 分组
     *
     * <p>示例：{@code .groupBy("?type")} 生成 {@code GROUP BY ?type}</p>
     */
    public SelectWrapper groupBy(String... variables) {
        for (String v : variables) {
            groupByClauses.add(v);
        }
        return this;
    }

    /**
     * HAVING 过滤（在 GROUP BY 之后）
     *
     * <p>示例：{@code .having("COUNT(?s) > 5")} 生成 {@code HAVING(COUNT(?s) > 5)}</p>
     */
    public SelectWrapper having(String expression) {
        havingClauses.add("HAVING(" + expression + ")");
        return this;
    }

    // =====================================================================
    // 构建
    // =====================================================================

    /**
     * 构建完整的 SPARQL SELECT 语句
     *
     * @return SPARQL SELECT 字符串
     */
    @Override
    public String build() {
        StringBuilder sb = new StringBuilder();

        // 1. PREFIX 块
        sb.append(buildPrefixBlock());

        // 2. SELECT 行
        sb.append("SELECT ");
        if (distinct) {
            sb.append("DISTINCT ");
        } else if (reduced) {
            sb.append("REDUCED ");
        }
        if (selectVars.isEmpty()) {
            sb.append("*");
        } else {
            sb.append(String.join(" ", selectVars));
        }
        sb.append("\n");

        // 3. WHERE 块
        sb.append(buildWhereBlock());

        // 4. GROUP BY
        if (!groupByClauses.isEmpty()) {
            sb.append("\nGROUP BY ").append(String.join(" ", groupByClauses));
        }

        // 5. HAVING
        for (String having : havingClauses) {
            sb.append("\n").append(having);
        }

        // 6. ORDER BY
        if (!orderByClauses.isEmpty()) {
            sb.append("\nORDER BY ").append(String.join(" ", orderByClauses));
        }

        // 7. LIMIT / OFFSET
        if (limit >= 0) {
            sb.append("\nLIMIT ").append(limit);
        }
        if (offset >= 0) {
            sb.append("\nOFFSET ").append(offset);
        }

        return sb.toString();
    }
}

