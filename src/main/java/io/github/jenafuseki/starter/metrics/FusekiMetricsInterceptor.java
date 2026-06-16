package io.github.jenafuseki.starter.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Fuseki 操作 Metrics 采集器
 *
 * <p>基于 Micrometer 对 SPARQL 操作进行计时和计数，暴露以下指标：</p>
 * <ul>
 *   <li>{@code fuseki.sparql.select.count} - SELECT 执行次数</li>
 *   <li>{@code fuseki.sparql.select.duration} - SELECT 执行耗时（Timer）</li>
 *   <li>{@code fuseki.sparql.ask.count} - ASK 执行次数</li>
 *   <li>{@code fuseki.sparql.update.count} - UPDATE 执行次数</li>
 *   <li>{@code fuseki.sparql.update.duration} - UPDATE 执行耗时（Timer）</li>
 *   <li>{@code fuseki.sparql.error.count} - 执行异常次数</li>
 * </ul>
 *
 * <p>仅当 classpath 存在 {@code micrometer-core} 时自动装配。</p>
 */
public class FusekiMetricsInterceptor {

    private static final Logger log = LoggerFactory.getLogger(FusekiMetricsInterceptor.class);

    // 指标名称常量
    public static final String METRIC_SELECT_COUNT    = "fuseki.sparql.select.count";
    public static final String METRIC_SELECT_DURATION = "fuseki.sparql.select.duration";
    public static final String METRIC_ASK_COUNT       = "fuseki.sparql.ask.count";
    public static final String METRIC_CONSTRUCT_COUNT = "fuseki.sparql.construct.count";
    public static final String METRIC_UPDATE_COUNT    = "fuseki.sparql.update.count";
    public static final String METRIC_UPDATE_DURATION = "fuseki.sparql.update.duration";
    public static final String METRIC_ERROR_COUNT     = "fuseki.sparql.error.count";

    private final MeterRegistry meterRegistry;

    // SELECT 指标
    private final Counter selectCounter;
    private final Timer   selectTimer;

    // ASK 指标
    private final Counter askCounter;

    // CONSTRUCT 指标
    private final Counter constructCounter;

    // UPDATE 指标
    private final Counter updateCounter;
    private final Timer   updateTimer;

    // 错误指标
    private final Counter errorCounter;

    public FusekiMetricsInterceptor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.selectCounter    = Counter.builder(METRIC_SELECT_COUNT)
                .description("SPARQL SELECT 执行次数")
                .register(meterRegistry);
        this.selectTimer      = Timer.builder(METRIC_SELECT_DURATION)
                .description("SPARQL SELECT 执行耗时")
                .register(meterRegistry);
        this.askCounter       = Counter.builder(METRIC_ASK_COUNT)
                .description("SPARQL ASK 执行次数")
                .register(meterRegistry);
        this.constructCounter = Counter.builder(METRIC_CONSTRUCT_COUNT)
                .description("SPARQL CONSTRUCT 执行次数")
                .register(meterRegistry);
        this.updateCounter    = Counter.builder(METRIC_UPDATE_COUNT)
                .description("SPARQL UPDATE 执行次数")
                .register(meterRegistry);
        this.updateTimer      = Timer.builder(METRIC_UPDATE_DURATION)
                .description("SPARQL UPDATE 执行耗时")
                .register(meterRegistry);
        this.errorCounter     = Counter.builder(METRIC_ERROR_COUNT)
                .description("SPARQL 执行异常次数")
                .register(meterRegistry);
    }

    /**
     * 包装 SELECT 操作，自动计数 + 计时
     *
     * @param operation 实际执行的操作
     * @param <T>       返回类型
     * @return 操作结果
     */
    public <T> T recordSelect(Supplier<T> operation) {
        long start = System.nanoTime();
        try {
            T result = operation.get();
            selectCounter.increment();
            selectTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            return result;
        } catch (Exception e) {
            errorCounter.increment();
            throw e;
        }
    }

    /**
     * 包装 ASK 操作，自动计数
     *
     * @param operation 实际执行的操作
     * @return 操作结果
     */
    public boolean recordAsk(Supplier<Boolean> operation) {
        try {
            boolean result = operation.get();
            askCounter.increment();
            return result;
        } catch (Exception e) {
            errorCounter.increment();
            throw e;
        }
    }

    /**
     * 包装 CONSTRUCT 操作，自动计数
     *
     * @param operation 实际执行的操作
     * @param <T>       返回类型
     * @return 操作结果
     */
    public <T> T recordConstruct(Supplier<T> operation) {
        try {
            T result = operation.get();
            constructCounter.increment();
            return result;
        } catch (Exception e) {
            errorCounter.increment();
            throw e;
        }
    }

    /**
     * 包装 UPDATE 操作，自动计数 + 计时
     *
     * @param operation 实际执行的操作（无返回值，封装为 Runnable）
     */
    public void recordUpdate(Runnable operation) {
        long start = System.nanoTime();
        try {
            operation.run();
            updateCounter.increment();
            updateTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            errorCounter.increment();
            throw e;
        }
    }

    /**
     * 获取底层 MeterRegistry，支持自定义指标扩展
     */
    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }
}

