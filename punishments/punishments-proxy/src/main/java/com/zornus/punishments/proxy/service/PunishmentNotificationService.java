package com.zornus.punishments.proxy.service;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.zornus.punishments.proxy.PunishmentProxyConstants;
import com.zornus.punishments.proxy.model.Punishment;
import com.zornus.punishments.proxy.model.PunishmentType;
import com.zornus.shared.model.PlayerRecord;
import com.zornus.shared.utilities.StringUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

public final class PunishmentNotificationService {
    private final ProxyServer proxyServer;

    public PunishmentNotificationService(@NonNull ProxyServer proxyServer) {
        this.proxyServer = proxyServer;
    }

    public void broadcastPunishment(@NonNull CommandSource source, @NonNull PlayerRecord target,
                                    @NonNull Punishment punishment) {
        String imposingName = source instanceof Player player
                ? player.getUsername()
                : PunishmentProxyConstants.CONSOLE_NAME;
        TagResolver resolver = TagResolver.resolver(
                Placeholder.unparsed("punisher", imposingName),
                Placeholder.unparsed("target", target.username()),
                Placeholder.unparsed("reason", punishment.reason()));
        Component message = StringUtils.deserialize(publicMessage(punishment), resolver);
        UUID sourcePlayerId = source instanceof Player player ? player.getUniqueId() : null;
        proxyServer.getAllPlayers().stream()
                .filter(player -> sourcePlayerId == null || !player.getUniqueId().equals(sourcePlayerId))
                .filter(player -> !player.getUniqueId().equals(target.playerUuid()))
                .forEach(player -> player.sendMessage(message));
    }

    public void notifyVictim(@NonNull Player target, @NonNull Punishment punishment) {
        sendVictimMessage(target, punishment);
    }

    public void enforce(@NonNull Player target, @NonNull Punishment punishment) {
        switch (punishment.type()) {
            case BAN -> target.disconnect(StringUtils.deserialize(PunishmentProxyConstants.ENFORCEMENT_BANNED));
            case KICK -> target.disconnect(StringUtils.deserialize(
                    PunishmentProxyConstants.NOTIFICATION_KICK_VICTIM,
                    Placeholder.unparsed("reason", punishment.reason())));
            case MUTE, WARN -> {
            }
        }
    }

    public void notifyDeferred(@NonNull Player target, @NonNull Punishment punishment) {
        if (punishment.type() == PunishmentType.MUTE || punishment.type() == PunishmentType.WARN) {
            sendVictimMessage(target, punishment);
        }
    }

    private void sendVictimMessage(Player target, Punishment punishment) {
        target.sendMessage(StringUtils.deserialize(
                victimMessage(punishment),
                TagResolver.resolver(
                        Placeholder.unparsed("reason", punishment.reason()),
                        Placeholder.unparsed("id", punishment.identifier().toUpperCase()))));
    }

    private String victimMessage(Punishment punishment) {
        return switch (punishment.type()) {
            case BAN -> PunishmentProxyConstants.NOTIFICATION_BAN_VICTIM;
            case MUTE -> PunishmentProxyConstants.NOTIFICATION_MUTE_VICTIM;
            case WARN -> PunishmentProxyConstants.NOTIFICATION_WARN_VICTIM;
            case KICK -> PunishmentProxyConstants.NOTIFICATION_KICK_VICTIM;
        };
    }

    private String publicMessage(Punishment punishment) {
        return switch (punishment.type()) {
            case BAN -> PunishmentProxyConstants.NOTIFICATION_BAN_PUBLIC;
            case MUTE -> PunishmentProxyConstants.NOTIFICATION_MUTE_PUBLIC;
            case WARN -> PunishmentProxyConstants.NOTIFICATION_WARN_PUBLIC;
            case KICK -> PunishmentProxyConstants.NOTIFICATION_KICK_PUBLIC;
        };
    }
}
