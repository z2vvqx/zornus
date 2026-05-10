package com.zornus.guilds.proxy.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.zornus.shared.model.PlayerRecord;
import com.zornus.guilds.proxy.GuildProxyConstants;
import com.zornus.guilds.proxy.model.Guild;
import com.zornus.guilds.proxy.model.GuildSettings;
import com.zornus.guilds.proxy.storage.GuildStorage;
import com.zornus.shared.utilities.StringUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class GuildNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuildNotificationService.class);
    private final @NonNull GuildStorage storage;
    private final @NonNull ProxyServer proxyServer;

    public GuildNotificationService(@NonNull GuildStorage storage, @NonNull ProxyServer proxyServer) {
        this.storage = storage;
        this.proxyServer = proxyServer;
    }

    private CompletableFuture<String> resolvePlayerName(@NonNull UUID playerId) {
        return proxyServer.getPlayer(playerId)
                .map(player -> CompletableFuture.completedFuture(player.getUsername()))
                .orElseGet(() -> storage.fetchPlayersByUuids(Set.of(playerId))
                        .thenApply(players -> players.getOrDefault(playerId,
                                new PlayerRecord(playerId, "Unknown")).username())
                        .exceptionally(throwable -> {
                            LOGGER.error("Failed to resolve player name for {}", playerId, throwable);
                            return "Unknown";
                        }));
    }

    public void notifyMemberJoined(@NonNull Guild guild, @NonNull Player sender) {
        Component message = StringUtils.deserialize(GuildProxyConstants.NOTIFICATION_MEMBER_JOINED,
                TagResolver.resolver(Placeholder.unparsed("sender", sender.getUsername())));
        broadcastToGuild(guild, message, sender.getUniqueId());
    }

    public void notifyMemberLeft(@NonNull Guild guild, @NonNull String memberName, @NonNull UUID excludedMemberId) {
        Component message = StringUtils.deserialize(GuildProxyConstants.NOTIFICATION_MEMBER_LEFT,
                TagResolver.resolver(Placeholder.unparsed("sender", memberName)));
        broadcastToGuild(guild, message, excludedMemberId);
    }

    public void notifyMemberKicked(@NonNull Guild guild, @NonNull UUID kickedMemberId,
                                   @NonNull String memberName, @NonNull String kickerName) {
        Component message = StringUtils.deserialize(GuildProxyConstants.NOTIFICATION_MEMBER_KICKED,
                TagResolver.resolver(
                        Placeholder.unparsed("member", memberName),
                        Placeholder.unparsed("kicker", kickerName)));
        broadcastToGuild(guild, message, kickedMemberId);
    }

    public CompletableFuture<Void> notifyLeadershipTransferred(@NonNull Guild guild,
                                                                @NonNull UUID oldLeaderId,
                                                                @NonNull UUID newLeaderId) {
        return resolvePlayerName(oldLeaderId)
                .thenCombine(resolvePlayerName(newLeaderId), (oldName, newName) -> {
                    Component message = StringUtils.deserialize(
                            GuildProxyConstants.NOTIFICATION_LEADERSHIP_TRANSFERRED,
                            TagResolver.resolver(
                                    Placeholder.unparsed("sender", oldName),
                                    Placeholder.unparsed("member", newName)));
                    broadcastToGuild(guild, message);
                    return null;
                });
    }

    public void sendInviteReceived(@NonNull UUID targetId, @NonNull Player sender, @NonNull Guild guild) {
        proxyServer.getPlayer(targetId).ifPresent(target -> {
            Component message = StringUtils.deserialize(GuildProxyConstants.NOTIFICATION_INVITE_RECEIVED,
                    TagResolver.resolver(
                            Placeholder.parsed("player", StringUtils.escapeTags(sender.getUsername())),
                            Placeholder.unparsed("guild", guild.guildName())));
            target.sendMessage(message);
        });
    }

    public void announceInviteSent(@NonNull Guild guild, @NonNull Player sender, @NonNull String targetUsername) {
        Component message = StringUtils.deserialize(GuildProxyConstants.NOTIFICATION_INVITE_SENT_ANNOUNCEMENT,
                TagResolver.resolver(
                        Placeholder.unparsed("sender", sender.getUsername()),
                        Placeholder.unparsed("target", targetUsername)));
        broadcastToGuild(guild, message, sender.getUniqueId());
    }

    public CompletableFuture<Void> notifyGuildDisbanded(@NonNull Guild guild, @NonNull UUID leaderId) {
        return resolvePlayerName(leaderId).thenAccept(leaderName -> {
            Component message = StringUtils.deserialize(GuildProxyConstants.NOTIFICATION_GUILD_DISBANDED,
                    TagResolver.resolver(Placeholder.unparsed("leader", leaderName)));
            broadcastToGuild(guild, message, leaderId);
        });
    }

    public void notifyGuildRenamed(@NonNull Guild guild, @NonNull String oldName, @NonNull String newName) {
        Component message = StringUtils.deserialize(GuildProxyConstants.NOTIFICATION_GUILD_RENAMED,
                TagResolver.resolver(
                        Placeholder.unparsed("old_name", oldName),
                        Placeholder.unparsed("new_name", newName)));
        broadcastToGuild(guild, message);
    }

    public void sendGuildChat(@NonNull Guild guild, @NonNull Player sender, @NonNull String message,
                              @NonNull Map<UUID, GuildSettings> settingsMap) {
        Component componentMessage = StringUtils.deserialize(GuildProxyConstants.NOTIFICATION_CHAT_FORMAT,
                TagResolver.resolver(
                        Placeholder.unparsed("sender", sender.getUsername()),
                        Placeholder.unparsed("message", message)));

        for (UUID memberId : guild.getMemberIds()) {
            proxyServer.getPlayer(memberId).ifPresent(member -> {
                GuildSettings settings = settingsMap.getOrDefault(memberId, new GuildSettings(memberId));
                if (settings.showChat()) {
                    member.sendMessage(componentMessage);
                }
            });
        }
    }

    private void broadcastToGuild(@NonNull Guild guild, @NonNull Component message) {
        for (UUID memberId : guild.getMemberIds()) {
            proxyServer.getPlayer(memberId).ifPresent(member -> member.sendMessage(message));
        }
    }

    private void broadcastToGuild(@NonNull Guild guild, @NonNull Component message, @NonNull UUID excludedMemberId) {
        for (UUID memberId : guild.getMemberIds()) {
            if (!memberId.equals(excludedMemberId)) {
                proxyServer.getPlayer(memberId).ifPresent(member -> member.sendMessage(message));
            }
        }
    }

    private void broadcastToGuild(@NonNull Guild guild, @NonNull Component message, @NonNull Set<UUID> excludedMemberIds) {
        for (UUID memberId : guild.getMemberIds()) {
            if (!excludedMemberIds.contains(memberId)) {
                proxyServer.getPlayer(memberId).ifPresent(member -> member.sendMessage(message));
            }
        }
    }
}
