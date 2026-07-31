package net.valoury.guilds.proxy.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.valoury.shared.model.PlayerRecord;
import net.valoury.guilds.proxy.GuildProxyConstants;
import net.valoury.guilds.proxy.model.Guild;
import net.valoury.guilds.proxy.model.GuildRank;
import net.valoury.guilds.proxy.model.GuildRankChangeDirection;
import net.valoury.guilds.proxy.model.GuildSettings;
import net.valoury.guilds.proxy.storage.GuildStorage;
import net.valoury.guilds.proxy.utilities.GuildColorFormatter;
import net.valoury.shared.utilities.StringUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.luckperms.api.LuckPerms;
import net.valoury.shared.utilities.PlayerNameFormatter;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class GuildNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuildNotificationService.class);
    private final @NonNull GuildStorage storage;
    private final @NonNull ProxyServer proxyServer;
    private final @NonNull LuckPerms luckPerms;

    public GuildNotificationService(
            @NonNull GuildStorage storage,
            @NonNull ProxyServer proxyServer,
            @NonNull LuckPerms luckPerms
    ) {
        this.storage = storage;
        this.proxyServer = proxyServer;
        this.luckPerms = luckPerms;
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
        Component broadcastMessage = StringUtils.deserialize(GuildProxyConstants.NOTIFICATION_MEMBER_KICKED,
                TagResolver.resolver(
                        Placeholder.unparsed("member", memberName),
                        Placeholder.unparsed("kicker", kickerName)));
        broadcastToGuild(guild, broadcastMessage, kickedMemberId);

        Component kickedPlayerMessage = StringUtils.deserialize(GuildProxyConstants.NOTIFICATION_YOU_WERE_KICKED,
                TagResolver.resolver(Placeholder.unparsed("kicker", kickerName)));
        proxyServer.getPlayer(kickedMemberId).ifPresent(kickedPlayer -> kickedPlayer.sendMessage(kickedPlayerMessage));
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

    public void notifyMemberRankChanged(
            @NonNull Guild guild,
            @NonNull String memberName,
            @NonNull String actorName,
            @NonNull GuildRank newRank,
            @NonNull GuildRankChangeDirection direction
    ) {
        String template = direction == GuildRankChangeDirection.PROMOTION
                ? GuildProxyConstants.NOTIFICATION_MEMBER_PROMOTED
                : GuildProxyConstants.NOTIFICATION_MEMBER_DEMOTED;
        Component message = StringUtils.deserialize(
                template,
                TagResolver.resolver(
                        Placeholder.unparsed("member", memberName),
                        Placeholder.unparsed("actor", actorName),
                        Placeholder.unparsed("rank", newRank.displayName())
                )
        );
        broadcastToGuild(guild, message);
    }

    public void sendGuildChat(@NonNull Guild guild, @NonNull Player sender, @NonNull String message,
                              @NonNull Map<UUID, GuildSettings> settingsMap) {
        GuildRank senderRank = guild.findMemberRank(sender.getUniqueId())
                .orElse(GuildRank.OUTCAST);
        Component rankTag = senderRank.chatTagInitial()
                .<Component>map(initial -> Component.text("[" + initial + "] ", NamedTextColor.GRAY))
                .orElseGet(Component::empty);
        Component componentMessage = StringUtils.deserialize(GuildProxyConstants.NOTIFICATION_CHAT_FORMAT,
                TagResolver.resolver(
                        Placeholder.component(
                                "guild",
                                GuildColorFormatter.createColoredText(guild.guildName(), guild.guildColor())
                        ),
                        Placeholder.component("playername", resolvePlayerName(sender)),
                        Placeholder.component("rank_tag", rankTag),
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

    private @NonNull Component resolvePlayerName(@NonNull Player player) {
        Component username = Component.text(player.getUsername());
        try {
            String suffix = luckPerms.getPlayerAdapter(Player.class)
                    .getMetaData(player)
                    .getSuffix();
            return PlayerNameFormatter.formatSuffixBeforeName(suffix, username);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Failed to resolve LuckPerms suffix for {}; using username without suffix",
                    player.getUniqueId(),
                    exception
            );
            return username;
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
