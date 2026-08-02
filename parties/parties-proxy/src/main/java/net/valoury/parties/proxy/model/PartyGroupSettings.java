package net.valoury.parties.proxy.model;

import net.valoury.shared.model.GroupJoinPolicy;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

public record PartyGroupSettings(
        @NonNull UUID partyId,
        @NonNull GroupJoinPolicy joinPolicy
) {

    public PartyGroupSettings(@NonNull UUID partyId) {
        this(partyId, GroupJoinPolicy.PRIVATE);
    }
}
