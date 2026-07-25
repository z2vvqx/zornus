package com.zornus.shared.database;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class DatabaseExecutorFactory {

    public static ExecutorService createBoundedExecutor(
            String threadNamePrefix,
            int threadCount
    ) {
        if (threadNamePrefix == null || threadNamePrefix.isBlank()) {
            throw new IllegalArgumentException("Database thread name prefix cannot be blank");
        }
        if (threadCount <= 0) {
            throw new IllegalArgumentException("Database thread count must be positive");
        }

        return new ThreadPoolExecutor(
                threadCount,
                threadCount,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(DatabaseDefaults.EXECUTOR_QUEUE_CAPACITY),
                Thread.ofPlatform().name(threadNamePrefix, 0).factory(),
                (task, executor) -> {
                    if (executor.isShutdown()) {
                        throw new RejectedExecutionException("Database executor is shut down");
                    }
                    task.run();
                }
        );
    }

    private DatabaseExecutorFactory() {
    }
}
