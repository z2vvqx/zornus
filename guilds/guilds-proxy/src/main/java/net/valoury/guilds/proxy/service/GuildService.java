package net.valoury.guilds.proxy.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.valoury.friends.api.FriendshipService;
import net.luckperms.api.LuckPerms;
import net.valoury.shared.model.PlayerRecord;
import net.valoury.guilds.proxy.GuildProxyConstants;
import net.valoury.guilds.proxy.model.*;
import net.valoury.guilds.proxy.model.result.GuildInfoResult;
import net.valoury.guilds.proxy.model.result.GuildListResult;
import net.valoury.guilds.proxy.model.result.GuildRankChangeResult;
import net.valoury.guilds.proxy.model.result.GuildRequestsResult;
import net.valoury.guilds.proxy.model.result.GuildResults;
import net.valoury.guilds.proxy.storage.*;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.model.GroupJoinPolicy;
import net.valoury.shared.utilities.PaginationResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class GuildService implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuildService.class);
    private static final Set<String> ALLOWED_GUILD_COLORS = Set.of(
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
            "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple",
            "yellow", "white"
    );

    private final @NonNull GuildStorage storage;
    private final @NonNull ProxyServer proxyServer;
    private final @NonNull GuildNotificationService notificationService;
    private final @NonNull FriendshipService friendshipService;

    public GuildService(
            @NonNull GuildStorage storage,
            @NonNull ProxyServer proxyServer,
            @NonNull FriendshipService friendshipService,
            @NonNull LuckPerms luckPerms
    ) {
        this.storage = storage;
        this.proxyServer = proxyServer;
        this.friendshipService = friendshipService;
        this.notificationService = new GuildNotificationService(storage, proxyServer, luckPerms);
    }

    @Override
    public void close() {
        storage.close();
    }

    public @NonNull GuildNotificationService getNotificationService() {
        return notificationService;
    }

    public @NonNull CompletableFuture<GuildResults.Create> createGuild(@NonNull Player sender, @NonNull String guildName, @NonNull String guildTag, @NonNull String guildColor) {
        return createGuildLegacy(sender, guildName, guildTag, guildColor).thenApply(GuildResults.Create::from);
    }

    private @NonNull CompletableFuture<GuildResult> createGuildLegacy(@NonNull Player sender, @NonNull String guildName, @NonNull String guildTag, @NonNull String guildColor) {
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
                    case CreateGuildOutcome.GuildTagAlreadyExists ignored ->
                            GuildResult.GUILD_TAG_ALREADY_EXISTS;
                });
    }

    private boolean isValidGuildName(String name) {
        return name != null && name.length() >= 3 && name.length() <= 24 && name.matches("^[a-zA-Z0-9_]+$");
    }

    private boolean isValidGuildTag(String tag) {
        return tag != null && tag.length() >= 2 && tag.length() <= 5 && tag.matches("^[a-zA-Z0-9_]+$");
    }

    public @NonNull CompletableFuture<GuildResults.Disband> disbandGuild(@NonNull Player sender, boolean isConfirming) {
        return disbandGuildLegacy(sender, isConfirming).thenApply(GuildResults.Disband::from);
    }

    private @NonNull CompletableFuture<GuildResult> disbandGuildLegacy(@NonNull Player sender, boolean isConfirming) {
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

    public @NonNull CompletableFuture<GuildResults.SendInvitation> sendInvitation(@NonNull Player sender, @Nullable String targetUsername) {
        if (targetUsername == null) {
            return CompletableFuture.completedFuture(new GuildResults.SendInvitation.PlayerNotFound());
        }

        UUID senderId = sender.getUniqueId();

        return storage.getPlayerGuild(senderId)
                .thenCompose(guildOptional -> {
                    if (guildOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                new GuildResults.SendInvitation.NotInGuild());
                    }

                    Guild guild = guildOptional.get();
                    GuildRank senderRank = guild.findMemberRank(senderId)
                            .orElse(GuildRank.OUTCAST);
                    if (!senderRank.canManageInvitations()) {
                        return CompletableFuture.completedFuture(
                                new GuildResults.SendInvitation.InsufficientRank());
                    }

                    if (sender.getUsername().equalsIgnoreCase(targetUsername)) {
                        return CompletableFuture.completedFuture(
                                new GuildResults.SendInvitation.CannotInviteSelf());
                    }

                    return resolveAndSendInvitation(sender, targetUsername, guild);
                });
    }

    private @NonNull CompletableFuture<GuildResults.SendInvitation> resolveAndSendInvitation(
            @NonNull Player sender,
            @NonNull String targetUsername,
            @NonNull Guild guild
    ) {
        UUID senderId = sender.getUniqueId();
        return resolveTargetPlayer(targetUsername)
                .thenCompose(targetOptional -> {
                    if (targetOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                new GuildResults.SendInvitation.PlayerNotFound());
                    }

                    PlayerRecord targetRecord = targetOptional.get();
                    UUID targetId = targetRecord.playerUuid();
                    String targetPlayerName = targetRecord.username();
                    if (senderId.equals(targetId)) {
                        return CompletableFuture.completedFuture(
                                new GuildResults.SendInvitation.CannotInviteSelf());
                    }

                    return executeSendInvitation(sender, targetId, targetPlayerName, guild)
                            .thenApply(result -> GuildResults.SendInvitation.from(
                                    result,
                                    targetPlayerName
                            ));
                });
    }

    private @NonNull CompletableFuture<GuildResult> executeSendInvitation(@NonNull Player sender, @NonNull UUID targetId, @NonNull String targetUsername, @NonNull Guild guild) {
        UUID senderId = sender.getUniqueId();

        return friendshipService.areFriends(senderId, targetId)
                .thenCompose(areFriends ->
                        executeStorageSendInvitation(sender, targetId, targetUsername, guild, areFriends));
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
                    case SendInvitationOutcome.SenderInsufficientRank senderInsufficientRank ->
                            GuildResult.INSUFFICIENT_RANK;
                    case SendInvitationOutcome.GuildNoLongerExists guildNoLongerExists -> GuildResult.GUILD_NOT_FOUND;
                });
    }

    public @NonNull CompletableFuture<GuildResults.AcceptInvitation> acceptInvitation(@NonNull Player sender, @Nullable String guildName) {
        if (guildName == null) {
            return CompletableFuture.completedFuture(new GuildResults.AcceptInvitation.GuildNotFound());
        }

        UUID senderId = sender.getUniqueId();

        return storage.isInGuild(senderId)
                .thenCompose(inGuild -> {
                    if (inGuild) {
                        return CompletableFuture.completedFuture(new GuildResults.AcceptInvitation.AlreadyInGuild());
                    }
                    return findAndAcceptInvitationByGuildName(senderId, guildName);
                });
    }

    private @NonNull CompletableFuture<GuildResults.AcceptInvitation> findAndAcceptInvitationByGuildName(
            @NonNull UUID senderId,
            @NonNull String guildName
    ) {
        return storage.findInvitationByGuildName(senderId, guildName)
                .thenCompose(invitationOptional -> {
                    if (invitationOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                new GuildResults.AcceptInvitation.NoInvitationFound());
                    }
                    GuildInvitation invitation = invitationOptional.get();
                    return addMemberToGuild(senderId, invitation);
                });
    }

    private @NonNull CompletableFuture<GuildResults.AcceptInvitation> addMemberToGuild(
            @NonNull UUID playerId,
            @NonNull GuildInvitation invitation
    ) {
        UUID guildId = invitation.guildId();
        return storage.tryAcceptInvitation(guildId, invitation.senderId(), playerId)
                .thenCompose(outcome -> switch (outcome) {
                    case AcceptInvitationOutcome.Accepted accepted -> storage.fetchGuild(guildId)
                            .thenApply(guildOptional -> {
                                if (guildOptional.isEmpty()) {
                                    return new GuildResults.AcceptInvitation.GuildNotFound();
                                }

                                Guild guild = guildOptional.get();
                                proxyServer.getPlayer(playerId).ifPresent(player ->
                                        notificationService.notifyMemberJoined(guild, player));
                                return new GuildResults.AcceptInvitation.Joined(guild.guildName());
                            });
                    case AcceptInvitationOutcome.GuildFull guildFull ->
                            CompletableFuture.completedFuture(new GuildResults.AcceptInvitation.GuildFull());
                    case AcceptInvitationOutcome.AlreadyInGuild alreadyInGuild ->
                            CompletableFuture.completedFuture(new GuildResults.AcceptInvitation.AlreadyInGuild());
                    case AcceptInvitationOutcome.InvitationExpired invitationExpired ->
                            CompletableFuture.completedFuture(
                                    new GuildResults.AcceptInvitation.NoInvitationFound());
                    case AcceptInvitationOutcome.InvitationNoLongerValid invitationNoLongerValid ->
                            CompletableFuture.completedFuture(
                                    new GuildResults.AcceptInvitation.NoInvitationFound());
                });
    }

    public @NonNull CompletableFuture<GuildResults.JoinPublic> joinPublicGuild(
            @NonNull Player sender,
            @Nullable String guildName
    ) {
        if (guildName == null) {
            return CompletableFuture.completedFuture(new GuildResults.JoinPublic.GuildNotFound());
        }

        UUID senderId = sender.getUniqueId();
        return storage.fetchGuildByName(guildName)
                .thenCompose(guildOptional -> {
                    if (guildOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                new GuildResults.JoinPublic.GuildNotFound());
                    }

                    Guild guild = guildOptional.get();
                    return storage.tryJoinPublicGuild(guild.guildId(), senderId)
                            .thenCompose(outcome -> switch (outcome) {
                                case JoinPublicGuildOutcome.Joined ignored ->
                                        completePublicGuildJoin(guild.guildId(), sender);
                                case JoinPublicGuildOutcome.AlreadyInGuild ignored ->
                                        CompletableFuture.completedFuture(
                                                new GuildResults.JoinPublic.AlreadyInGuild());
                                case JoinPublicGuildOutcome.GuildFull ignored ->
                                        CompletableFuture.completedFuture(
                                                new GuildResults.JoinPublic.GuildFull());
                                case JoinPublicGuildOutcome.GuildPrivate ignored ->
                                        CompletableFuture.completedFuture(
                                                new GuildResults.JoinPublic.GuildPrivate());
                                case JoinPublicGuildOutcome.GuildNotFound ignored ->
                                        CompletableFuture.completedFuture(
                                                new GuildResults.JoinPublic.GuildNotFound());
                            });
                });
    }

    private @NonNull CompletableFuture<GuildResults.JoinPublic> completePublicGuildJoin(
            @NonNull UUID guildId,
            @NonNull Player joiningPlayer
    ) {
        return storage.fetchGuild(guildId)
                .thenApply(guildOptional -> {
                    if (guildOptional.isEmpty()) {
                        return new GuildResults.JoinPublic.GuildNotFound();
                    }

                    Guild updatedGuild = guildOptional.get();
                    notificationService.notifyMemberJoined(updatedGuild, joiningPlayer);
                    return new GuildResults.JoinPublic.Joined(updatedGuild.guildName());
                });
    }

    public @NonNull CompletableFuture<GuildResults.RejectInvitation> rejectInvitation(@NonNull Player sender, @Nullable String guildName) {
        return rejectInvitationLegacy(sender, guildName).thenApply(GuildResults.RejectInvitation::from);
    }

    private @NonNull CompletableFuture<GuildResult> rejectInvitationLegacy(@NonNull Player sender, @Nullable String guildName) {
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

    public @NonNull CompletableFuture<GuildResults.RevokeInvitation> revokeInvitation(@NonNull Player sender, @Nullable String targetUsername) {
        if (targetUsername == null) {
            return CompletableFuture.completedFuture(
                    new GuildResults.RevokeInvitation.PlayerNotFound());
        }

        UUID senderId = sender.getUniqueId();

        return storage.getPlayerGuild(senderId)
                .thenCompose(guildOptional -> {
                    if (guildOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                new GuildResults.RevokeInvitation.NotInGuild());
                    }
                    Guild guild = guildOptional.get();
                    GuildRank senderRank = guild.findMemberRank(senderId)
                            .orElse(GuildRank.OUTCAST);
                    if (!senderRank.canManageInvitations()) {
                        return CompletableFuture.completedFuture(
                                new GuildResults.RevokeInvitation.InsufficientRank());
                    }
                    return findAndRevokeInvitation(targetUsername, guild.guildId(), senderId);
                });
    }

    private @NonNull CompletableFuture<GuildResults.RevokeInvitation> findAndRevokeInvitation(
            @NonNull String targetUsername,
            @NonNull UUID guildId,
            @NonNull UUID requesterId
    ) {
        return resolveTargetPlayer(targetUsername)
                .thenCompose(targetOptional -> {
                    if (targetOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                new GuildResults.RevokeInvitation.PlayerNotFound());
                    }
                    PlayerRecord target = targetOptional.get();
                    UUID targetId = target.playerUuid();
                    return storage.tryRevokeInvitation(guildId, requesterId, targetId)
                            .thenApply(outcome -> switch (outcome) {
                                case RevokeInvitationOutcome.Revoked ignored ->
                                        new GuildResults.RevokeInvitation.Revoked(
                                                target.username());
                                case RevokeInvitationOutcome.InvitationNotFound ignored ->
                                        new GuildResults.RevokeInvitation.NoInvitationFound(
                                                target.username());
                                case RevokeInvitationOutcome.InsufficientRank ignored ->
                                        new GuildResults.RevokeInvitation.InsufficientRank();
                                case RevokeInvitationOutcome.GuildNotFound ignored ->
                                        new GuildResults.RevokeInvitation.GuildNotFound();
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
                    new GuildRequestsResult.InvalidRequestType());
        }

        return invitationsFuture.thenApply(invitations -> {
            if (invitations.isEmpty()) {
                return new GuildRequestsResult.Empty();
            }

            PaginationResult<GuildInvitation> pagination = PaginationResult.paginate(invitations, page, SharedConstants.ENTRIES_PER_PAGE);
            if (!pagination.isValidPage()) {
                return new GuildRequestsResult.InvalidPage(pagination);
            }
            return new GuildRequestsResult.Found(pagination);
        });
    }

    public @NonNull CompletableFuture<GuildResults.Leave> leaveGuild(@NonNull Player sender) {
        return leaveGuildLegacy(sender).thenApply(GuildResults.Leave::from);
    }

    private @NonNull CompletableFuture<GuildResult> leaveGuildLegacy(@NonNull Player sender) {
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

    public @NonNull CompletableFuture<GuildResults.KickMember> kickMember(@NonNull Player sender, @Nullable String targetUsername) {
        if (targetUsername == null) {
            return CompletableFuture.completedFuture(
                    new GuildResults.KickMember.PlayerNotFound());
        }

        UUID senderId = sender.getUniqueId();

        return storage.getPlayerGuild(senderId)
                .thenCompose(guildOptional -> {
                    if (guildOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                new GuildResults.KickMember.NotInGuild());
                    }
                    Guild guild = guildOptional.get();
                    GuildRank senderRank = guild.findMemberRank(senderId)
                            .orElse(GuildRank.OUTCAST);
                    if (senderRank.hierarchyLevel() < GuildRank.OFFICER.hierarchyLevel()) {
                        return CompletableFuture.completedFuture(
                                new GuildResults.KickMember.InsufficientRank());
                    }
                    return findAndKickMember(
                            targetUsername,
                            guild,
                            senderId,
                            sender.getUsername()
                    );
                });
    }

    private @NonNull CompletableFuture<GuildResults.KickMember> findAndKickMember(
            @NonNull String targetUsername,
            @NonNull Guild guild,
            @NonNull UUID requesterId,
            @NonNull String kickerName
    ) {
        return storage.fetchGuildMemberByUsername(guild.guildId(), targetUsername)
                .thenCompose(targetOptional -> {
                    if (targetOptional.isEmpty()) {
                        return resolveTargetPlayer(targetUsername)
                                .thenApply(resolvedTarget -> resolvedTarget.isPresent()
                                        ? new GuildResults.KickMember.PlayerNotInGuild(
                                                resolvedTarget.get().username())
                                        : new GuildResults.KickMember.PlayerNotFound());
                    }

                    PlayerRecord target = targetOptional.get();
                    UUID targetId = target.playerUuid();

                    if (requesterId.equals(targetId)) {
                        return CompletableFuture.completedFuture(
                                new GuildResults.KickMember.CannotRemoveSelf());
                    }

                    GuildRank requesterRank = guild.findMemberRank(requesterId)
                            .orElse(GuildRank.OUTCAST);
                    Optional<GuildRank> targetRank = guild.findMemberRank(targetId);
                    if (targetRank.isPresent() && !requesterRank.canKick(targetRank.get())) {
                        return CompletableFuture.completedFuture(
                                targetRank.get() == GuildRank.LEADER
                                        ? new GuildResults.KickMember.CannotRemoveLeader()
                                        : new GuildResults.KickMember.InsufficientRank()
                        );
                    }

                    return storage.tryRemoveMember(guild.guildId(), targetId, requesterId)
                            .thenApply(outcome -> switch (outcome) {
                                case RemoveMemberOutcome.MemberRemoved memberRemoved -> {
                                    notificationService.notifyMemberKicked(
                                            guild,
                                            targetId,
                                            target.username(),
                                            kickerName
                                    );
                                    yield new GuildResults.KickMember.Removed(
                                            target.username());
                                }
                                case RemoveMemberOutcome.GuildDisbanded guildDisbanded ->
                                        new GuildResults.KickMember.GuildDisbanded();
                                case RemoveMemberOutcome.MemberNotFound memberNotFound ->
                                        new GuildResults.KickMember.PlayerNotInGuild(
                                                target.username());
                                case RemoveMemberOutcome.GuildNotFound guildNotFound ->
                                        new GuildResults.KickMember.GuildNotFound();
                                case RemoveMemberOutcome.CannotRemoveLeader cannotRemoveLeader ->
                                        new GuildResults.KickMember.CannotRemoveLeader();
                                case RemoveMemberOutcome.InsufficientRank insufficientRank ->
                                        new GuildResults.KickMember.InsufficientRank();
                            });
                });
    }

    public @NonNull CompletableFuture<GuildRankChangeResult> promoteMember(
            @NonNull Player sender,
            @Nullable String targetUsername
    ) {
        return changeMemberRank(sender, targetUsername, GuildRankChangeDirection.PROMOTION);
    }

    public @NonNull CompletableFuture<GuildRankChangeResult> demoteMember(
            @NonNull Player sender,
            @Nullable String targetUsername
    ) {
        return changeMemberRank(sender, targetUsername, GuildRankChangeDirection.DEMOTION);
    }

    private @NonNull CompletableFuture<GuildRankChangeResult> changeMemberRank(
            @NonNull Player sender,
            @Nullable String targetUsername,
            @NonNull GuildRankChangeDirection direction
    ) {
        if (targetUsername == null) {
            return CompletableFuture.completedFuture(new GuildRankChangeResult.PlayerNotFound());
        }

        UUID actorId = sender.getUniqueId();
        return storage.getPlayerGuild(actorId)
                .thenCompose(guildOptional -> {
                    if (guildOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                new GuildRankChangeResult.NotInGuild());
                    }

                    Guild guild = guildOptional.get();
                    GuildRank actorRank = guild.findMemberRank(actorId)
                            .orElse(GuildRank.OUTCAST);
                    if (!actorRank.canChangeRanks()) {
                        return CompletableFuture.completedFuture(
                                new GuildRankChangeResult.InsufficientRank());
                    }

                    return storage.fetchGuildMemberByUsername(
                                    guild.guildId(),
                                    targetUsername
                            )
                            .thenCompose(targetOptional -> {
                                if (targetOptional.isEmpty()) {
                                    return resolveTargetPlayer(targetUsername)
                                            .thenApply(resolvedTarget -> resolvedTarget.isPresent()
                                                    ? new GuildRankChangeResult.PlayerNotInGuild(
                                                            resolvedTarget.get().username())
                                                    : new GuildRankChangeResult.PlayerNotFound());
                                }

                                PlayerRecord target = targetOptional.get();
                                UUID targetId = target.playerUuid();
                                if (actorId.equals(targetId)) {
                                    return CompletableFuture.completedFuture(
                                            new GuildRankChangeResult.CannotChangeOwnRank());
                                }

                                Optional<GuildRank> targetRankOptional =
                                        guild.findMemberRank(targetId);
                                if (targetRankOptional.isEmpty()) {
                                    return CompletableFuture.completedFuture(
                                            new GuildRankChangeResult.PlayerNotInGuild(
                                                    target.username()));
                                }

                                GuildRank targetRank = targetRankOptional.get();
                                Optional<GuildRankChangeResult> deniedResult =
                                        validateRankChange(
                                                actorRank,
                                                targetRank,
                                                target.username(),
                                                direction
                                        );
                                if (deniedResult.isPresent()) {
                                    return CompletableFuture.completedFuture(
                                            deniedResult.get());
                                }

                                return executeRankChange(
                                        sender,
                                        target,
                                        guild,
                                        direction
                                );
                            });
                });
    }

    private @NonNull Optional<GuildRankChangeResult> validateRankChange(
            @NonNull GuildRank actorRank,
            @NonNull GuildRank targetRank,
            @NonNull String targetName,
            @NonNull GuildRankChangeDirection direction
    ) {
        if (!actorRank.isHigherThan(targetRank)) {
            return Optional.of(new GuildRankChangeResult.CannotManageRank(targetName));
        }

        if (direction == GuildRankChangeDirection.PROMOTION) {
            Optional<GuildRank> promotedRank = targetRank.nextHigher();
            if (promotedRank.isEmpty()) {
                return Optional.of(
                        new GuildRankChangeResult.AlreadyHighestRank(targetName));
            }
            if (promotedRank.get() == actorRank) {
                return Optional.of(
                        new GuildRankChangeResult.PromotionWouldMatchActorRank(targetName));
            }
            if (!actorRank.canPromote(targetRank)) {
                return Optional.of(
                        new GuildRankChangeResult.CannotManageRank(targetName));
            }
            return Optional.empty();
        }

        if (targetRank.nextLower().isEmpty()) {
            return Optional.of(
                    new GuildRankChangeResult.AlreadyLowestRank(targetName));
        }
        return actorRank.canDemote(targetRank)
                ? Optional.empty()
                : Optional.of(new GuildRankChangeResult.CannotManageRank(targetName));
    }

    private @NonNull CompletableFuture<GuildRankChangeResult> executeRankChange(
            @NonNull Player actor,
            @NonNull PlayerRecord target,
            @NonNull Guild guild,
            @NonNull GuildRankChangeDirection direction
    ) {
        return storage.tryChangeMemberRank(
                        guild.guildId(),
                        actor.getUniqueId(),
                        target.playerUuid(),
                        direction
                )
                .thenApply(outcome -> switch (outcome) {
                    case GuildRankChangeOutcome.Changed changed -> {
                        notificationService.notifyMemberRankChanged(
                                guild,
                                target.username(),
                                actor.getUsername(),
                                changed.newRank(),
                                direction
                        );
                        yield new GuildRankChangeResult.Changed(
                                target.username(),
                                actor.getUsername(),
                                changed.previousRank(),
                                changed.newRank()
                        );
                    }
                    case GuildRankChangeOutcome.GuildNotFound ignored ->
                            new GuildRankChangeResult.GuildNotFound();
                    case GuildRankChangeOutcome.ActorNotMember ignored ->
                            new GuildRankChangeResult.NotInGuild();
                    case GuildRankChangeOutcome.MemberNotFound ignored ->
                            new GuildRankChangeResult.PlayerNotInGuild(target.username());
                    case GuildRankChangeOutcome.CannotChangeSelf ignored ->
                            new GuildRankChangeResult.CannotChangeOwnRank();
                    case GuildRankChangeOutcome.InsufficientRank ignored ->
                            new GuildRankChangeResult.InsufficientRank();
                    case GuildRankChangeOutcome.CannotManageRank ignored ->
                            new GuildRankChangeResult.CannotManageRank(target.username());
                    case GuildRankChangeOutcome.PromotionWouldMatchActorRank ignored ->
                            new GuildRankChangeResult.PromotionWouldMatchActorRank(
                                    target.username());
                    case GuildRankChangeOutcome.AlreadyHighestRank ignored ->
                            new GuildRankChangeResult.AlreadyHighestRank(target.username());
                    case GuildRankChangeOutcome.AlreadyLowestRank ignored ->
                            new GuildRankChangeResult.AlreadyLowestRank(target.username());
                });
    }

    public @NonNull CompletableFuture<GuildListResult> getGuildMembers(@NonNull Player sender, int page) {
        UUID senderId = sender.getUniqueId();

        return storage.getPlayerGuild(senderId)
                .thenApply(guildOptional -> {
                    if (guildOptional.isEmpty()) {
                        return new GuildListResult.NotInGuild();
                    }
                    Guild guild = guildOptional.get();
                    List<UUID> members = new ArrayList<>(guild.getMemberIds());
                    Set<UUID> onlineMemberIds = members.stream()
                            .filter(memberId -> proxyServer.getPlayer(memberId).isPresent())
                            .collect(Collectors.toUnmodifiableSet());
                    members.sort((a, b) -> {
                        GuildRank firstMemberRank = guild.findMemberRank(a)
                                .orElse(GuildRank.OUTCAST);
                        GuildRank secondMemberRank = guild.findMemberRank(b)
                                .orElse(GuildRank.OUTCAST);
                        int rankComparison = Integer.compare(
                                secondMemberRank.hierarchyLevel(),
                                firstMemberRank.hierarchyLevel()
                        );
                        if (rankComparison != 0) {
                            return rankComparison;
                        }

                        boolean firstMemberOnline = onlineMemberIds.contains(a);
                        boolean secondMemberOnline = onlineMemberIds.contains(b);
                        if (firstMemberOnline != secondMemberOnline) {
                            return firstMemberOnline ? -1 : 1;
                        }

                        return a.compareTo(b);
                    });

                    if (members.isEmpty()) {
                        return new GuildListResult.Empty();
                    }

                    PaginationResult<UUID> pagination = PaginationResult.paginate(members, page, SharedConstants.ENTRIES_PER_PAGE);
                    if (!pagination.isValidPage()) {
                        return new GuildListResult.InvalidPage(pagination);
                    }
                    return new GuildListResult.Found(pagination, guild);
                });
    }

    public @NonNull CompletableFuture<GuildResults.SendChat> sendGuildChat(@NonNull Player sender, @NonNull String message) {
        return sendGuildChatLegacy(sender, message).thenApply(GuildResults.SendChat::from);
    }

    private @NonNull CompletableFuture<GuildResult> sendGuildChatLegacy(@NonNull Player sender, @NonNull String message) {
        UUID senderId = sender.getUniqueId();

        return storage.getPlayerGuild(senderId)
                .thenCompose(guildOptional -> {
                    if (guildOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(GuildResult.NOT_IN_GUILD);
                    }
                    if (message.length() > GuildProxyConstants.MAX_MESSAGE_LENGTH) {
                        return CompletableFuture.completedFuture(GuildResult.MESSAGE_TOO_LONG);
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
                    case RemoveMemberOutcome.InsufficientRank insufficientRank -> GuildResult.NOT_LEADER;
                });
    }

    public @NonNull CompletableFuture<GuildResults.TransferLeadership> transferLeadership(@NonNull Player sender, @Nullable String targetUsername, boolean isConfirming) {
        if (targetUsername == null) {
            return CompletableFuture.completedFuture(
                    new GuildResults.TransferLeadership.PlayerNotFound());
        }

        UUID senderId = sender.getUniqueId();

        return storage.getPlayerGuild(senderId)
                .thenCompose(guildOptional -> {
                    if (guildOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                new GuildResults.TransferLeadership.NotInGuild());
                    }
                    Guild guild = guildOptional.get();
                    if (!guild.isLeader(senderId)) {
                        return CompletableFuture.completedFuture(
                                new GuildResults.TransferLeadership.NotLeader());
                    }

                    return storage.fetchGuildMemberByUsername(
                                    guild.guildId(),
                                    targetUsername
                            )
                            .thenCompose(targetOptional -> {
                                if (targetOptional.isEmpty()) {
                                    return resolveTargetPlayer(targetUsername)
                                            .thenApply(resolvedTarget -> resolvedTarget.isPresent()
                                                    ? new GuildResults.TransferLeadership.PlayerNotInGuild(
                                                            resolvedTarget.get().username())
                                                    : new GuildResults.TransferLeadership.PlayerNotFound());
                                }

                                PlayerRecord target = targetOptional.get();
                                UUID targetId = target.playerUuid();

                                if (senderId.equals(targetId)) {
                                    return CompletableFuture.completedFuture(
                                            new GuildResults.TransferLeadership.CannotTransferToSelf());
                                }

                                return handleTransferConfirmation(
                                        senderId,
                                        targetId,
                                        guild,
                                        isConfirming
                                ).thenApply(result -> GuildResults.TransferLeadership.from(
                                        result,
                                        target.username()
                                ));
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

    public @NonNull CompletableFuture<GuildResults.Rename> renameGuild(@NonNull Player sender, @Nullable String newName, boolean isConfirming) {
        return renameGuildLegacy(sender, newName, isConfirming).thenApply(GuildResults.Rename::from);
    }

    private @NonNull CompletableFuture<GuildResult> renameGuildLegacy(@NonNull Player sender, @Nullable String newName, boolean isConfirming) {
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
                    if (!isValidGuildName(newName)) {
                        return CompletableFuture.completedFuture(GuildResult.INVALID_GUILD_NAME);
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

    public @NonNull CompletableFuture<GuildResults.UpdateSetting> updateSettings(@NonNull Player sender, @Nullable String setting, @Nullable String value) {
        return updateSettingsLegacy(sender, setting, value).thenApply(GuildResults.UpdateSetting::from);
    }

    private @NonNull CompletableFuture<GuildResult> updateSettingsLegacy(@NonNull Player sender, @Nullable String setting, @Nullable String value) {
        if (setting == null || value == null) {
            return CompletableFuture.completedFuture(GuildResult.INVALID_SETTING);
        }

        UUID senderId = sender.getUniqueId();

        return switch (setting.toLowerCase()) {
            case "invites" -> updateInvitePrivacy(senderId, value);
            case "chat" -> updateShowChat(senderId, value);
            case "privacy" -> updateGroupPrivacy(senderId, value);
            default -> CompletableFuture.completedFuture(GuildResult.INVALID_SETTING);
        };
    }

    public @NonNull CompletableFuture<GuildSettings> getSettings(@NonNull UUID playerId) {
        return storage.fetchSettings(playerId)
                .thenApply(settings -> settings.orElseGet(() -> new GuildSettings(playerId)));
    }

    public @NonNull CompletableFuture<Optional<GuildGroupSettings>> getGroupSettingsForPlayer(
            @NonNull UUID playerId
    ) {
        return storage.getPlayerGuild(playerId)
                .thenCompose(guildOptional -> guildOptional
                        .map(guild -> storage.fetchGroupSettings(guild.guildId()))
                        .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty())));
    }

    public @NonNull CompletableFuture<GuildResults.UpdateTag> updateGuildTag(@NonNull Player sender, @Nullable String guildTag) {
        return updateGuildTagLegacy(sender, guildTag).thenApply(GuildResults.UpdateTag::from);
    }

    private @NonNull CompletableFuture<GuildResult> updateGuildTagLegacy(@NonNull Player sender, @Nullable String guildTag) {
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
                    if (!isValidGuildTag(guildTag)) {
                        return CompletableFuture.completedFuture(GuildResult.INVALID_GUILD_TAG);
                    }
                    return storage.tryUpdateGuildTag(guild.guildId(), senderId, guildTag)
                            .thenApply(outcome -> switch (outcome) {
                                case UpdateGuildTagOutcome.Updated ignored ->
                                        GuildResult.GUILD_TAG_UPDATED;
                                case UpdateGuildTagOutcome.GuildNotFound ignored ->
                                        GuildResult.GUILD_NOT_FOUND;
                                case UpdateGuildTagOutcome.NotLeader ignored ->
                                        GuildResult.NOT_LEADER;
                                case UpdateGuildTagOutcome.GuildTagAlreadyExists ignored ->
                                        GuildResult.GUILD_TAG_ALREADY_EXISTS;
                            });
                });
    }

    public @NonNull CompletableFuture<GuildResults.UpdateColor> updateGuildColor(@NonNull Player sender, @Nullable String guildColor) {
        return updateGuildColorLegacy(sender, guildColor).thenApply(GuildResults.UpdateColor::from);
    }

    private @NonNull CompletableFuture<GuildResult> updateGuildColorLegacy(@NonNull Player sender, @Nullable String guildColor) {
        UUID senderId = sender.getUniqueId();
        return storage.getPlayerGuild(senderId)
                .thenCompose(guildOptional -> {
                    if (guildOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(GuildResult.NOT_IN_GUILD);
                    }
                    Guild guild = guildOptional.get();
                    GuildRank senderRank = guild.findMemberRank(senderId)
                            .orElse(GuildRank.OUTCAST);
                    if (!senderRank.canUpdateColor()) {
                        return CompletableFuture.completedFuture(GuildResult.INSUFFICIENT_RANK);
                    }
                    if (guildColor == null || !ALLOWED_GUILD_COLORS.contains(guildColor.toLowerCase())) {
                        return CompletableFuture.completedFuture(GuildResult.INVALID_GUILD_COLOR);
                    }
                    String formattedColor = "<" + guildColor.toLowerCase() + ">";
                    return storage.updateGuildColor(guild.guildId(), senderId, formattedColor)
                            .thenApply(outcome -> switch (outcome) {
                                case UpdateGuildColorOutcome.Updated ignored ->
                                        GuildResult.GUILD_COLOR_UPDATED;
                                case UpdateGuildColorOutcome.InsufficientRank ignored ->
                                        GuildResult.INSUFFICIENT_RANK;
                                case UpdateGuildColorOutcome.GuildNotFound ignored ->
                                        GuildResult.GUILD_NOT_FOUND;
                            });
                });
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

    private @NonNull CompletableFuture<GuildResult> updateGroupPrivacy(
            @NonNull UUID playerId,
            @NonNull String value
    ) {
        return storage.getPlayerGuild(playerId)
                .thenCompose(guildOptional -> {
                    if (guildOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(GuildResult.NOT_IN_GUILD);
                    }
                    Optional<GroupJoinPolicy> joinPolicy = GroupJoinPolicy.fromInput(value);
                    if (joinPolicy.isEmpty()) {
                        return CompletableFuture.completedFuture(GuildResult.INVALID_SETTING);
                    }
                    return storage.updateJoinPolicy(
                                    guildOptional.get().guildId(),
                                    playerId,
                                    joinPolicy.get()
                            )
                            .thenApply(outcome -> switch (outcome) {
                                case UpdateGuildJoinPolicyOutcome.Updated ignored ->
                                        GuildResult.SETTING_UPDATED;
                                case UpdateGuildJoinPolicyOutcome.InsufficientRank ignored ->
                                        GuildResult.INSUFFICIENT_RANK;
                                case UpdateGuildJoinPolicyOutcome.GuildNotFound ignored ->
                                        GuildResult.GUILD_NOT_FOUND;
                            });
                });
    }

    public @NonNull CompletableFuture<GuildInfoResult> getGuildInfo(@NonNull Player sender) {
        UUID senderId = sender.getUniqueId();
        return storage.getPlayerGuild(senderId)
                .thenApply(guildOptional -> guildOptional
                        .<GuildInfoResult>map(GuildInfoResult.Found::new)
                        .orElseGet(GuildInfoResult.NotInGuild::new));
    }

    public @NonNull CompletableFuture<GuildInfoResult> getGuildInfoByName(@NonNull String guildName) {
        return storage.fetchGuildByName(guildName)
                .thenApply(guildOptional -> guildOptional
                        .<GuildInfoResult>map(GuildInfoResult.Found::new)
                        .orElseGet(GuildInfoResult.NotFound::new));
    }

    public @NonNull CompletableFuture<Optional<Guild>> fetchGuild(@NonNull UUID guildId) {
        return storage.fetchGuild(guildId);
    }

    public @NonNull CompletableFuture<Map<UUID, PlayerRecord>> fetchPlayersByUuids(
            @NonNull Collection<UUID> playerIds) {
        return storage.fetchPlayersByUuids(playerIds);
    }

    public @NonNull CompletableFuture<Void> handlePlayerJoin(@NonNull UUID playerId, @NonNull String username) {
        return storage.upsertPlayer(playerId, username);
    }

    private @NonNull CompletableFuture<Optional<PlayerRecord>> resolveTargetPlayer(@NonNull String username) {
        Optional<Player> onlinePlayer = proxyServer.getPlayer(username);
        if (onlinePlayer.isPresent()) {
            Player player = onlinePlayer.get();
            return CompletableFuture.completedFuture(
                    Optional.of(new PlayerRecord(player.getUniqueId(), player.getUsername())));
        }
        return storage.fetchPlayerByUsername(username);
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
