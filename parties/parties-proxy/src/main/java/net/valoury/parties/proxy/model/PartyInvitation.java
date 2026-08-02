package net.valoury.parties.proxy.model;

import net.valoury.parties.proxy.PartyProxyConstants;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record PartyInvitation(
        @NonNull Optional<UUID> partyId,
        @NonNull UUID senderId,
        @NonNull UUID targetId,
        @NonNull Instant timestamp
) {
    public PartyInvitation {
        partyId = Objects.requireNonNull(partyId, "partyId");
        senderId = Objects.requireNonNull(senderId, "senderId");
        targetId = Objects.requireNonNull(targetId, "targetId");
        timestamp = Objects.requireNonNull(timestamp, "timestamp");
    }

    public boolean isExpired() {
        return timestamp.plus(PartyProxyConstants.INVITATION_EXPIRY).isBefore(Instant.now());
    }
}
