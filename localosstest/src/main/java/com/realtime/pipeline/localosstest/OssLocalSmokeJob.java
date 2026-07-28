package com.realtime.pipeline.localosstest;

import java.time.Duration;

import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.configuration.MemorySize;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.OnCheckpointRollingPolicy;
import org.apache.flink.streaming.api.functions.source.SourceFunction;

/**
 * 本地 Flink → OSS 冒烟作业。
 *
 * <p>持续产生少量文本行，经 FileSink 写到 {@code --output}（通常为 {@code oss://...}），
 * 并开启 checkpoint（落在集群 conf 的 {@code state.checkpoints.dir}，也应指向 OSS）。
 *
 * <pre>
 *   flink run -c com.realtime.pipeline.localosstest.OssLocalSmokeJob localosstest-smoke.jar \
 *     --output oss://bucket/prefix/out \
 *     --checkpoint-interval-ms 10000
 * </pre>
 */
public final class OssLocalSmokeJob {

    private OssLocalSmokeJob() {}

    public static void main(String[] args) throws Exception {
        String output = null;
        long checkpointIntervalMs = 10_000L;
        long sleepMs = 500L;
        boolean rollOnCheckpoint = true;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--output":
                    output = requireValue(args, ++i, "--output");
                    break;
                case "--checkpoint-interval-ms":
                    checkpointIntervalMs = Long.parseLong(requireValue(args, ++i, "--checkpoint-interval-ms"));
                    break;
                case "--sleep-ms":
                    sleepMs = Long.parseLong(requireValue(args, ++i, "--sleep-ms"));
                    break;
                case "--roll-on-checkpoint":
                    rollOnCheckpoint = Boolean.parseBoolean(requireValue(args, ++i, "--roll-on-checkpoint"));
                    break;
                case "--help":
                case "-h":
                    printHelp();
                    return;
                default:
                    throw new IllegalArgumentException("Unknown arg: " + args[i]);
            }
        }
        if (output == null || output.isBlank()) {
            throw new IllegalArgumentException("Missing required --output (e.g. oss://bucket/prefix/out)");
        }

        final long sleep = sleepMs;
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        env.enableCheckpointing(checkpointIntervalMs);
        CheckpointConfig ck = env.getCheckpointConfig();
        ck.setMinPauseBetweenCheckpoints(Math.max(1000L, checkpointIntervalMs / 2));
        ck.setCheckpointTimeout(Math.max(60_000L, checkpointIntervalMs * 6));
        ck.setTolerableCheckpointFailureNumber(3);

        env.addSource(new SourceFunction<String>() {
            private volatile boolean running = true;

            @Override
            public void run(SourceContext<String> ctx) throws Exception {
                long seq = 0;
                while (running) {
                    String line = "ts=" + System.currentTimeMillis()
                            + ",seq=" + seq++
                            + ",host=" + hostname();
                    synchronized (ctx.getCheckpointLock()) {
                        ctx.collect(line);
                    }
                    Thread.sleep(sleep);
                }
            }

            @Override
            public void cancel() {
                running = false;
            }
        }).name("localosstest-source").uid("localosstest-source")
                .sinkTo(buildSink(output, rollOnCheckpoint))
                .name("oss-file-sink").uid("oss-file-sink");

        System.out.println("=== localosstest OssLocalSmokeJob ===");
        System.out.println("output                = " + output);
        System.out.println("checkpointIntervalMs  = " + checkpointIntervalMs);
        System.out.println("sleepMs               = " + sleepMs);
        System.out.println("rollOnCheckpoint      = " + rollOnCheckpoint);

        env.execute("localosstest-oss-smoke");
    }

    private static FileSink<String> buildSink(String output, boolean rollOnCheckpoint) {
        FileSink.DefaultRowFormatBuilder<String> builder = FileSink
                .forRowFormat(new Path(output), new SimpleStringEncoder<>("UTF-8"));
        if (rollOnCheckpoint) {
            // 与 CDC FileSink 一致：依赖成功 checkpoint 才会落盘可见 part 文件
            return builder.withRollingPolicy(OnCheckpointRollingPolicy.build()).build();
        }
        return builder.withRollingPolicy(
                        DefaultRollingPolicy.builder()
                                .withRolloverInterval(Duration.ofSeconds(30))
                                .withInactivityInterval(Duration.ofSeconds(15))
                                .withMaxPartSize(MemorySize.ofMebiBytes(8))
                                .build())
                .build();
    }

    private static String requireValue(String[] args, int idx, String flag) {
        if (idx >= args.length) {
            throw new IllegalArgumentException("Missing value for " + flag);
        }
        return args[idx];
    }

    private static String hostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static void printHelp() {
        System.out.println("Usage: OssLocalSmokeJob --output oss://bucket/prefix/out [options]");
        System.out.println("  --checkpoint-interval-ms  N   default 10000");
        System.out.println("  --sleep-ms                N   default 500");
        System.out.println("  --roll-on-checkpoint true|false  default true");
    }
}
