package com.zornus.parties.proxy.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.zornus.parties.proxy.PartyProxyConstants;
import com.zornus.parties.proxy.model.Party;
import com.zornus.parties.proxy.model.PartySettings;
import com.zornus.parties.proxy.storage.PartyStorage;
import com.zornus.shared.utilities.StringUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class PartyNotificationService {

    private final @NonNull PartyStorage storage;
    private final @NonNull ProxyServer proxyServer;

    public PartyNotificationService(@NonNull PartyStorage storage, @NonNull ProxyServer proxyServer) {
        this.storage = storage;
        this.proxyServer = proxyServer;
    }

    public void notifyMemberDisconnected(@NonNull Party party, @NonNull UUID playerId) {
        String playerName = proxyServer.getPlayer(playerId)
                .map(Player::getUsername)
                .orElse("Unknown");
        Component message = StringUtils.deserialize(PartyProxyConstants.NOTIFICATION_MEMBER_DISCONNECTED,
                TagResolver.resolver(Placeholder.unparsed("player", playerName)));
        broadcastToParty(party, message);
    }

    public void notifyLeaderDisconnected(@NonNull Party party, @NonNull UUID oldLeaderId) {
        String oldLeaderName = proxyServer.getPlayer(oldLeaderId)
                .map(Player::getUsername)
                .orElse("Unknown");
        String newLeaderName = proxyServer.getPlayer(party.leaderId())
                .map(Player::getUsername)
                .orElse("Unknown");
        Component message = StringUtils.deserialize(PartyProxyConstants.NOTIFICATION_LEADER_DISCONNECTED,
                TagResolver.resolver(
                        Placeholder.unparsed("old_leader", oldLeaderName),
                        Placeholder.unparsed("new_leader", newLeaderName)));
        broadcastToParty(party, message);
    }

    public void notifyMemberJoined(@NonNull Party party, @NonNull Player sender) {
        Component message = StringUtils.deserialize(PartyProxyConstants.NOTIFICATION_MEMBER_JOINED,
                TagResolver.resolver(Placeholder.unparsed("sender", sender.getUsername())));
        broadcastToParty(party, message, sender.getUniqueId());
    }

    public void notifyMemberLeft(@NonNull Party party, @NonNull UUID memberId) {
        String memberName = proxyServer.getPlayer(memberId)
                .map(Player::getUsername)
                .orElse("Unknown");
        Component message = StringUtils.deserialize(PartyProxyConstants.NOTIFICATION_MEMBER_LEFT,
                TagResolver.resolver(Placeholder.unparsed("sender", memberName)));
        broadcastToParty(party, message);
    }

    public void notifyMemberKicked(@NonNull Party party, @NonNull Player member, @Nullable String reason) {
        TagResolver resolver;
        String messageConstant;

        if (reason != null && !reason.trim().isEmpty()) {
            resolver = TagResolver.resolver(
                    Placeholder.unparsed("member", member.getUsername()),
                    Placeholder.unparsed("reason", reason));
            messageConstant = PartyProxyConstants.NOTIFICATION_MEMBER_KICKED_WITH_REASON;
        } else {
            resolver = TagResolver.resolver(Placeholder.unparsed("member", member.getUsername()));
            messageConstant = PartyProxyConstants.NOTIFICATION_MEMBER_KICKED;
        }

        Component message = StringUtils.deserialize(messageConstant, resolver);
        broadcastToParty(party, message, member.getUniqueId());
    }

    public void sendInviteReceived(@NonNull Player target, @NonNull Player sender, @NonNull Party party) {
        Component message = StringUtils.deserialize(PartyProxyConstants.NOTIFICATION_INVITE_RECEIVED,
                TagResolver.resolver(
                        Placeholder.unparsed("player", sender.getUsername())));
        target.sendMessage(message);
    }

    public void announceInviteSent(@NonNull Party party, @NonNull Player sender, @NonNull Player target) {
        Component message = StringUtils.deserialize(PartyProxyConstants.NOTIFICATION_INVITE_SENT_ANNOUNCEMENT,
                TagResolver.resolver(
                        Placeholder.unparsed("sender", sender.getUsername()),
                        Placeholder.unparsed("target", target.getUsername())));
        broadcastToParty(party, message, sender.getUniqueId());
    }

    public void notifyLeadershipTransferred(@NonNull Party party, @NonNull UUID oldLeaderId, @NonNull Player newLeader) {
        String oldLeaderName = proxyServer.getPlayer(oldLeaderId)
                .map(Player::getUsername)
                .orElse("Unknown");

        Component message = StringUtils.deserialize(PartyProxyConstants.NOTIFICATION_LEADERSHIP_TRANSFERRED,
                TagResolver.resolver(
                        Placeholder.unparsed("sender", oldLeaderName),
                        Placeholder.unparsed("member", newLeader.getUsername())));
        broadcastToParty(party, message);
    }

    public void notifyMemberWarped(@NonNull Player member, @NonNull Player sender) {
        Component message = StringUtils.deserialize(PartyProxyConstants.NOTIFICATION_MEMBER_WARPED,
                TagResolver.resolver(Placeholder.unparsed("sender", sender.getUsername())));
        member.sendMessage(message);
    }

    public void notifyPartyDisbanded(@NonNull Party party, @NonNull UUID leaderId) {
        String leaderName = proxyServer.getPlayer(leaderId)
                .map(Player::getUsername)
                .orElse("Unknown");
        Component message = StringUtils.deserialize(PartyProxyConstants.NOTIFICATION_PARTY_DISBANDED,
                TagResolver.resolver(Placeholder.unparsed("leader", leaderName)));
        broadcastToParty(party, message, leaderId);
    }

    public void sendPartyChat(@NonNull Party party, @NonNull Player sender, @NonNull String message) {
        Component componentMessage = StringUtils.deserialize(PartyProxyConstants.NOTIFICATION_CHAT_FORMAT,
                TagResolver.resolver(
                        Placeholder.unparsed("sender", sender.getUsername()),
                        Placeholder.unparsed("message", message)));

        storage.fetchSettingsForMembers(party.getMemberIds()).thenAccept(settingsMap -> {
            sendPartyChatFiltered(party, sender, message, settingsMap);
        });
    }

    public void sendPartyChatFiltered(@NonNull Party party, @NonNull Player sender, @NonNull String message,
                                        @NonNull Map<UUID, PartySettings> settingsMap) {
        Component componentMessage = StringUtils.deserialize(PartyProxyConstants.NOTIFICATION_CHAT_FORMAT,
                TagResolver.resolver(
                        Placeholder.unparsed("sender", sender.getUsername()),
                        Placeholder.unparsed("message", message)));

        for (UUID memberId : party.getMemberIds()) {
            proxyServer.getPlayer(memberId).ifPresent(member -> {
                PartySettings settings = settingsMap.getOrDefault(memberId, new PartySettings(memberId));
                if (settings.allowChat()) {
                    member.sendMessage(componentMessage);
                }
            });
        }
    }

    private void broadcastToParty(@NonNull Party party, @NonNull Component message, @Nullable UUID... excludedMemberIds) {
        Set<UUID> exclusions = excludedMemberIds == null || excludedMemberIds.length == 0
                ? Collections.emptySet()
                : new HashSet<>(Arrays.asList(excludedMemberIds));

        Set<UUID> memberIds = party.getMemberIds();
        if (memberIds.isEmpty()) return;

        for (UUID memberId : memberIds) {
            if (!exclusions.contains(memberId)) {
                proxyServer.getPlayer(memberId).ifPresent(member -> member.sendMessage(message));
            }
        }
    }
}
