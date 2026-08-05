package net.valoury.friends.proxy.service;

import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.valoury.friends.proxy.model.result.JumpToFriendResult;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class FriendConnectionResultTest {

    @Test
    void reportsOnlySuccessfulConnectionsAsCompletedJumps() {
        assertInstanceOf(
                JumpToFriendResult.Jumped.class,
                FriendService.jumpResultForConnection(
                        new StubConnectionResult(ConnectionRequestBuilder.Status.SUCCESS))
        );
        assertInstanceOf(
                JumpToFriendResult.JumpFailed.class,
                FriendService.jumpResultForConnection(
                        new StubConnectionResult(ConnectionRequestBuilder.Status.CONNECTION_CANCELLED))
        );
    }

    private record StubConnectionResult(
            ConnectionRequestBuilder.Status status
    ) implements ConnectionRequestBuilder.Result {

        @Override
        public ConnectionRequestBuilder.Status getStatus() {
            return status;
        }

        @Override
        public Optional<Component> getReasonComponent() {
            return Optional.empty();
        }

        @Override
        public RegisteredServer getAttemptedConnection() {
            return null;
        }
    }
}
