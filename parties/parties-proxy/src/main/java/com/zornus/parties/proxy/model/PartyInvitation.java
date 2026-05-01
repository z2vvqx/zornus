package com.zornus.parties.proxy.model;

import com.zornus.parties.proxy.PartyProxyConstants;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.UUID;

public record PartyInvitation(
        @NonNull UUID partyId,
        @NonNull String partyName,
        @NonNull UUID senderId,
        @NonNull String senderName,
        @NonNull UUID targetId,
        @NonNull String targetName,
        @NonNull Instant timestamp
) {

    public PartyInvitation(@NonNull UUID partyId, @NonNull String partyName, @NonNull UUID senderId,
                           @NonNull String senderName, @NonNull UUID targetId, @NonNull String targetName) {
        this(partyId, partyName, senderId, senderName, targetId, targetName, Instant.now());
    }

    public boolean isExpired() {
        return timestamp.plus(PartyProxyConstants.INVITATION_EXPIRY).isBefore(Instant.now());
    }
}
