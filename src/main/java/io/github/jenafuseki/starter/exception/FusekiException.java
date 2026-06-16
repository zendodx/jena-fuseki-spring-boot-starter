package io.github.jenafuseki.starter.exception;

/**
 * Fuseki 操作统一异常
 * <p>封装底层 HTTP 通信、SPARQL 解析、连接等异常，对外提供统一的 RuntimeException</p>
 */
public class FusekiException extends RuntimeException {

    public FusekiException(String message) {
        super(message);
    }

    public FusekiException(String message, Throwable cause) {
        super(message, cause);
    }

    public FusekiException(Throwable cause) {
        super(cause);
    }
}

