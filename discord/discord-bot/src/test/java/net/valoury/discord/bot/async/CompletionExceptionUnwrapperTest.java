package net.valoury.discord.bot.async;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertSame;

class CompletionExceptionUnwrapperTest {
    @Test
    void unwrapsNestedCompletionAndExecutionFailures() {
        IllegalStateException cause = new IllegalStateException("failure");
        CompletionException wrapped = new CompletionException(new ExecutionException(cause));

        assertSame(cause, CompletionExceptionUnwrapper.unwrap(wrapped));
        assertSame(cause, CompletionExceptionUnwrapper.unwrap(cause));
    }
}
