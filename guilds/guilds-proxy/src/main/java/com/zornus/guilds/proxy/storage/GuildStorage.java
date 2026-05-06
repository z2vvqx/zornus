package com.zornus.guilds.proxy.storage;

import com.zornus.friends.proxy.model.PlayerRecord;
import com.zornus.guilds.proxy.model.Guild;
import com.zornus.guilds.proxy.model.GuildInvitation;
import com.zornus.guilds.proxy.model.GuildSettings;
import com.zornus.guilds.proxy.model.PendingConfirmation;
import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface GuildStorage {

    // Compound operations
    CompletableFuture<CreateGuildOutcome> tryCreateGuild(@NonNull UUID leaderId, @NonNull String guildName, @NonNull String guildTag, @NonNull String guildColor);
    CompletableFuture<DisbandGuildOutcome> tryDisbandGuild(@NonNull UUID guildId, @NonNull UUID leaderId);
    CompletableFuture<RemoveMemberOutcome> tryRemoveMember(@NonNull UUID guildId, @NonNull UUID memberId, @NonNull UUID requesterId);
    CompletableFuture<SendInvitationOutcome> trySendInvitation(@NonNull UUID guildId, @NonNull UUID senderId, @NonNull UUID targetId, boolean isFriend);
    CompletableFuture<AcceptInvitationOutcome> tryAcceptInvitation(@NonNull UUID guildId, @NonNull UUID senderId, @NonNull UUID targetId);
    CompletableFuture<TransferLeadershipOutcome> tryTransferLeadership(@NonNull UUID guildId, @NonNull UUID newLeaderId, @NonNull UUID oldLeaderId);
    CompletableFuture<RenameGuildOutcome> tryRenameGuild(@NonNull UUID guildId, @NonNull UUID leaderId, @NonNull String newName);

    // Single-query operations
    CompletableFuture<Optional<Guild>> fetchGuild(@NonNull UUID guildId);
    CompletableFuture<Optional<Guild>> fetchGuildByName(@NonNull String name);
    CompletableFuture<Optional<Guild>> getPlayerGuild(@NonNull UUID playerId);
    CompletableFuture<Boolean> isInGuild(@NonNull UUID playerId);

    CompletableFuture<List<GuildInvitation>> fetchIncomingInvitations(@NonNull UUID playerId);
    CompletableFuture<List<GuildInvitation>> fetchOutgoingInvitations(@NonNull UUID playerId);
    CompletableFuture<Optional<GuildInvitation>> findInvitationByGuildName(@NonNull UUID inviteeId, @NonNull String guildName);
    CompletableFuture<Boolean> removePendingInvitation(@NonNull UUID guildId, @NonNull UUID senderId, @NonNull UUID targetId);

    CompletableFuture<Optional<GuildSettings>> fetchSettings(@NonNull UUID playerId);
    CompletableFuture<Map<UUID, GuildSettings>> fetchSettingsForMembers(@NonNull Collection<UUID> memberIds);
    CompletableFuture<Void> updateInvitePrivacy(@NonNull UUID playerId, @NonNull String value);
    CompletableFuture<Void> updateShowChat(@NonNull UUID playerId, boolean value);

    CompletableFuture<Void> upsertPlayer(@NonNull UUID playerId, @NonNull String username);
    CompletableFuture<Optional<PlayerRecord>> fetchPlayerByUsername(@NonNull String username);
    CompletableFuture<Map<UUID, PlayerRecord>> fetchPlayersByUuids(@NonNull Collection<UUID> playerIds);

    CompletableFuture<ConfirmationOutcome> setPendingConfirmation(@NonNull PendingConfirmation confirmation);
    CompletableFuture<Void> removePendingConfirmation(@NonNull UUID playerId);
    CompletableFuture<Optional<PendingConfirmation>> fetchPendingConfirmation(@NonNull UUID playerId);

    CompletableFuture<Boolean> recordInvitationCooldown(@NonNull UUID playerA, @NonNull UUID playerB, @NonNull Instant now);
    CompletableFuture<Optional<Instant>> fetchInvitationCooldown(@NonNull UUID playerA, @NonNull UUID playerB);

    CompletableFuture<Void> cleanupExpiredInvitations(@NonNull Instant now, @NonNull Duration expiry);
    CompletableFuture<Void> cleanupExpiredConfirmations(@NonNull Instant now, @NonNull Duration expiry);
    CompletableFuture<Void> cleanupExpiredCooldowns(@NonNull Instant now, @NonNull Duration expiry);

    void close();
}
