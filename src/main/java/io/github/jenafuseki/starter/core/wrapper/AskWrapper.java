package io.github.jenafuseki.starter.core.wrapper;

/**
 * SPARQL ASK 查询构建器
 *
 * <p>链式 API，用于构建 SPARQL ASK 语句，继承 {@link SparqlWrapper} 的
 * PREFIX / WHERE 模式 / FILTER 等公共方法。</p>
 *
 * <p><b>基本用法：</b></p>
 * <pre>{@code
 * AskWrapper wrapper = new AskWrapper()
 *     .prefixFoaf()
 *     .isA("?s", "foaf:Person")
 *     .triple("?s", "foaf:name", "?name")
 *     .filterEqLiteral("?name", "Alice");
 *
 * // ASK {
 * //   ?s a foaf:Person .
 * //   ?s foaf:name ?name .
 * //   FILTER(?name = "Alice")
 * // }
 *
 * boolean exists = fusekiTemplate.ask(wrapper);
 * }</pre>
 *
 * <p><b>直接指定 URI 主语：</b></p>
 * <pre>{@code
 * AskWrapper wrapper = new AskWrapper()
 *     .tripleUri("http://example.org/Alice", "?p", "?o");
 * // ASK { <http://example.org/Alice> ?p ?o . }
 * }</pre>
 */
public class AskWrapper extends SparqlWrapper<AskWrapper> {

    /**
     * 构建完整的 SPARQL ASK 语句
     *
     * @return SPARQL ASK 字符串
     */
    @Override
    public String build() {
        StringBuilder sb = new StringBuilder();

        // 1. PREFIX 块
        sb.append(buildPrefixBlock());

        // 2. ASK { ... }
        sb.append("ASK\n");
        sb.append(buildWhereBlock());

        return sb.toString();
    }
}

