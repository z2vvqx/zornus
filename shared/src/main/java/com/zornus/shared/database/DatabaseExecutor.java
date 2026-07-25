package com.zornus.shared.database;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class DatabaseExecutor {

    private final ThreadPoolExecutor executor;

    public DatabaseExecutor(String threadNamePrefix, int threadCount) {
        if (threadNamePrefix == null || threadNamePrefix.isBlank()) {
            throw new IllegalArgumentException("Database thread name prefix cannot be blank");
        }
        if (threadCount <= 0) {
            throw new IllegalArgumentException("Database thread count must be positive");
        }

        this.executor = new ThreadPoolExecutor(
                threadCount,
                threadCount,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(DatabaseDefaults.EXECUTOR_QUEUE_CAPACITY),
                Thread.ofPlatform().name(threadNamePrefix, 0).factory(),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    public <T> CompletableFuture<T> supply(Supplier<T> task) {
        Objects.requireNonNull(task, "Database task cannot be null");

        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    future.complete(task.get());
                } catch (Throwable exception) {
                    future.completeExceptionally(exception);
                }
            });
        } catch (RejectedExecutionException exception) {
            String message = executor.isShutdown()
                    ? "Database executor is shut down"
                    : "Database executor queue is full";
            future.completeExceptionally(new RejectedExecutionException(message, exception));
        }
        return future;
    }

    public CompletableFuture<Void> run(Runnable task) {
        Objects.requireNonNull(task, "Database task cannot be null");
        return supply(() -> {
            task.run();
            return null;
        });
    }

    public void shutdown() {
        executor.shutdown();
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return executor.awaitTermination(timeout, unit);
    }

    public void shutdownNow() {
        executor.shutdownNow();
    }
}
