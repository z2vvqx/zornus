package net.valoury.staff.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.utilities.StringUtils;
import net.valoury.staff.proxy.StaffProxyConstants;
import net.valoury.staff.proxy.model.StaffInspection;
import net.valoury.staff.proxy.service.StaffService;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

public final class StaffInspectCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(StaffInspectCommand.class);

    public static @NonNull LiteralArgumentBuilder<CommandSource> create(
            StaffService staffService,
            ProxyServer proxyServer
    ) {
        return BrigadierCommand.literalArgumentBuilder("inspect")
                .requires(source -> source.hasPermission(
                        StaffProxyConstants.INSPECT_COMMAND_PERMISSION
                ))
                .executes(context -> {
                    context.getSource().sendMessage(
                            StringUtils.deserialize(StaffProxyConstants.USAGE_INSPECT)
                    );
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder(
                                "player_name",
                                StringArgumentType.word()
                        )
                        .suggests(onlinePlayerSuggestions(proxyServer))
                        .executes(context -> inspectPlayer(
                                context,
                                staffService,
                                proxyServer
                        ))
                );
    }

    private static @NonNull SuggestionProvider<CommandSource> onlinePlayerSuggestions(
            ProxyServer proxyServer
    ) {
        return (context, builder) -> {
            String remainingInput = builder.getRemainingLowerCase();
            if (remainingInput.isEmpty()) {
                return builder.buildFuture();
            }
            proxyServer.getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(username -> username.toLowerCase(Locale.ROOT)
                            .startsWith(remainingInput))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    private static int inspectPlayer(
            @NonNull CommandContext<CommandSource> context,
            @NonNull StaffService staffService,
            @NonNull ProxyServer proxyServer
    ) {
        CommandSource source = context.getSource();
        String targetName = StringArgumentType.getString(context, "player_name");
        staffService.inspect(targetName)
                .thenAccept(inspection -> {
                    if (inspection.isEmpty()) {
                        source.sendMessage(StringUtils.deserialize(
                                SharedConstants.PLAYER_NOT_FOUND
                        ));
                        return;
                    }
                    source.sendMessage(createDisplay(inspection.get(), proxyServer));
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to inspect staff connection target {}", targetName, throwable);
                    source.sendMessage(StringUtils.deserialize(
                            SharedConstants.ERROR_UNEXPECTED
                    ));
                    return null;
                });
        return Command.SINGLE_SUCCESS;
    }

    static @NonNull Component createDisplay(
            @NonNull StaffInspection inspection,
            @NonNull ProxyServer proxyServer
    ) {
        TextComponent.Builder message = Component.text()
                .appendNewline()
                .append(detailLine("Player", Component.text(
                        inspection.target().username()
                )))
                .appendNewline()
                .append(detailLine("UUID", Component.text(
                        inspection.target().playerUuid().toString()
                )))
                .appendNewline()
                .append(detailLine(
                        "Status",
                        statusComponent(inspection, proxyServer)
                ))
                .appendNewline()
                .append(detailLine(
                        "Window",
                        Component.text(StaffProxyConstants.CONNECTION_RETENTION.toDays()
                                + " days")
                ))
                .appendNewline()
                .append(detailLine(
                        "Connection Events",
                        Component.text(inspection.connectionCount())
                ))
                .appendNewline()
                .append(detailLine(
                        "Address IDs",
                        Component.text(inspection.connections().size())
                ))
                .appendNewline()
                .append(detailLine(
                        "Latest IP",
                        latestAddressIdentifier(inspection)
                ))
                .appendNewline()
                .append(detailLine(
                        "Related Accounts",
                        Component.text(inspection.relatedAccounts().size())
                ))
                .appendNewline()
                .append(detailLine(
                        "First Seen",
                        timestampComponent(inspection.firstSeenAt())
                ))
                .appendNewline()
                .append(detailLine(
                        "Last Seen",
                        timestampComponent(inspection.lastSeenAt())
                ))
                .appendNewline();
        return message.build();
    }

    private static @NonNull Component latestAddressIdentifier(
            @NonNull StaffInspection inspection
    ) {
        if (inspection.connections().isEmpty()) {
            return Component.text("None");
        }
        return Component.text(inspection.connections()
                .getFirst()
                .addressFingerprint()
                .displayIdentifier());
    }

    private static @NonNull Component statusComponent(
            @NonNull StaffInspection inspection,
            @NonNull ProxyServer proxyServer
    ) {
        Optional<Player> onlinePlayer = proxyServer.getPlayer(
                inspection.target().playerUuid()
        );
        if (onlinePlayer.isEmpty()) {
            return StringUtils.deserialize("<red>Offline</red>");
        }
        String serverName = onlinePlayer.get().getCurrentServer()
                .map(connection -> connection.getServerInfo().getName())
                .orElse(null);
        return serverName == null
                ? StringUtils.deserialize("<green>Online</green>")
                : StringUtils.deserialize(
                        "<green>Online on <server></green>",
                        Placeholder.unparsed("server", serverName)
                );
    }

    private static @NonNull Component timestampComponent(
            @NonNull Optional<Instant> timestamp
    ) {
        return timestamp.<Component>map(StringUtils::formatRelativeTime)
                .orElseGet(() -> Component.text("None"));
    }

    private static @NonNull Component detailLine(
            @NonNull String key,
            @NonNull Component value
    ) {
        return StringUtils.deserialize(
                SharedConstants.BULLET_POINT + StaffProxyConstants.UI_DETAIL_ENTRY,
                TagResolver.resolver(
                        Placeholder.unparsed("key", key),
                        Placeholder.component("value", value)
                )
        );
    }
}
