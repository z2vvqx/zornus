package net.valoury.staff.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.utilities.PaginationResult;
import net.valoury.shared.utilities.StringUtils;
import net.valoury.staff.proxy.StaffProxyConstants;
import net.valoury.staff.proxy.model.ConnectionSummary;
import net.valoury.staff.proxy.model.StaffInspection;
import net.valoury.staff.proxy.service.StaffService;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class StaffConnectionsCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(
            StaffConnectionsCommand.class
    );

    public static @NonNull LiteralArgumentBuilder<CommandSource> create(
            StaffService staffService,
            ProxyServer proxyServer
    ) {
        return BrigadierCommand.literalArgumentBuilder("connections")
                .requires(source -> source.hasPermission(
                        StaffProxyConstants.CONNECTIONS_COMMAND_PERMISSION
                ))
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(
                            StaffProxyConstants.USAGE_CONNECTIONS
                    ));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder(
                                "player_name",
                                StringArgumentType.word()
                        )
                        .suggests(onlinePlayerSuggestions(proxyServer))
                        .executes(context -> displayConnections(
                                context,
                                staffService,
                                1
                        ))
                        .then(BrigadierCommand
                                .requiredArgumentBuilder(
                                        "page",
                                        IntegerArgumentType.integer(1)
                                )
                                .executes(context -> displayConnections(
                                        context,
                                        staffService,
                                        IntegerArgumentType.getInteger(
                                                context,
                                                "page"
                                        )
                                ))
                        )
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

    private static int displayConnections(
            @NonNull CommandContext<CommandSource> context,
            @NonNull StaffService staffService,
            int page
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
                    sendConnections(source, inspection.get(), page);
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to fetch staff connections for {}", targetName, throwable);
                    source.sendMessage(StringUtils.deserialize(
                            SharedConstants.ERROR_UNEXPECTED
                    ));
                    return null;
                });
        return Command.SINGLE_SUCCESS;
    }

    static void sendConnections(
            @NonNull CommandSource source,
            @NonNull StaffInspection inspection,
            int page
    ) {
        if (inspection.connections().isEmpty()) {
            source.sendMessage(StringUtils.deserialize(
                    StaffProxyConstants.UI_CONNECTIONS_EMPTY
            ));
            return;
        }
        PaginationResult<ConnectionSummary> pagination = PaginationResult.paginate(
                inspection.connections(),
                page,
                SharedConstants.ENTRIES_PER_PAGE
        );
        if (!pagination.isValidPage()) {
            source.sendMessage(StringUtils.deserialize(
                    SharedConstants.INVALID_PAGE,
                    Placeholder.unparsed(
                            "maximum_pages",
                            String.valueOf(pagination.maximumPages())
                    )
            ));
            return;
        }

        List<Component> entries = new ArrayList<>();
        for (ConnectionSummary connection : pagination.items()) {
            Component accountCount = Component.text(
                    connection.associatedAccountCount() + " account(s)"
            ).clickEvent(ClickEvent.suggestCommand(
                    "/staff related "
                            + inspection.target().username()
                            + " "
                            + connection.addressFingerprint().displayIdentifier()
            ));
            Component summaryLine = StringUtils.deserialize(
                    SharedConstants.BULLET_POINT
                            + StaffProxyConstants.UI_CONNECTION_ENTRY,
                    TagResolver.resolver(
                            Placeholder.unparsed(
                                    "identifier",
                                    connection.addressFingerprint().displayIdentifier()
                            ),
                            Placeholder.unparsed(
                                    "connections",
                                    String.valueOf(connection.connectionCount())
                            ),
                            Placeholder.component("accounts", accountCount)
                    )
            );
            Component timespanLine = StringUtils.deserialize(
                    StaffProxyConstants.UI_ENTRY_TIMESPAN,
                    TagResolver.resolver(
                            Placeholder.component(
                                    "first_seen",
                                    StringUtils.formatRelativeTime(connection.firstSeenAt())
                            ),
                            Placeholder.component(
                                    "last_seen",
                                    StringUtils.formatRelativeTime(connection.lastSeenAt())
                            )
                    )
            );
            entries.add(summaryLine.appendNewline().append(timespanLine));
        }
        TextComponent.Builder message = Component.text()
                .appendNewline()
                .append(Component.join(JoinConfiguration.newlines(), entries));
        if (pagination.hasMultiplePages()) {
            message.appendNewline()
                    .appendNewline()
                    .append(StringUtils.deserialize(
                            StaffProxyConstants.UI_CONNECTIONS_PAGINATION,
                            TagResolver.resolver(
                                    Placeholder.unparsed(
                                            "current_page",
                                            String.valueOf(page)
                                    ),
                                    Placeholder.unparsed(
                                            "maximum_pages",
                                            String.valueOf(pagination.maximumPages())
                                    ),
                                    Placeholder.unparsed(
                                            "target",
                                            inspection.target().username()
                                    )
                            )
                    ));
        }
        source.sendMessage(message.appendNewline().build());
    }
}
