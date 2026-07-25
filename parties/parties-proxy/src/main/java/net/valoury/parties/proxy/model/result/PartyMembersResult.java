package net.valoury.parties.proxy.model.result;

import net.valoury.parties.proxy.model.Party;
import net.valoury.shared.utilities.PaginationResult;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

public sealed interface PartyMembersResult {
    record Found(
            @NonNull PaginationResult<UUID> pagination,
            @NonNull Party party
    ) implements PartyMembersResult {
    }

    record Empty(@NonNull Party party) implements PartyMembersResult {
    }

    record InvalidPage(
            @NonNull PaginationResult<UUID> pagination,
            @NonNull Party party
    ) implements PartyMembersResult {
    }

    record NotInParty() implements PartyMembersResult {
    }
}
