package com.realtime.pipeline.cdc;

import com.realtime.pipeline.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 数据库连接管理器
 * 负责管理数据库连接和自动重连逻辑
 * 
 * 功能:
 * - 建立和维护数据库连接
 * - 检测连接断开
 * - 自动重连（30秒内）
 * - 连接健康检查
 * 
 * 需求: 1.6, 1.7
 */
public class ConnectionManager implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(ConnectionManager.class);

    private final DatabaseConfig config;
    private final AtomicReference<Connection> connection = new AtomicReference<>();
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    
    private transient ScheduledExecutorService reconnectExecutor;
    private transient ScheduledExecutorService healthCheckExecutor;

    /**
     * 构造函数
     * @param config 数据库配置
     */
    public ConnectionManager(DatabaseConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Database config cannot be null");
        }
        this.config = config;
    }

    /**
     * 建立数据库连接
     * 
     * @return 数据库连接
     * @throws SQLException 如果连接失败
     */
    public Connection connect() throws SQLException {
        logger.info("Connecting to database: {}:{}/{}", 
            config.getHost(), config.getPort(), config.getSchema());

        try {
            // 构建JDBC URL（OceanBase兼容MySQL协议）
            String jdbcUrl = String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&connectTimeout=%d",
                config.getHost(),
                config.getPort(),
                config.getSchema(),
                config.getConnectionTimeout() * 1000
            );

            // 建立连接
            Connection conn = DriverManager.getConnection(
                jdbcUrl,
                config.getUsername(),
                config.getPassword()
            );

            connection.set(conn);
            connected.set(true);
            reconnecting.set(false);

            logger.info("Successfully connected to database");

            // 启动健康检查
            startHealthCheck();

            return conn;

        } catch (SQLException e) {
            connected.set(false);
            logger.error("Failed to connect to database", e);
            
            // 启动自动重连
            startAutoReconnect();
            
            throw e;
        }
    }

    /**
     * 断开数据库连接
     */
    public void disconnect() {
        logger.info("Disconnecting from database");
        
        // 停止健康检查和重连任务
        stopHealthCheck();
        stopAutoReconnect();
        
        Connection conn = connection.get();
        if (conn != null) {
            try {
                conn.close();
                logger.info("Database connection closed");
            } catch (SQLException e) {
                logger.warn("Error closing database connection", e);
            }
        }
        
        connection.set(null);
        connected.set(false);
    }

    /**
     * 获取当前连接
     * 
     * @return 数据库连接，如果未连接则返回null
     */
    public Connection getConnection() {
        return connection.get();
    }

    /**
     * 检查是否已连接
     * 
     * @return true如果已连接
     */
    public boolean isConnected() {
        return connected.get() && isConnectionValid();
    }

    /**
     * 检查连接是否有效
     * 
     * @return true如果连接有效
     */
    private boolean isConnectionValid() {
        Connection conn = connection.get();
        if (conn == null) {
            return false;
        }
        
        try {
            return !conn.isClosed() && conn.isValid(5);
        } catch (SQLException e) {
            logger.warn("Connection validation failed", e);
            return false;
        }
    }

    /**
     * 启动自动重连
     * 在连接断开时，每30秒尝试重连一次
     */
    private void startAutoReconnect() {
        if (reconnecting.get()) {
            return; // 已经在重连中
        }
        
        reconnecting.set(true);
        logger.info("Starting auto-reconnect with interval: {} seconds", config.getReconnectInterval());
        
        if (reconnectExecutor == null || reconnectExecutor.isShutdown()) {
            reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "db-reconnect");
                t.setDaemon(true);
                return t;
            });
        }
        
        reconnectExecutor.scheduleWithFixedDelay(() -> {
            if (!isConnected()) {
                logger.info("Attempting to reconnect to database...");
                try {
                    connect();
                    logger.info("Reconnection successful");
                    stopAutoReconnect();
                } catch (SQLException e) {
                    logger.warn("Reconnection attempt failed, will retry in {} seconds", 
                        config.getReconnectInterval());
                }
            }
        }, config.getReconnectInterval(), config.getReconnectInterval(), TimeUnit.SECONDS);
    }

    /**
     * 停止自动重连
     */
    private void stopAutoReconnect() {
        reconnecting.set(false);
        if (reconnectExecutor != null && !reconnectExecutor.isShutdown()) {
            reconnectExecutor.shutdown();
            logger.info("Auto-reconnect stopped");
        }
    }

    /**
     * 启动健康检查
     * 定期检查连接状态，如果断开则触发重连
     */
    private void startHealthCheck() {
        if (healthCheckExecutor == null || healthCheckExecutor.isShutdown()) {
            healthCheckExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "db-health-check");
                t.setDaemon(true);
                return t;
            });
        }
        
        healthCheckExecutor.scheduleWithFixedDelay(() -> {
            if (!isConnectionValid()) {
                logger.warn("Database connection is not valid, triggering reconnect");
                connected.set(false);
                startAutoReconnect();
            }
        }, 10, 10, TimeUnit.SECONDS);
        
        logger.info("Health check started");
    }

    /**
     * 停止健康检查
     */
    private void stopHealthCheck() {
        if (healthCheckExecutor != null && !healthCheckExecutor.isShutdown()) {
            healthCheckExecutor.shutdown();
            logger.info("Health check stopped");
        }
    }

    /**
     * 检查是否正在重连
     * 
     * @return true如果正在重连
     */
    public boolean isReconnecting() {
        return reconnecting.get();
    }
}
