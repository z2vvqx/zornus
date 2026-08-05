package net.valoury.parties.proxy.service;

import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyConnectionResultTest {

    @Test
    void acceptsOnlySuccessfulConnectionRequests() {
        assertTrue(PartyService.connectionRequestSucceeded(
                new StubConnectionResult(ConnectionRequestBuilder.Status.SUCCESS)));
        assertFalse(PartyService.connectionRequestSucceeded(
                new StubConnectionResult(ConnectionRequestBuilder.Status.SERVER_DISCONNECTED)));
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
