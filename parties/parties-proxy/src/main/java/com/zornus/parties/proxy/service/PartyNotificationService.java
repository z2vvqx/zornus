package com.zornus.parties.proxy.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.zornus.parties.proxy.PartyProxyConstants;
import com.zornus.parties.proxy.model.Party;
import com.zornus.parties.proxy.model.PartySettings;
import com.zornus.shared.utilities.StringUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PartyNotificationService {

    private final @NonNull ProxyServer proxyServer;

    public PartyNotificationService(@NonNull ProxyServer proxyServer) {
        this.proxyServer = proxyServer;
    }

    public void notifyMemberDisconnected(@NonNull Party party, @NonNull UUID playerId, @NonNull String playerName) {
        Component message = StringUtils.deserialize(PartyProxyConstants.NOTIFICATION_MEMBER_DISCONNECTED,
                TagResolver.resolver(Placeholder.unparsed("player", playerName)));
        broadcastToParty(party, message);
    }

    public void notifyLeaderDisconnected(@NonNull Party party, @NonNull UUID oldLeaderId, @NonNull String oldLeaderName) {
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

    public void notifyMemberLeft(@NonNull Party party, @NonNull String memberName, @NonNull UUID excludedMemberId) {
        Component message = StringUtils.deserialize(PartyProxyConstants.NOTIFICATION_MEMBER_LEFT,
                TagResolver.resolver(Placeholder.unparsed("sender", memberName)));
        broadcastToParty(party, message, excludedMemberId);
    }

    public void notifyMemberKicked(@NonNull Party party, @NonNull Player member, @Nullable String reason) {
        TagResolver broadcastResolver;
        String broadcastMessageConstant;
        String kickedPlayerMessageConstant;

        if (reason != null && !reason.trim().isEmpty()) {
            broadcastResolver = TagResolver.resolver(
                    Placeholder.unparsed("member", member.getUsername()),
                    Placeholder.unparsed("reason", reason));
            broadcastMessageConstant = PartyProxyConstants.NOTIFICATION_MEMBER_KICKED_WITH_REASON;
            kickedPlayerMessageConstant = PartyProxyConstants.NOTIFICATION_YOU_WERE_KICKED_WITH_REASON;
        } else {
            broadcastResolver = TagResolver.resolver(Placeholder.unparsed("member", member.getUsername()));
            broadcastMessageConstant = PartyProxyConstants.NOTIFICATION_MEMBER_KICKED;
            kickedPlayerMessageConstant = PartyProxyConstants.NOTIFICATION_YOU_WERE_KICKED;
        }

        Component broadcastMessage = StringUtils.deserialize(broadcastMessageConstant, broadcastResolver);
        broadcastToParty(party, broadcastMessage, member.getUniqueId());

        TagResolver kickedPlayerResolver = (reason != null && !reason.trim().isEmpty())
                ? TagResolver.resolver(Placeholder.unparsed("reason", reason))
                : TagResolver.empty();
        Component kickedPlayerMessage = StringUtils.deserialize(kickedPlayerMessageConstant, kickedPlayerResolver);
        member.sendMessage(kickedPlayerMessage);
    }

    public void sendInviteReceived(@NonNull Player target, @NonNull Player sender, @NonNull Party party) {
        Component message = StringUtils.deserialize(PartyProxyConstants.NOTIFICATION_INVITE_RECEIVED,
                TagResolver.resolver(
                        Placeholder.parsed("player", StringUtils.escapeTags(sender.getUsername()))));
        target.sendMessage(message);
    }

    public void announceInviteSent(@NonNull Party party, @NonNull Player sender, @NonNull Player target) {
        Component message = StringUtils.deserialize(PartyProxyConstants.NOTIFICATION_INVITE_SENT_ANNOUNCEMENT,
                TagResolver.resolver(
                        Placeholder.unparsed("sender", sender.getUsername()),
                        Placeholder.unparsed("target", target.getUsername())));
        broadcastToParty(party, message, sender.getUniqueId());
    }

    public void notifyLeadershipTransferred(@NonNull Party party, @NonNull String oldLeaderName, @NonNull Player newLeader) {
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

    private void broadcastToParty(@NonNull Party party, @NonNull Component message) {
        Set<UUID> memberIds = party.getMemberIds();
        if (memberIds.isEmpty()) return;

        for (UUID memberId : memberIds) {
            proxyServer.getPlayer(memberId).ifPresent(member -> member.sendMessage(message));
        }
    }

    private void broadcastToParty(@NonNull Party party, @NonNull Component message, @NonNull UUID excludedMemberId) {
        Set<UUID> memberIds = party.getMemberIds();
        if (memberIds.isEmpty()) return;

        for (UUID memberId : memberIds) {
            if (!memberId.equals(excludedMemberId)) {
                proxyServer.getPlayer(memberId).ifPresent(member -> member.sendMessage(message));
            }
        }
    }
}
