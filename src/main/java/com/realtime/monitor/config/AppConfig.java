package com.realtime.monitor.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import lombok.Getter;

/**
 * 应用配置
 */
@Configuration
@Getter
public class AppConfig {
    
    @Value("${flink.rest.url:http://localhost:8081}")
    private String flinkRestUrl;
    
    @Value("${output.path:./output/cdc}")
    private String outputPath;

    /** Flink 集群上的输出路径（TaskManager 视角） */
    @Value("${flink.output.path:/opt/flink/output/cdc}")
    private String flinkOutputPath;
    
    @Value("${config.dir:/app/config}")
    private String configDir;
    
    @Value("${oracle.container:oracle11g}")
    private String oracleContainer;
    

}
