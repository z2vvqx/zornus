package com.zornus.parties.proxy.model;

import com.zornus.parties.proxy.PartyProxyConstants;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.UUID;

public record PartyInvitation(
        @NonNull UUID partyId,
        @NonNull UUID senderId,
        @NonNull UUID targetId,
        @NonNull Instant timestamp
) {

    public PartyInvitation(@NonNull UUID partyId, @NonNull UUID senderId, @NonNull UUID targetId) {
        this(partyId, senderId, targetId, Instant.now());
    }

    public boolean isExpired() {
        return timestamp.plus(PartyProxyConstants.INVITATION_EXPIRY).isBefore(Instant.now());
    }
}
