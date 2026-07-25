package com.zornus.parties.proxy.model.result;

import com.zornus.parties.proxy.model.Party;
import com.zornus.shared.utilities.PaginationResult;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

public sealed interface PartyMembersResult {
    record Found(
            @NonNull PaginationResult<UUID> pagination,
            @NonNull Party party
    ) implements PartyMembersResult {}

    record Empty(@NonNull Party party) implements PartyMembersResult {}

    record InvalidPage(
            @NonNull PaginationResult<UUID> pagination,
            @NonNull Party party
    ) implements PartyMembersResult {}

    record NotInParty() implements PartyMembersResult {}
}
