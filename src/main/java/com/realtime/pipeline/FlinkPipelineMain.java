package com.realtime.pipeline;

import com.realtime.pipeline.config.PipelineConfig;
import com.realtime.pipeline.flink.FlinkEnvironmentConfigurator;
import com.realtime.pipeline.flink.checkpoint.CheckpointListener;
import com.realtime.pipeline.flink.checkpoint.MetricsCheckpointListener;
import com.realtime.pipeline.flink.processor.EventProcessorWithDLQ;
import com.realtime.pipeline.flink.sink.CsvFileSink;
import com.realtime.pipeline.flink.sink.JsonFileSink;
import com.realtime.pipeline.flink.sink.ParquetFileSink;
import com.realtime.pipeline.flink.source.DataHubSource;
import com.realtime.pipeline.model.ChangeEvent;
import com.realtime.pipeline.model.ProcessedEvent;
import com.realtime.pipeline.monitoring.AlertManager;
import com.realtime.pipeline.monitoring.HealthCheckServer;
import com.realtime.pipeline.monitoring.MetricsReporter;
import com.realtime.pipeline.monitoring.MonitoringService;
import com.realtime.pipeline.util.ConfigLoader;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Flink实时数据管道主程序入口
 * 
 * 职责:
 * 1. 加载配置
 * 2. 初始化所有组件
 * 3. 构建Flink作业DAG
 * 4. 提交作业到Flink集群
 * 
 * 验证需求: 所有需求的集成
 */
public class FlinkPipelineMain {
    private static final Logger logger = LoggerFactory.getLogger(FlinkPipelineMain.class);

    public static void main(String[] args) {
        logger.info("Starting Flink Realtime Data Pipeline...");
        
        try {
            // 1. 加载配置
            PipelineConfig config = loadConfiguration(args);
            logger.info("Configuration loaded successfully");
            
            // 2. 创建Flink执行环境
            StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
            logger.info("Flink execution environment created");
            
            // 3. 配置Flink环境（Checkpoint、状态后端、并行度等）
            FlinkEnvironmentConfigurator configurator = new FlinkEnvironmentConfigurator(
                config.getFlink(),
                config.getHighAvailability()
            );
            configurator.configure(env);
            logger.info("Flink environment configured");
            
            // 4. 初始化监控服务和相关组件
            Object[] monitoringComponents = initializeMonitoring(config);
            MonitoringService monitoringService = (MonitoringService) monitoringComponents[0];
            MetricsReporter metricsReporter = (MetricsReporter) monitoringComponents[1];
            AlertManager alertManager = (AlertManager) monitoringComponents[2];
            HealthCheckServer healthCheckServer = (HealthCheckServer) monitoringComponents[3];
            logger.info("Monitoring components initialized");
            
            // 5. 构建Flink作业DAG
            buildJobDAG(env, config, monitoringService, metricsReporter);
            logger.info("Flink job DAG built successfully");
            
            // 6. 启动告警管理器
            if (alertManager != null) {
                alertManager.start();
                logger.info("Alert manager started");
            }
            
            // 7. 提交作业到Flink集群
            logger.info("Submitting Flink job to cluster...");
            env.execute("Realtime Data Pipeline");
            
            logger.info("Flink job completed");
            
            // 8. 清理资源
            if (alertManager != null) {
                alertManager.shutdown();
            }
            if (healthCheckServer != null) {
                healthCheckServer.stop();
            }
            
        } catch (Exception e) {
            logger.error("Fatal error in Flink Pipeline", e);
            System.exit(1);
        }
    }

    /**
     * 加载配置
     * 支持从命令行参数指定配置文件路径，否则使用默认配置
     * 
     * @param args 命令行参数
     * @return 管道配置
     * @throws Exception 如果配置加载失败
     */
    private static PipelineConfig loadConfiguration(String[] args) throws Exception {
        String configPath = "application.yml";
        
        // 从命令行参数获取配置文件路径
        if (args.length > 0) {
            configPath = args[0];
            logger.info("Using configuration file from command line: {}", configPath);
        } else {
            logger.info("Using default configuration file: {}", configPath);
        }
        
        // 加载配置（支持环境变量覆盖）
        PipelineConfig config = ConfigLoader.loadConfig(configPath);
        
        // 验证配置
        config.validate();
        
        logger.info("Configuration validated successfully");
        logger.info("Pipeline configuration: database={}, datahub={}, output={}",
            config.getDatabase().getHost(),
            config.getDatahub().getProject(),
            config.getOutput().getPath());
        
        return config;
    }

