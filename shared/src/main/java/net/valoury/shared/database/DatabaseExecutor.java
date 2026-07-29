package net.valoury.shared.database;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class DatabaseExecutor {

    private final ThreadPoolExecutor executor;
    private final ThreadLocal<Boolean> executingTask = ThreadLocal.withInitial(() -> false);
    private final AtomicBoolean acceptingExternalSubmissions = new AtomicBoolean(true);
    private final AtomicInteger outstandingTasks = new AtomicInteger();

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
        if (!reserveTask()) {
            future.completeExceptionally(
                    new RejectedExecutionException("Database executor is shut down")
            );
            return future;
        }
        try {
            executor.execute(() -> {
                executingTask.set(true);
                try {
                    future.complete(task.get());
                } catch (Throwable exception) {
                    future.completeExceptionally(exception);
                } finally {
                    executingTask.remove();
                    releaseTask();
                }
            });
        } catch (RejectedExecutionException exception) {
            releaseTask();
            future.completeExceptionally(new RejectedExecutionException(
                    isShutdownRequested()
                            ? "Database executor is shut down"
                            : "Database executor queue is full",
                    exception
            ));
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
        acceptingExternalSubmissions.set(false);
        shutdownWhenDrained();
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return executor.awaitTermination(timeout, unit);
    }

    public void shutdownNow() {
        acceptingExternalSubmissions.set(false);
        executor.shutdownNow();
    }

    private boolean reserveTask() {
        if (!acceptingExternalSubmissions.get() && !executingTask.get()) {
            return false;
        }
        if (executor.isShutdown()) {
            return false;
        }

        outstandingTasks.incrementAndGet();
        if (executor.isShutdown()) {
            releaseTask();
            return false;
        }
        return true;
    }

    private void releaseTask() {
        if (outstandingTasks.decrementAndGet() == 0) {
            shutdownWhenDrained();
        }
    }

    private void shutdownWhenDrained() {
        if (!acceptingExternalSubmissions.get() && outstandingTasks.get() == 0) {
            executor.shutdown();
        }
    }

    private boolean isShutdownRequested() {
        return !acceptingExternalSubmissions.get();
    }
}
