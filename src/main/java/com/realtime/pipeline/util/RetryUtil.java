package com.realtime.pipeline.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * 重试工具类
 * 提供通用的重试机制
 */
public class RetryUtil {
    private static final Logger logger = LoggerFactory.getLogger(RetryUtil.class);

    /**
     * 执行带重试的操作
     * @param operation 要执行的操作
     * @param maxAttempts 最大尝试次数
     * @param backoffSeconds 重试间隔（秒）
     * @param operationName 操作名称（用于日志）
     * @return 操作结果
     * @throws Exception 如果所有重试都失败
     */
    public static <T> T executeWithRetry(
            Callable<T> operation,
            int maxAttempts,
            int backoffSeconds,
            String operationName) throws Exception {
        
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                logger.debug("Executing {} (attempt {}/{})", operationName, attempt, maxAttempts);
                return operation.call();
            } catch (Exception e) {
                lastException = e;
                logger.warn("Failed to execute {} (attempt {}/{}): {}", 
                           operationName, attempt, maxAttempts, e.getMessage());
                
                if (attempt < maxAttempts) {
                    logger.info("Retrying {} in {} seconds...", operationName, backoffSeconds);
                    try {
                        TimeUnit.SECONDS.sleep(backoffSeconds);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted", ie);
                    }
                }
            }
        }
        
        logger.error("All {} attempts failed for {}", maxAttempts, operationName);
        throw lastException;
    }

    /**
     * 执行带重试的操作（无返回值）
     * @param operation 要执行的操作
     * @param maxAttempts 最大尝试次数
     * @param backoffSeconds 重试间隔（秒）
     * @param operationName 操作名称（用于日志）
     * @throws Exception 如果所有重试都失败
     */
    public static void executeWithRetry(
            Runnable operation,
            int maxAttempts,
            int backoffSeconds,
            String operationName) throws Exception {
        
        executeWithRetry(() -> {
            operation.run();
            return null;
        }, maxAttempts, backoffSeconds, operationName);
    }

    /**
     * 执行带重试的操作（支持特定异常类型）
     * @param operation 要执行的操作
     * @param maxAttempts 最大尝试次数
     * @param backoffMillis 重试间隔（毫秒）
     * @param retryableException 可重试的异常类型
     * @throws Exception 如果所有重试都失败
     */
    public static void executeWithRetry(
            Runnable operation,
            int maxAttempts,
            long backoffMillis,
            Class<? extends Exception> retryableException) throws Exception {
        
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                logger.debug("Executing operation (attempt {}/{})", attempt, maxAttempts);
                operation.run();
                return; // 成功，直接返回
            } catch (Exception e) {
                lastException = e;
                
                // 检查是否为可重试的异常
                if (!retryableException.isInstance(e)) {
                    logger.error("Non-retryable exception occurred: {}", e.getMessage());
                    throw e;
                }
                
                logger.warn("Failed to execute operation (attempt {}/{}): {}", 
                           attempt, maxAttempts, e.getMessage());
                
                if (attempt < maxAttempts) {
                    logger.info("Retrying in {} ms...", backoffMillis);
                    try {
                        TimeUnit.MILLISECONDS.sleep(backoffMillis);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted", ie);
                    }
                }
            }
        }
        
        logger.error("All {} attempts failed", maxAttempts);
        if (lastException != null) {
            throw lastException;
        }
    }

    /**
     * 执行带指数退避的重试
     * @param operation 要执行的操作
     * @param maxAttempts 最大尝试次数
     * @param initialBackoffSeconds 初始重试间隔（秒）
     * @param operationName 操作名称（用于日志）
     * @return 操作结果
     * @throws Exception 如果所有重试都失败
     */
    public static <T> T executeWithExponentialBackoff(
            Callable<T> operation,
            int maxAttempts,
            int initialBackoffSeconds,
            String operationName) throws Exception {
        
        Exception lastException = null;
        int backoff = initialBackoffSeconds;
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                logger.debug("Executing {} (attempt {}/{})", operationName, attempt, maxAttempts);
                return operation.call();
            } catch (Exception e) {
                lastException = e;
                logger.warn("Failed to execute {} (attempt {}/{}): {}", 
                           operationName, attempt, maxAttempts, e.getMessage());
                
                if (attempt < maxAttempts) {
                    logger.info("Retrying {} in {} seconds...", operationName, backoff);
                    try {
                        TimeUnit.SECONDS.sleep(backoff);
                        backoff *= 2; // 指数退避
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted", ie);
                    }
                }
            }
        }
        
        logger.error("All {} attempts failed for {}", maxAttempts, operationName);
        throw lastException;
    }
}
