import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * 内存加压工具：通过 DirectByteBuffer 申请堆外内存，抬高进程 RSS，
 * 用于触发容器 cgroup OOMKilled，验证 Kubernetes Pod 自愈。
 *
 * 用法（在 Pod 内）:
 *   java -Xmx64m -XX:MaxDirectMemorySize=8g -cp /tmp MemStress 1800
 * 参数为目标申请量（MB）。达到 container memory limit 前通常被 OOM killer 杀掉。
 */
public class MemStress {
    public static void main(String[] args) throws Exception {
        int targetMb = args.length > 0 ? Integer.parseInt(args[0]) : 2048;
        int chunkMb = args.length > 1 ? Integer.parseInt(args[1]) : 64;
        System.out.println("MemStress(direct) start: target=" + targetMb + "MB chunk=" + chunkMb + "MB");
        System.out.flush();

        List<ByteBuffer> held = new ArrayList<>();
        int allocated = 0;
        while (allocated < targetMb) {
            int next = Math.min(chunkMb, targetMb - allocated);
            ByteBuffer buf = ByteBuffer.allocateDirect(next * 1024 * 1024);
            // 触摸每一页，确保真正占用物理内存
            for (int i = 0; i < buf.capacity(); i += 4096) {
                buf.put(i, (byte) 1);
            }
            held.add(buf);
            allocated += next;
            System.out.println("allocated_direct=" + allocated + "MB  buffers=" + held.size());
            System.out.flush();
            Thread.sleep(150);
        }
        System.out.println("Reached target without cgroup OOM, holding...");
        System.out.flush();
        Thread.sleep(Long.MAX_VALUE);
    }
}
