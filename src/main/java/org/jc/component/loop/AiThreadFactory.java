package org.jc.component.loop;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

public class AiThreadFactory implements ThreadFactory {
    private final AtomicLong threadNumber = new AtomicLong(1);

    private final String namePrefix;

    private final boolean daemon;

    private static final ThreadGroup threadGroup = new ThreadGroup("ai");

    private AiThreadFactory(String namePrefix, boolean daemon) {
        this.namePrefix = namePrefix;
        this.daemon = daemon;
    }

    public static ThreadFactory create(String namePrefix, boolean daemon) {
        return new AiThreadFactory(namePrefix, daemon);
    }

    public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(threadGroup
                , runnable
                , threadGroup.getName() + "-" + namePrefix + "-" + threadNumber.getAndIncrement());
        thread.setDaemon(daemon);
        // 默认常规优先级
        //Thread.NORM_PRIORITY 等价数值：```
        //- MIN_PRIORITY = 1
        //- NORM_PRIORITY = 5
        //- MAX_PRIORITY = 10
        if (thread.getPriority() != Thread.NORM_PRIORITY) {
            // 强制重置为标准中等优先级
            //作用：统一线程调度优先级，消除高 / 低优先级带来的调度倾斜，保证公平调度、避免饥饿、统一性能基线。
            //注意：
            //高优先级线程不一定先执行，依赖 OS 内核实现；
            //生产不要依赖优先级做业务时序控制，不可靠；
            //仅用于线程池资源规整、环境复位规范动作
            thread.setPriority(Thread.NORM_PRIORITY);
        }
        return thread;
    }
}
