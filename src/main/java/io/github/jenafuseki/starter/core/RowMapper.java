package io.github.jenafuseki.starter.core;

import org.apache.jena.query.QuerySolution;

/**
 * SPARQL SELECT 结果行映射器
 *
 * <p>类比 Spring JDBC 的 {@code RowMapper}，将 SPARQL SELECT 的每一行
 * {@link QuerySolution} 映射为业务对象。</p>
 *
 * <pre>{@code
 * List<Person> list = template.select(sparql, row -> {
 *     Person p = new Person();
 *     p.setName(row.getLiteral("name").getString());
 *     p.setAge(row.getLiteral("age").getInt());
 *     return p;
 * });
 * }</pre>
 *
 * @param <T> 目标对象类型
 */
@FunctionalInterface
public interface RowMapper<T> {

    /**
     * 将 SPARQL 查询结果的一行映射为目标对象
     *
     * @param row    当前行的 QuerySolution，通过变量名获取 RDFNode
     * @param rowNum 当前行号（从 0 开始）
     * @return 映射后的目标对象
     */
    T mapRow(QuerySolution row, int rowNum);
}

