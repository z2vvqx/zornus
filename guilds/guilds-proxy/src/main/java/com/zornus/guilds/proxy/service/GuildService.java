package com.zornus.guilds.proxy.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.zornus.shared.model.PlayerRecord;
import com.zornus.friends.proxy.service.FriendService;
import com.zornus.guilds.proxy.GuildProxyConstants;
import com.zornus.guilds.proxy.model.*;
import com.zornus.guilds.proxy.model.result.GuildInfoResult;
import com.zornus.guilds.proxy.model.result.GuildListResult;
import com.zornus.guilds.proxy.model.result.GuildRequestsResult;
import com.zornus.guilds.proxy.storage.*;
import com.zornus.shared.SharedConstants;
import com.zornus.shared.utilities.PaginationResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class GuildService implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuildService.class);

    private final @NonNull GuildStorage storage;
    private final @NonNull ProxyServer proxyServer;
    private final @NonNull GuildNotificationService notificationService;
    private final @Nullable FriendService friendService;

    public GuildService(@NonNull GuildStorage storage, @NonNull ProxyServer proxyServer, @Nullable FriendService friendService) {
        this.storage = storage;
        this.proxyServer = proxyServer;
        this.friendService = friendService;
        this.notificationService = new GuildNotificationService(storage, proxyServer);
    }

    @Override
    public void close() {
        storage.close();
    }

    public @NonNull GuildNotificationService getNotificationService() {
        return notificationService;
    }

    public @NonNull CompletableFuture<GuildResult> createGuild(@NonNull Player sender, @NonNull String guildName, @NonNull String guildTag, @NonNull String guildColor) {
        UUID senderId = sender.getUniqueId();

        if (!isValidGuildName(guildName)) {
            return CompletableFuture.completedFuture(GuildResult.INVALID_GUILD_NAME);
        }

        if (!isValidGuildTag(guildTag)) {
            return CompletableFuture.completedFuture(GuildResult.INVALID_GUILD_TAG);
        }

        return storage.tryCreateGuild(senderId, guildName, guildTag, guildColor)
                .thenApply(outcome -> switch (outcome) {
                    case CreateGuildOutcome.Created created -> GuildResult.GUILD_CREATED;
                    case CreateGuildOutcome.AlreadyInGuild alreadyInGuild -> GuildResult.ALREADY_IN_GUILD;
                    case CreateGuildOutcome.GuildNameAlreadyExists ignored -> GuildResult.NAME_ALREADY_EXISTS;
                });
    }

    private boolean isValidGuildName(String name) {
        return name != null && name.length() >= 3 && name.length() <= 24 && name.matches("^[a-zA-Z0-9_]+$");
    }

    private boolean isValidGuildTag(String tag) {
        return tag != null && tag.length() >= 2 && tag.length() <= 5 && tag.matches("^[a-zA-Z0-9_]+$");
    }

    public @NonNull CompletableFuture<GuildResult> disbandGuild(@NonNull Player sender, boolean isConfirming) {
        UUID senderId = sender.getUniqueId();
        return storage.getPlayerGuild(senderId)
                .thenCompose(guildOptional -> {
                    if (guildOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(GuildResult.NOT_IN_GUILD);
                    }
                    Guild guild = guildOptional.get();
                    if (!guild.isLeader(senderId)) {
                        return CompletableFuture.completedFuture(GuildResult.NOT_LEADER);
                    }
                    return handleDisbandConfirmation(senderId, guild, isConfirming);
                });
    }

    private @NonNull CompletableFuture<GuildResult> handleDisbandConfirmation(@NonNull UUID senderId, @NonNull Guild guild, boolean isConfirming) {
        if (!isConfirming) {
            return setupConfirmation(senderId, ConfirmationType.DISBAND_GUILD, null, null);
        }
        return confirmAndExecute(senderId, ConfirmationType.DISBAND_GUILD, null, null, () -> disbandGuildInternal(guild, senderId));
    }

    private @NonNull CompletableFuture<GuildResult> disbandGuildInternal(@NonNull Guild guild, @NonNull UUID leaderId) {
        return storage.tryDisbandGuild(guild.guildId(), leaderId)
                .thenApply(outcome -> switch (outcome) {
                    case DisbandGuildOutcome.Disbanded disbanded -> {
                        notificationService.notifyGuildDisbanded(guild, leaderId)
                                .exceptionally(throwable -> {
                                    LOGGER.error("Failed to send guild disbanded notification", throwable);
                                    return null;
                                });
                        yield GuildResult.GUILD_DISBANDED;
                    }
                    case DisbandGuildOutcome.GuildNotFound guildNotFound -> GuildResult.GUILD_NOT_FOUND;
                    case DisbandGuildOutcome.NotLeader notLeader -> GuildResult.NOT_LEADER;
                });
    }

    public @NonNull CompletableFuture<GuildResult> sendInvitation(@NonNull Player sender, @Nullable String targetUsername) {
        if (targetUsername == null) {
            return CompletableFuture.completedFuture(GuildResult.PLAYER_NOT_FOUND);
        }

        UUID senderId = sender.getUniqueId();

        return storage.fetchPlayerByUsername(targetUsername)
                .thenCompose(targetOptional -> {
                    if (targetOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(GuildResult.PLAYER_NOT_FOUND);
                    }
                    PlayerRecord targetRecord = targetOptional.get();
                    UUID targetId = targetRecord.playerUuid();
                    String targetPlayerName = targetRecord.username();

                    if (senderId.equals(targetId)) {
                        return CompletableFuture.completedFuture(GuildResult.CANNOT_INVITE_SELF);
                    }

                    return storage.getPlayerGuild(senderId)
                            .thenCompose(guildOptional -> {
                                if (guildOptional.isEmpty()) {
                                    return CompletableFuture.completedFuture(GuildResult.NOT_IN_GUILD);
                                }
                                Guild guild = guildOptional.get();
                                if (!guild.isLeader(senderId)) {
                                    return CompletableFuture.completedFuture(GuildResult.NOT_LEADER);
                                }
                                return executeSendInvitation(sender, targetId, targetPlayerName, guild);
                            });
                });
    }

    private @NonNull CompletableFuture<GuildResult> executeSendInvitation(@NonNull Player sender, @NonNull UUID targetId, @NonNull String targetUsername, @NonNull Guild guild) {
        UUID senderId = sender.getUniqueId();

        if (friendService != null) {
            return friendService.areFriends(senderId, targetId)
                    .thenCompose(isFriend -> executeStorageSendInvitation(sender, targetId, targetUsername, guild, isFriend));
        } else {
            LOGGER.warn("FriendService unavailable; treating invite_privacy='friend' as 'all' for player {}", targetId);
            return executeStorageSendInvitation(sender, targetId, targetUsername, guild, true);
        }
    }

    private @NonNull CompletableFuture<GuildResult> executeStorageSendInvitation(@NonNull Player sender, @NonNull UUID targetId, @NonNull String targetUsername, @NonNull Guild guild, boolean isPreCheckedFriend) {
        UUID senderId = sender.getUniqueId();

        return storage.trySendInvitation(guild.guildId(), senderId, targetId, isPreCheckedFriend)
                .thenApply(outcome -> switch (outcome) {
                    case SendInvitationOutcome.Sent sent -> {
                        notificationService.sendInviteReceived(targetId, sender, guild);
                        notificationService.announceInviteSent(guild, sender, targetUsername);
                        yield GuildResult.INVITATION_SENT;
                    }
                    case SendInvitationOutcome.TargetAlreadyInGuild targetAlreadyInGuild ->
                            GuildResult.TARGET_ALREADY_IN_GUILD;
                    case SendInvitationOutcome.TargetInAnotherGuild targetInAnotherGuild ->
                            GuildResult.TARGET_IN_ANOTHER_GUILD;
                    case SendInvitationOutcome.GuildFull guildFull -> GuildResult.GUILD_FULL;
                    case SendInvitationOutcome.CooldownActive cooldownActive -> GuildResult.INVITATION_COOLDOWN_ACTIVE;
                    case SendInvitationOutcome.SenderLimitReached senderLimitReached ->
                            GuildResult.SENDER_INVITATION_LIMIT_REACHED;
                    case SendInvitationOutcome.ReceiverLimitReached receiverLimitReached ->
                            GuildResult.RECEIVER_INVITATION_LIMIT_REACHED;
                    case SendInvitationOutcome.InvitesDisabled invitesDisabled ->
                            "friend".equals(invitesDisabled.privacy()) ? GuildResult.INVITES_FRIENDS_ONLY : GuildResult.INVITES_DISABLED;
                    case SendInvitationOutcome.AlreadyInvited alreadyInvited -> GuildResult.ALREADY_INVITED;
                    case SendInvitationOutcome.SenderNoLongerLeader senderNoLongerLeader -> GuildResult.NOT_LEADER;
                    case SendInvitationOutcome.GuildNoLongerExists guildNoLongerExists -> GuildResult.GUILD_NOT_FOUND;
                });
    }

    public @NonNull CompletableFuture<GuildResult> acceptInvitation(@NonNull Player sender, @Nullable String guildName) {
        if (guildName == null) {
            return CompletableFuture.completedFuture(GuildResult.GUILD_NOT_FOUND);
        }

        UUID senderId = sender.getUniqueId();

        return storage.isInGuild(senderId)
                .thenCompose(inGuild -> {
                    if (inGuild) {
                        return CompletableFuture.completedFuture(GuildResult.ALREADY_IN_GUILD);
                    }
                    return findAndAcceptInvitationByGuildName(senderId, guildName);
                });
    }

    private @NonNull CompletableFuture<GuildResult> findAndAcceptInvitationByGuildName(@NonNull UUID senderId, @NonNull String guildName) {
        return storage.findInvitationByGuildName(senderId, guildName)
                .thenCompose(invitationOptional -> {
                    if (invitationOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(GuildResult.NO_INVITATION_FOUND);
                    }
                    GuildInvitation invitation = invitationOptional.get();
                    return addMemberToGuild(senderId, invitation);
                });
    }

    private @NonNull CompletableFuture<GuildResult> addMemberToGuild(@NonNull UUID playerId, @NonNull GuildInvitation invitation) {
        UUID guildId = invitation.guildId();
        return storage.tryAcceptInvitation(guildId, invitation.senderId(), playerId)
                .thenCompose(outcome -> switch (outcome) {
                    case AcceptInvitationOutcome.Accepted accepted -> storage.fetchGuild(guildId)
                            .thenApply(guildOptional -> {
                                guildOptional.ifPresent(guild ->
                                        proxyServer.getPlayer(playerId).ifPresent(player ->
                                                notificationService.notifyMemberJoined(guild, player)));
                                return GuildResult.JOINED_GUILD;
                            });
                    case AcceptInvitationOutcome.GuildFull guildFull ->
                            CompletableFuture.completedFuture(GuildResult.GUILD_FULL);
                    case AcceptInvitationOutcome.AlreadyInGuild alreadyInGuild ->
                            CompletableFuture.completedFuture(GuildResult.ALREADY_IN_GUILD);
                    case AcceptInvitationOutcome.InvitationExpired invitationExpired ->
                            CompletableFuture.completedFuture(GuildResult.NO_INVITATION_FOUND);
                    case AcceptInvitationOutcome.InvitationNoLongerValid invitationNoLongerValid ->
                            CompletableFuture.completedFuture(GuildResult.NO_INVITATION_FOUND);
                });
    }

    public @NonNull CompletableFuture<GuildResult> rejectInvitation(@NonNull Player sender, @Nullable String guildName) {
        if (guildName == null) {
            return CompletableFuture.completedFuture(GuildResult.GUILD_NOT_FOUND);
        }

        UUID senderId = sender.getUniqueId();

        return storage.findInvitationByGuildName(senderId, guildName)
                .thenCompose(invitationOptional -> {
                    if (invitationOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(GuildResult.NO_INVITATION_FOUND);
                    }
                    GuildInvitation invitation = invitationOptional.get();
                    return storage.removePendingInvitation(invitation.guildId(), invitation.senderId(), senderId)
                            .thenApply(removed -> removed ? GuildResult.INVITATION_REJECTED : GuildResult.NO_INVITATION_FOUND);
                });
    }

    public @NonNull CompletableFuture<GuildResult> revokeInvitation(@NonNull Player sender, @Nullable String targetUsername) {
        if (targetUsername == null) {
            return CompletableFuture.completedFuture(GuildResult.PLAYER_NOT_FOUND);
        }

        UUID senderId = sender.getUniqueId();

        return storage.getPlayerGuild(senderId)
                .thenCompose(guildOptional -> {
                    if (guildOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(GuildResult.NOT_IN_GUILD);
                    }
                    Guild guild = guildOptional.get();
                    if (!guild.isLeader(senderId)) {
                        return CompletableFuture.completedFuture(GuildResult.NOT_LEADER);
                    }
                    return findAndRevokeInvitation(targetUsername, guild.guildId());
                });
    }

    private @NonNull CompletableFuture<GuildResult> findAndRevokeInvitation(@NonNull String targetUsername, @NonNull UUID guildId) {
        return storage.fetchPlayerByUsername(targetUsername)
                .thenCompose(targetOptional -> {
                    if (targetOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(GuildResult.PLAYER_NOT_FOUND);
                    }
                    UUID targetId = targetOptional.get().playerUuid();

                    // Find invitation by the target for this guild
                    return storage.fetchIncomingInvitations(targetId)
                            .thenCompose(invitations -> {
                                Optional<GuildInvitation> invitationOpt = invitations.stream()
                                        .filter(inv -> inv.guildId().equals(guildId))
                                        .findFirst();

                                if (invitationOpt.isEmpty()) {
                                    return CompletableFuture.completedFuture(GuildResult.NO_INVITATION_FOUND);
                                }
                                GuildInvitation invitation = invitationOpt.get();
                                return storage.removePendingInvitation(invitation.guildId(), invitation.senderId(), targetId)
                                        .thenApply(removed -> removed ? GuildResult.INVITATION_REVOKED : GuildResult.NO_INVITATION_FOUND);
                            });
                });
    }

    public @NonNull CompletableFuture<GuildRequestsResult> getRequestsList(@NonNull UUID playerId, @NonNull String type, int page) {
        CompletableFuture<List<GuildInvitation>> invitationsFuture;
        if ("incoming".equalsIgnoreCase(type)) {
            invitationsFuture = storage.fetchIncomingInvitations(playerId);
        } else if ("outgoing".equalsIgnoreCase(type)) {
            invitationsFuture = storage.fetchOutgoingInvitations(playerId);
        } else {
            return CompletableFuture.completedFuture(
                    new GuildRequestsResult(GuildResult.INVALID_REQUEST_TYPE, PaginationResult.invalidPage(1)));
        }

        return invitationsFuture.thenApply(invitations -> {
            if (invitations.isEmpty()) {
                return new GuildRequestsResult(GuildResult.LIST_EMPTY, PaginationResult.invalidPage(1));
            }

            PaginationResult<GuildInvitation> pagination = PaginationResult.paginate(invitations, page, SharedConstants.ENTRIES_PER_PAGE);
            if (!pagination.isValidPage()) {
                return new GuildRequestsResult(GuildResult.INVALID_PAGE, pagination);
            }
            return new GuildRequestsResult(GuildResult.SUCCESS, pagination);
        });
    }

    public @NonNull CompletableFuture<GuildResult> leaveGuild(@NonNull Player sender) {
        UUID senderId = sender.getUniqueId();

        return storage.getPlayerGuild(senderId)
                .thenCompose(guildOptional -> {
                    if (guildOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(GuildResult.NOT_IN_GUILD);
                    }
                    Guild guild = guildOptional.get();
                    return removePlayerFromGuild(senderId, guild, true)
                            .thenApply(result -> {
                                if (result == GuildResult.LEFT_GUILD || result == GuildResult.LEFT_GUILD_DISBANDED) {
                                    notificationService.notifyMemberLeft(guild, sender.getUsername(), senderId);
                                }
                                return result;
                            });
                });
    }

    public @NonNull CompletableFuture<GuildResult> kickMember(@NonNull Player sender, @Nullable String targetUsername) {
        if (targetUsername == null) {
            return CompletableFuture.completedFuture(GuildResult.PLAYER_NOT_FOUND);
        }

        UUID senderId = sender.getUniqueId();

        return storage.getPlayerGuild(senderId)
                .thenCompose(guildOptional -> {
                    if (guildOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(GuildResult.NOT_IN_GUILD);
                    }
                    Guild guild = guildOptional.get();
                    if (!guild.isLeader(senderId)) {
                        return CompletableFuture.completedFuture(GuildResult.NOT_LEADER);
                    }
                    return findAndKickMember(targetUsername, guild, sender.getUsername());
                });
    }

    private @NonNull CompletableFuture<GuildResult> findAndKickMember(@NonNull String targetUsername, @NonNull Guild guild, @NonNull String kickerName) {
        return storage.fetchPlayerByUsername(targetUsername)
                .thenCompose(targetOptional -> {
                    if (targetOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(GuildResult.PLAYER_NOT_FOUND);
                    }
                    UUID targetId = targetOptional.get().playerUuid();

                    if (!guild.isMember(targetId)) {
                        return CompletableFuture.completedFuture(GuildResult.PLAYER_NOT_IN_GUILD);
                    }

                    String targetName = targetOptional.get().username();

                    return storage.tryRemoveMember(guild.guildId(), targetId, guild.leaderId())
                            .thenApply(outcome -> switch (outcome) {
                                case RemoveMemberOutcome.MemberRemoved memberRemoved -> {
                                    notificationService.notifyMemberKicked(guild, targetId, targetName, kickerName);
                                    yield GuildResult.MEMBER_REMOVED;
                                }
                                case RemoveMemberOutcome.GuildDisbanded guildDisbanded ->
                                        GuildResult.LEFT_GUILD_DISBANDED;
                                case RemoveMemberOutcome.MemberNotFound memberNotFound ->
                                        GuildResult.PLAYER_NOT_IN_GUILD;
                                case RemoveMemberOutcome.GuildNotFound guildNotFound -> GuildResult.GUILD_NOT_FOUND;
                                case RemoveMemberOutcome.CannotRemoveLeader cannotRemoveLeader ->
                                        GuildResult.CANNOT_REMOVE_LEADER;
                                case RemoveMemberOutcome.NotLeader notLeader -> GuildResult.NOT_LEADER;
                            });
                });
    }

    public @NonNull CompletableFuture<GuildListResult> getGuildMembers(@NonNull Player sender, int page) {
        UUID senderId = sender.getUniqueId();

        return storage.getPlayerGuild(senderId)
                .thenApply(guildOptional -> {
                    if (guildOptional.isEmpty()) {
                        return new GuildListResult(GuildResult.NOT_IN_GUILD, PaginationResult.invalidPage(1));
                    }
                    Guild guild = guildOptional.get();
                    List<UUID> members = new ArrayList<>(guild.getMemberIds());
                    // Sort: leader first, then UUID natural ordering
                    members.sort((a, b) -> {
                        if (guild.isLeader(a)) return -1;
                        if (guild.isLeader(b)) return 1;
                        return a.compareTo(b);
                    });

                    if (members.isEmpty()) {
                        return new GuildListResult(GuildResult.LIST_EMPTY, PaginationResult.invalidPage(1));
                    }

                    PaginationResult<UUID> pagination = PaginationResult.paginate(members, page, SharedConstants.ENTRIES_PER_PAGE);
                    if (!pagination.isValidPage()) {
                        return new GuildListResult(GuildResult.INVALID_PAGE, pagination);
                    }
                    return new GuildListResult(GuildResult.SUCCESS, pagination);
                });
    }

    public @NonNull CompletableFuture<GuildResult> sendGuildChat(@NonNull Player sender, @NonNull String message) {
        if (message.length() > GuildProxyConstants.MAX_MESSAGE_LENGTH) {
            return CompletableFuture.completedFuture(GuildResult.MESSAGE_TOO_LONG);
        }

        UUID senderId = sender.getUniqueId();

        return storage.getPlayerGuild(senderId)
                .thenCompose(guildOptional -> {
                    if (guildOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(GuildResult.NOT_IN_GUILD);
                    }
                    Guild guild = guildOptional.get();
                    Set<UUID> memberIds = guild.getMemberIds();

                    return storage.fetchSettingsForMembers(memberIds)
                            .thenApply(settingsMap -> {
                                GuildSettings senderSettings = settingsMap.getOrDefault(senderId, new GuildSettings(senderId));
                                if (!senderSettings.showChat()) {
                                    return GuildResult.CHAT_DISABLED;
                                }
                                notificationService.sendGuildChat(guild, sender, message, settingsMap);
                                return GuildResult.CHAT_SENT;
                            });
                });
    }

    private @NonNull CompletableFuture<GuildResult> removePlayerFromGuild(@NonNull UUID memberId, @NonNull Guild guild, boolean isLeaving) {
        return storage.tryRemoveMember(guild.guildId(), memberId, memberId)
                .thenApply(outcome -> switch (outcome) {
                    case RemoveMemberOutcome.MemberRemoved memberRemoved -> GuildResult.LEFT_GUILD;
                    case RemoveMemberOutcome.GuildDisbanded guildDisbanded ->
                            isLeaving ? GuildResult.LEFT_GUILD_DISBANDED : GuildResult.LEFT_GUILD;
                    case RemoveMemberOutcome.MemberNotFound memberNotFound -> GuildResult.PLAYER_NOT_IN_GUILD;
                    case RemoveMemberOutcome.GuildNotFound guildNotFound -> GuildResult.GUILD_NOT_FOUND;
                    case RemoveMemberOutcome.CannotRemoveLeader cannotRemoveLeader -> GuildResult.CANNOT_REMOVE_LEADER;
                    case RemoveMemberOutcome.NotLeader notLeader -> GuildResult.NOT_LEADER;
                });
    }

    public @NonNull CompletableFuture<GuildResult> transferLeadership(@NonNull Player sender, @Nullable String targetUsername, boolean isConfirming) {
        if (targetUsername == null) {
            return CompletableFuture.completedFuture(GuildResult.PLAYER_NOT_FOUND);
        }

        UUID senderId = sender.getUniqueId();

        return storage.getPlayerGuild(senderId)
                .thenCompose(guildOptional -> {
                    if (guildOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(GuildResult.NOT_IN_GUILD);
                    }
                    Guild guild = guildOptional.get();
                    if (!guild.isLeader(senderId)) {
                        return CompletableFuture.completedFuture(GuildResult.NOT_LEADER);
                    }

                    return storage.fetchPlayerByUsername(targetUsername)
                            .thenCompose(targetOptional -> {
                                if (targetOptional.isEmpty()) {
                                    return CompletableFuture.completedFuture(GuildResult.PLAYER_NOT_FOUND);
                                }
                                UUID targetId = targetOptional.get().playerUuid();

                                if (senderId.equals(targetId)) {
                                    return CompletableFuture.completedFuture(GuildResult.CANNOT_TRANSFER_TO_SELF);
                                }

                                if (!guild.isMember(targetId)) {
                                    return CompletableFuture.completedFuture(GuildResult.PLAYER_NOT_IN_GUILD);
                                }

                                return handleTransferConfirmation(senderId, targetId, guild, isConfirming);
                            });
                });
    }

    private @NonNull CompletableFuture<GuildResult> handleTransferConfirmation(@NonNull UUID senderId, @NonNull UUID targetId, @NonNull Guild guild, boolean isConfirming) {
        if (!isConfirming) {
            return setupConfirmation(senderId, ConfirmationType.TRANSFER_LEADERSHIP, targetId, null);
        }
        return confirmAndExecute(senderId, ConfirmationType.TRANSFER_LEADERSHIP, targetId, null, () -> executeTransferLeadership(senderId, targetId, guild));
    }

    private @NonNull CompletableFuture<GuildResult> executeTransferLeadership(@NonNull UUID senderId, @NonNull UUID targetId, @NonNull Guild guild) {
        return storage.tryTransferLeadership(guild.guildId(), targetId, senderId)
                .thenApply(outcome -> switch (outcome) {
                    case TransferLeadershipOutcome.Transferred transferred -> {
                        notificationService.notifyLeadershipTransferred(guild, senderId, targetId)
                                .exceptionally(throwable -> {
                                    LOGGER.error("Failed to send leadership transferred notification", throwable);
                                    return null;
                                });
                        yield GuildResult.LEADERSHIP_TRANSFERRED;
                    }
                    case TransferLeadershipOutcome.GuildNotFound guildNotFound -> GuildResult.GUILD_NOT_FOUND;
                    case TransferLeadershipOutcome.TargetNotMember targetNotMember -> GuildResult.PLAYER_NOT_IN_GUILD;
                });
    }

    public @NonNull CompletableFuture<GuildResult> renameGuild(@NonNull Player sender, @Nullable String newName, boolean isConfirming) {
        if (newName == null) {
            return CompletableFuture.completedFuture(GuildResult.INVALID_GUILD_NAME);
        }

        if (!isValidGuildName(newName)) {
            return CompletableFuture.completedFuture(GuildResult.INVALID_GUILD_NAME);
        }

        UUID senderId = sender.getUniqueId();

        return storage.getPlayerGuild(senderId)
                .thenCompose(guildOptional -> {
                    if (guildOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(GuildResult.NOT_IN_GUILD);
                    }
                    Guild guild = guildOptional.get();
                    if (!guild.isLeader(senderId)) {
                        return CompletableFuture.completedFuture(GuildResult.NOT_LEADER);
                    }

                    if (guild.guildName().equals(newName)) {
                        return CompletableFuture.completedFuture(GuildResult.NAME_ALREADY_EXISTS);
                    }

                    return handleRenameConfirmation(senderId, newName, guild, isConfirming);
                });
    }

    private @NonNull CompletableFuture<GuildResult> handleRenameConfirmation(@NonNull UUID senderId, @NonNull String newName, @NonNull Guild guild, boolean isConfirming) {
        if (!isConfirming) {
            return setupConfirmation(senderId, ConfirmationType.RENAME_GUILD, null, newName);
        }
        return confirmAndExecute(senderId, ConfirmationType.RENAME_GUILD, null, newName, () -> executeRenameGuild(senderId, newName, guild));
    }

    private @NonNull CompletableFuture<GuildResult> executeRenameGuild(@NonNull UUID senderId, @NonNull String newName, @NonNull Guild guild) {
        String oldName = guild.guildName();
        return storage.tryRenameGuild(guild.guildId(), senderId, newName)
                .thenApply(outcome -> switch (outcome) {
                    case RenameGuildOutcome.Renamed renamed -> {
                        notificationService.notifyGuildRenamed(guild, oldName, newName);
                        yield GuildResult.GUILD_RENAMED;
                    }
                    case RenameGuildOutcome.GuildNotFound guildNotFound -> GuildResult.GUILD_NOT_FOUND;
                    case RenameGuildOutcome.NotLeader notLeader -> GuildResult.NOT_LEADER;
                    case RenameGuildOutcome.NameAlreadyExists nameAlreadyExists -> GuildResult.NAME_ALREADY_EXISTS;
                });
    }

    public @NonNull CompletableFuture<GuildResult> updateSettings(@NonNull Player sender, @Nullable String setting, @Nullable String value) {
        if (setting == null || value == null) {
            return CompletableFuture.completedFuture(GuildResult.INVALID_SETTING);
        }

        UUID senderId = sender.getUniqueId();

        return switch (setting.toLowerCase()) {
            case "invites" -> updateInvitePrivacy(senderId, value);
            case "chat" -> updateShowChat(senderId, value);
            default -> CompletableFuture.completedFuture(GuildResult.INVALID_SETTING);
        };
    }

    private @NonNull CompletableFuture<GuildResult> updateInvitePrivacy(@NonNull UUID playerId, @NonNull String value) {
        if (!List.of("all", "friend", "none").contains(value.toLowerCase())) {
            return CompletableFuture.completedFuture(GuildResult.INVALID_SETTING);
        }
        return storage.updateInvitePrivacy(playerId, value.toLowerCase())
                .thenApply(ignored -> GuildResult.SETTING_UPDATED);
    }

    private @NonNull CompletableFuture<GuildResult> updateShowChat(@NonNull UUID playerId, @NonNull String value) {
        boolean showChat = Boolean.parseBoolean(value) || "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
        return storage.updateShowChat(playerId, showChat)
                .thenApply(ignored -> GuildResult.SETTING_UPDATED);
    }

    public @NonNull CompletableFuture<GuildInfoResult> getGuildInfo(@NonNull Player sender) {
        UUID senderId = sender.getUniqueId();
        return storage.getPlayerGuild(senderId)
                .thenApply(guildOptional -> guildOptional
                        .map(guild -> new GuildInfoResult(GuildResult.SUCCESS, Optional.of(guild)))
                        .orElseGet(() -> new GuildInfoResult(GuildResult.NOT_IN_GUILD, Optional.empty())));
    }

    public @NonNull CompletableFuture<GuildInfoResult> getGuildInfoByName(@NonNull String guildName) {
        return storage.fetchGuildByName(guildName)
                .thenApply(guildOptional -> guildOptional
                        .map(guild -> new GuildInfoResult(GuildResult.SUCCESS, Optional.of(guild)))
                        .orElseGet(() -> new GuildInfoResult(GuildResult.GUILD_NOT_FOUND, Optional.empty())));
    }

    public @NonNull CompletableFuture<Void> handlePlayerJoin(@NonNull UUID playerId, @NonNull String username) {
        return storage.upsertPlayer(playerId, username);
    }

    public void cleanupExpiredInvitations() {
        storage.cleanupExpiredInvitations(Instant.now(), GuildProxyConstants.INVITATION_EXPIRY)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to cleanup expired invitations", throwable);
                    return null;
                });
    }

    public void cleanupExpiredConfirmations() {
        storage.cleanupExpiredConfirmations(Instant.now(), GuildProxyConstants.CONFIRMATION_EXPIRY)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to cleanup expired confirmations", throwable);
                    return null;
                });
    }

    public void cleanupExpiredCooldowns() {
        storage.cleanupExpiredCooldowns(Instant.now(), GuildProxyConstants.INVITATION_COOLDOWN)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to cleanup expired cooldowns", throwable);
                    return null;
                });
    }

    private @NonNull CompletableFuture<GuildResult> setupConfirmation(@NonNull UUID playerId, @NonNull ConfirmationType type, @Nullable UUID targetId, @Nullable String newValue) {
        PendingConfirmation confirmation = new PendingConfirmation(playerId, type, targetId, newValue);
        return storage.setPendingConfirmation(confirmation)
                .thenCompose(outcome -> {
                    if (outcome instanceof ConfirmationOutcome.Set) {
                        return CompletableFuture.completedFuture(getRequiredResult(type));
                    }
                    ConfirmationOutcome.AlreadyExists alreadyExists = (ConfirmationOutcome.AlreadyExists) outcome;
                    PendingConfirmation existing = alreadyExists.existing();
                    boolean paramsMismatch =
                            (targetId != null && !targetId.equals(existing.targetId())) ||
                            (newValue != null && !newValue.equalsIgnoreCase(
                                    existing.newValue() != null ? existing.newValue() : ""));
                    if (existing.isExpired() || existing.type() != type || paramsMismatch) {
                        return storage.removePendingConfirmation(playerId)
                                .thenCompose(ignored -> storage.setPendingConfirmation(confirmation))
                                .thenApply(retryOutcome -> {
                                    if (retryOutcome instanceof ConfirmationOutcome.Set) {
                                        return getRequiredResult(type);
                                    }
                                    return GuildResult.NO_CONFIRMATION_PENDING;
                                });
                    }
                    // Exact match — confirm the action
                    return CompletableFuture.completedFuture(getRequiredResult(type));
                });
    }

    private @NonNull CompletableFuture<GuildResult> confirmAndExecute(@NonNull UUID playerId, @NonNull ConfirmationType expectedType,
                                                                      @Nullable UUID expectedTargetId, @Nullable String expectedNewValue, @NonNull Supplier<CompletableFuture<GuildResult>> onSuccess) {
        return storage.fetchPendingConfirmation(playerId)
                .thenCompose(existingOpt -> {
                    if (existingOpt.isEmpty()) {
                        return CompletableFuture.completedFuture(GuildResult.NO_CONFIRMATION_PENDING);
                    }
                    PendingConfirmation existing = existingOpt.get();
                    if (existing.isExpired() || existing.type() != expectedType) {
                        return storage.removePendingConfirmation(playerId)
                                .thenApply(ignored -> GuildResult.NO_CONFIRMATION_PENDING);
                    }
                    if (expectedTargetId != null && !expectedTargetId.equals(existing.targetId())) {
                        return CompletableFuture.completedFuture(GuildResult.NO_CONFIRMATION_PENDING);
                    }
                    if (expectedNewValue != null && !expectedNewValue.equalsIgnoreCase(
                            existing.newValue() != null ? existing.newValue() : "")) {
                        return CompletableFuture.completedFuture(GuildResult.NO_CONFIRMATION_PENDING);
                    }
                    return storage.removePendingConfirmation(playerId)
                            .thenCompose(ignored -> onSuccess.get());
                });
    }

    private @NonNull GuildResult getRequiredResult(@NonNull ConfirmationType type) {
        return switch (type) {
            case DISBAND_GUILD -> GuildResult.DISBAND_CONFIRMATION_REQUIRED;
            case TRANSFER_LEADERSHIP -> GuildResult.TRANSFER_CONFIRMATION_REQUIRED;
            case RENAME_GUILD -> GuildResult.RENAME_CONFIRMATION_REQUIRED;
        };
    }
}