    /**
     * 初始化监控服务和相关组件
     * 
     * @param config 管道配置
     * @return 监控组件数组 [MonitoringService, MetricsReporter, AlertManager, HealthCheckServer]
     */
    private static Object[] initializeMonitoring(PipelineConfig config) {
        // 1. 创建监控服务
        MonitoringService monitoringService = new MonitoringService(config.getMonitoring());
        
        if (config.getMonitoring().isEnabled()) {
            logger.info("Monitoring enabled with interval: {}s", 
                config.getMonitoring().getMetricsInterval());
        } else {
            logger.info("Monitoring is disabled");
        }
        
        // 2. 创建指标报告器
        MetricsReporter metricsReporter = new MetricsReporter(monitoringService);
        logger.info("Metrics reporter created");
        
        // 3. 创建告警管理器
        AlertManager alertManager = null;
        if (config.getMonitoring().isEnabled()) {
            alertManager = new AlertManager(config.getMonitoring(), metricsReporter);
            logger.info("Alert manager initialized with {} alert rules", 
                alertManager.getAllRules().size());
        }
        
        // 4. 创建健康检查服务器
        HealthCheckServer healthCheckServer = null;
        if (config.getMonitoring().isEnabled()) {
            try {
                healthCheckServer = new HealthCheckServer(
                    config.getMonitoring(),
                    metricsReporter,
                    alertManager
                );
                healthCheckServer.start();
                logger.info("Health check server started on port {}", 
                    config.getMonitoring().getHealthCheckPort());
            } catch (Exception e) {
                logger.warn("Failed to start health check server: {}", e.getMessage());
            }
        }
        
        return new Object[] { monitoringService, metricsReporter, alertManager, healthCheckServer };
    }

    /**
     * 构建Flink作业DAG
     * 
     * 数据流:
     * DataHub Source -> Event Processor (with DLQ) -> File Sink (JSON/Parquet/CSV)
     *                                                  |
     *                                                  +-> Checkpoint Listener (metrics)
     * 
     * @param env Flink执行环境
     * @param config 管道配置
     * @param monitoringService 监控服务
     * @param metricsReporter 指标报告器
     */
    private static void buildJobDAG(
            StreamExecutionEnvironment env,
            PipelineConfig config,
            MonitoringService monitoringService,
            MetricsReporter metricsReporter) {
        
        logger.info("Building Flink job DAG...");
        
        // 1. 创建DataHub数据源
        DataHubSource dataHubSource = new DataHubSource(config.getDatahub());
        DataStream<ChangeEvent> sourceStream = env
            .addSource(dataHubSource)
            .name("DataHub Source")
            .uid("datahub-source");
        
        logger.info("DataHub source added to DAG");
        
        // 2. 添加事件处理器（带死信队列）
        String dlqPath = config.getOutput().getPath() + "/dlq";
        EventProcessorWithDLQ processor = new EventProcessorWithDLQ(dlqPath);
        
        DataStream<ProcessedEvent> processedStream = sourceStream
            .keyBy(event -> event.getTable())  // 按表名分区，保持同一表的事件顺序
            .map(processor)
            .name("Event Processor")
            .uid("event-processor")
            .filter(event -> event != null);  // 过滤掉处理失败的null事件
        
        logger.info("Event processor added to DAG with DLQ path: {}", dlqPath);
        
        // 3. 添加Checkpoint监听器（用于指标收集）
        CheckpointListener checkpointListener = new CheckpointListener();
        MetricsCheckpointListener<ProcessedEvent> metricsListener = 
            new MetricsCheckpointListener<>(checkpointListener);
        
        DataStream<ProcessedEvent> monitoredStream = processedStream
            .map(metricsListener)
            .name("Checkpoint Metrics Listener")
            .uid("checkpoint-metrics");
        
        logger.info("Checkpoint metrics listener added to DAG");
        
        // 4. 添加文件输出Sink（根据配置选择格式）
        String outputFormat = config.getOutput().getFormat().toLowerCase();
        
        switch (outputFormat) {
            case "json":
                JsonFileSink jsonSink = new JsonFileSink(config.getOutput());
                monitoredStream
                    .addSink(jsonSink.createSink())
                    .name("JSON File Sink")
                    .uid("json-sink");
                logger.info("JSON file sink added to DAG, output path: {}", config.getOutput().getPath());
                break;
                
            case "parquet":
                ParquetFileSink parquetSink = new ParquetFileSink(config.getOutput());
                monitoredStream
                    .addSink(parquetSink.createSink())
                    .name("Parquet File Sink")
                    .uid("parquet-sink");
                logger.info("Parquet file sink added to DAG, output path: {}", config.getOutput().getPath());
                break;
                
            case "csv":
                CsvFileSink csvSink = new CsvFileSink(config.getOutput());
                monitoredStream
                    .addSink(csvSink.createSink())
                    .name("CSV File Sink")
                    .uid("csv-sink");
                logger.info("CSV file sink added to DAG, output path: {}", config.getOutput().getPath());
                break;
                
            default:
                throw new IllegalArgumentException("Unsupported output format: " + outputFormat + 
                    ". Supported formats: json, parquet, csv");
        }
        
        logger.info("Flink job DAG construction completed");
        logger.info("Job topology: DataHub Source -> Event Processor -> {} File Sink", 
            outputFormat.toUpperCase());
    }
}
