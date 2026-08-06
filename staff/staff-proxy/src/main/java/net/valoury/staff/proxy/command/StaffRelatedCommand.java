package net.valoury.staff.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
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
import net.valoury.staff.proxy.model.AddressFingerprint;
import net.valoury.staff.proxy.model.ConnectionSummary;
import net.valoury.staff.proxy.model.RelatedAccount;
import net.valoury.staff.proxy.model.StaffInspection;
import net.valoury.staff.proxy.service.StaffService;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class StaffRelatedCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(StaffRelatedCommand.class);
    private static final Pattern ADDRESS_IDENTIFIER_PATTERN = Pattern.compile(
            "IP-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}",
            Pattern.CASE_INSENSITIVE
    );
    private static final SimpleCommandExceptionType INVALID_ADDRESS_IDENTIFIER =
            new SimpleCommandExceptionType(new LiteralMessage(
                    "Expected an address identifier such as IP-1234-5678-9ABC"
            ));
    private static final ArgumentType<String> ADDRESS_IDENTIFIER_ARGUMENT = reader -> {
        int startingCursor = reader.getCursor();
        String identifier = reader.readUnquotedString();
        if (!ADDRESS_IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            reader.setCursor(startingCursor);
            throw INVALID_ADDRESS_IDENTIFIER.createWithContext(reader);
        }
        return identifier.toUpperCase(Locale.ROOT);
    };

    public static @NonNull LiteralArgumentBuilder<CommandSource> create(
            StaffService staffService,
            ProxyServer proxyServer
    ) {
        return BrigadierCommand.literalArgumentBuilder("related")
                .requires(source -> source.hasPermission(
                        StaffProxyConstants.RELATED_COMMAND_PERMISSION
                ))
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(
                            StaffProxyConstants.USAGE_RELATED
                    ));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder(
                                "player_name",
                                StringArgumentType.word()
                        )
                        .suggests(onlinePlayerSuggestions(proxyServer))
                        .executes(context -> displayRelatedAccounts(
                                context,
                                staffService,
                                null,
                                1
                        ))
                        .then(BrigadierCommand
                                .requiredArgumentBuilder(
                                        "page",
                                        IntegerArgumentType.integer(1)
                                )
                                .executes(context -> displayRelatedAccounts(
                                        context,
                                        staffService,
                                        null,
                                        IntegerArgumentType.getInteger(
                                                context,
                                                "page"
                                        )
                                ))
                        )
                        .then(BrigadierCommand
                                .requiredArgumentBuilder(
                                        "address_identifier",
                                        ADDRESS_IDENTIFIER_ARGUMENT
                                )
                                .executes(context -> displayRelatedAccounts(
                                        context,
                                        staffService,
                                        context.getArgument(
                                                "address_identifier",
                                                String.class
                                        ),
                                        1
                                ))
                                .then(BrigadierCommand
                                        .requiredArgumentBuilder(
                                                "filtered_page",
                                                IntegerArgumentType.integer(1)
                                        )
                                        .executes(context -> displayRelatedAccounts(
                                                context,
                                                staffService,
                                                context.getArgument(
                                                        "address_identifier",
                                                        String.class
                                                ),
                                                IntegerArgumentType.getInteger(
                                                        context,
                                                        "filtered_page"
                                                )
                                        ))
                                )
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

    private static int displayRelatedAccounts(
            @NonNull CommandContext<CommandSource> context,
            @NonNull StaffService staffService,
            @Nullable String addressIdentifier,
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
                    sendRelatedAccounts(
                            source,
                            inspection.get(),
                            addressIdentifier,
                            page
                    );
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to fetch related staff accounts for {}", targetName, throwable);
                    source.sendMessage(StringUtils.deserialize(
                            SharedConstants.ERROR_UNEXPECTED
                    ));
                    return null;
                });
        return Command.SINGLE_SUCCESS;
    }

    static void sendRelatedAccounts(
            @NonNull CommandSource source,
            @NonNull StaffInspection inspection,
            int page
    ) {
        sendRelatedAccounts(source, inspection, null, page);
    }

    static void sendRelatedAccounts(
            @NonNull CommandSource source,
            @NonNull StaffInspection inspection,
            @Nullable String addressIdentifier,
            int page
    ) {
        List<RelatedAccount> relatedAccounts = inspection.relatedAccounts();
        if (addressIdentifier != null) {
            List<ConnectionSummary> matchingConnections = inspection.connections().stream()
                    .filter(connection -> connection.addressFingerprint()
                            .displayIdentifier()
                            .equalsIgnoreCase(addressIdentifier))
                    .toList();
            if (matchingConnections.isEmpty()) {
                source.sendMessage(StringUtils.deserialize(
                        StaffProxyConstants.UI_RELATED_CONNECTION_NOT_FOUND
                ));
                return;
            }
            if (matchingConnections.size() > 1) {
                source.sendMessage(StringUtils.deserialize(
                        StaffProxyConstants.UI_RELATED_CONNECTION_AMBIGUOUS
                ));
                return;
            }
            AddressFingerprint selectedAddressFingerprint = matchingConnections
                    .getFirst()
                    .addressFingerprint();
            relatedAccounts = relatedAccounts.stream()
                    .filter(account -> account.directlySharedAddressFingerprints()
                            .contains(selectedAddressFingerprint))
                    .toList();
        }

        if (relatedAccounts.isEmpty()) {
            source.sendMessage(StringUtils.deserialize(
                    addressIdentifier == null
                            ? StaffProxyConstants.UI_RELATED_EMPTY
                            : StaffProxyConstants.UI_RELATED_CONNECTION_EMPTY
            ));
            return;
        }
        PaginationResult<RelatedAccount> pagination = PaginationResult.paginate(
                relatedAccounts,
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
        for (RelatedAccount account : pagination.items()) {
            String entryTemplate = account.direct()
                    ? StaffProxyConstants.UI_RELATED_DIRECT_ENTRY
                    : StaffProxyConstants.UI_RELATED_INDIRECT_ENTRY;
            Component target = Component.text(account.username())
                    .clickEvent(ClickEvent.suggestCommand(
                            "/staff inspect " + account.username()
                    ))
                    .hoverEvent(Component.text(account.playerUuid().toString()));
            Component summaryLine = StringUtils.deserialize(
                    SharedConstants.BULLET_POINT + entryTemplate,
                    TagResolver.resolver(
                            Placeholder.component("target", target),
                            Placeholder.unparsed(
                                    "connections",
                                    String.valueOf(account.directlySharedConnectionCount())
                            ),
                            Placeholder.unparsed(
                                    "via",
                                    account.connectedThroughUsername() == null
                                            ? ""
                                            : account.connectedThroughUsername()
                            ),
                            Placeholder.unparsed(
                                    "depth",
                                    String.valueOf(account.connectionDepth())
                            )
                    )
            );
            Component timespanLine = StringUtils.deserialize(
                    StaffProxyConstants.UI_ENTRY_TIMESPAN,
                    TagResolver.resolver(
                            Placeholder.component(
                                    "first_seen",
                                    StringUtils.formatRelativeTime(account.firstSeenAt())
                            ),
                            Placeholder.component(
                                    "last_seen",
                                    StringUtils.formatRelativeTime(account.lastSeenAt())
                            )
                    )
            );
            entries.add(summaryLine.appendNewline().append(timespanLine));
        }
        TextComponent.Builder message = Component.text()
                .appendNewline()
                .append(Component.join(JoinConfiguration.newlines(), entries));
        if (pagination.hasMultiplePages()) {
            String paginationTemplate = addressIdentifier == null
                    ? StaffProxyConstants.UI_RELATED_PAGINATION
                    : StaffProxyConstants.UI_RELATED_CONNECTION_PAGINATION;
            message.appendNewline()
                    .appendNewline()
                    .append(StringUtils.deserialize(
                            paginationTemplate,
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
                                    ),
                                    Placeholder.unparsed(
                                            "identifier",
                                            addressIdentifier == null ? "" : addressIdentifier
                                    )
                            )
                    ));
        }
        source.sendMessage(message.appendNewline().build());
    }
}
