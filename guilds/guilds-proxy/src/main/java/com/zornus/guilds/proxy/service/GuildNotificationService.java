package com.zornus.guilds.proxy.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.zornus.guilds.proxy.GuildProxyConstants;
import com.zornus.guilds.proxy.model.Guild;
import com.zornus.guilds.proxy.model.GuildSettings;
import com.zornus.shared.utilities.StringUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jspecify.annotations.NonNull;

import java.util.*;

public final class GuildNotificationService {

    private final @NonNull ProxyServer proxyServer;

    public GuildNotificationService(@NonNull ProxyServer proxyServer) {
        this.proxyServer = proxyServer;
    }

    public void notifyMemberJoined(@NonNull Guild guild, @NonNull Player sender) {
        Component message = StringUtils.deserialize(GuildProxyConstants.NOTIFICATION_MEMBER_JOINED,
                TagResolver.resolver(Placeholder.unparsed("sender", sender.getUsername())));
        broadcastToGuild(guild, message, sender.getUniqueId());
    }

    public void notifyMemberLeft(@NonNull Guild guild, @NonNull UUID memberId, @NonNull String memberName) {
        Component message = StringUtils.deserialize(GuildProxyConstants.NOTIFICATION_MEMBER_LEFT,
                TagResolver.resolver(Placeholder.unparsed("sender", memberName)));
        broadcastToGuild(guild, message);
    }

    public void notifyMemberKicked(@NonNull Guild guild, @NonNull String memberName, @NonNull String kickerName) {
        Component message = StringUtils.deserialize(GuildProxyConstants.NOTIFICATION_MEMBER_KICKED,
                TagResolver.resolver(
                        Placeholder.unparsed("member", memberName),
                        Placeholder.unparsed("kicker", kickerName)));
        broadcastToGuild(guild, message);
    }

    public void notifyLeadershipTransferred(@NonNull Guild guild, @NonNull UUID oldLeaderId, @NonNull Player newLeader) {
        String oldLeaderName = proxyServer.getPlayer(oldLeaderId)
                .map(Player::getUsername)
                .orElse("Unknown");

        Component message = StringUtils.deserialize(GuildProxyConstants.NOTIFICATION_LEADERSHIP_TRANSFERRED,
                TagResolver.resolver(
                        Placeholder.unparsed("sender", oldLeaderName),
                        Placeholder.unparsed("member", newLeader.getUsername())));
        broadcastToGuild(guild, message);
    }

    public void sendInviteReceived(@NonNull Player target, @NonNull Player sender, @NonNull Guild guild) {
        Component message = StringUtils.deserialize(GuildProxyConstants.NOTIFICATION_INVITE_RECEIVED,
                TagResolver.resolver(
                        Placeholder.unparsed("player", sender.getUsername()),
                        Placeholder.unparsed("guild", guild.guildName())));
        target.sendMessage(message);
    }

    public void announceInviteSent(@NonNull Guild guild, @NonNull Player sender, @NonNull Player target) {
        Component message = StringUtils.deserialize(GuildProxyConstants.NOTIFICATION_INVITE_SENT_ANNOUNCEMENT,
                TagResolver.resolver(
                        Placeholder.unparsed("sender", sender.getUsername()),
                        Placeholder.unparsed("target", target.getUsername())));
        broadcastToGuild(guild, message, sender.getUniqueId());
    }

    public void notifyGuildDisbanded(@NonNull Guild guild, @NonNull UUID leaderId) {
        String leaderName = proxyServer.getPlayer(leaderId)
                .map(Player::getUsername)
                .orElse("Unknown");
        Component message = StringUtils.deserialize(GuildProxyConstants.NOTIFICATION_GUILD_DISBANDED,
                TagResolver.resolver(Placeholder.unparsed("leader", leaderName)));
        broadcastToGuild(guild, message, leaderId);
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

    private void broadcastToGuild(@NonNull Guild guild, @NonNull Component message, @NonNull UUID... excludedMemberIds) {
        Set<UUID> exclusions = excludedMemberIds == null || excludedMemberIds.length == 0
                ? Collections.emptySet()
                : new HashSet<>(Arrays.asList(excludedMemberIds));

        Set<UUID> memberIds = guild.getMemberIds();
        if (memberIds.isEmpty()) return;

        for (UUID memberId : memberIds) {
            if (!exclusions.contains(memberId)) {
                proxyServer.getPlayer(memberId).ifPresent(member -> member.sendMessage(message));
            }
        }
    }
}
