package com.zornus.friends.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.zornus.friends.proxy.FriendProxyConstants;
import com.zornus.friends.proxy.model.FriendRelation;
import com.zornus.friends.proxy.model.FriendSettings;
import com.zornus.friends.proxy.model.PresenceState;
import com.zornus.friends.proxy.model.result.FriendResult;
import com.zornus.friends.proxy.model.result.FriendListResult;
import com.zornus.friends.proxy.service.FriendService;
import com.zornus.shared.SharedConstants;
import com.zornus.shared.utilities.PaginationResult;
import com.zornus.shared.utilities.StringUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Command for listing friends with pagination.
 */
public final class FriendListCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(FriendListCommand.class);

    public static LiteralArgumentBuilder<CommandSource> create(FriendService friendService, ProxyServer proxyServer) {
        return BrigadierCommand
                .literalArgumentBuilder("list")
                .executes(context -> handleListFriends(context, friendService, proxyServer, 1))
                .then(BrigadierCommand
                        .requiredArgumentBuilder("page_index", IntegerArgumentType.integer(1))
                        .executes(context -> {
                            int page = IntegerArgumentType.getInteger(context, "page_index");
                            return handleListFriends(context, friendService, proxyServer, page);
                        })
                );
    }

    private static int handleListFriends(@NonNull CommandContext<CommandSource> context, FriendService friendService, ProxyServer proxyServer, int page) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        friendService.getFriendsList(sender.getUniqueId(), page)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to fetch friends list for player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return new FriendListResult(FriendResult.ERROR_ALREADY_HANDLED, PaginationResult.invalidPage(1));
                })
                .thenAccept(result -> {
                    switch (result.result()) {
                        case LIST_EMPTY ->
                                sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.UI_LIST_EMPTY));
                        case INVALID_PAGE -> {
                            TagResolver pageResolver = TagResolver.resolver(Placeholder.unparsed("maximum_pages", String.valueOf(result.paginationResult().maximumPages())));
                            sender.sendMessage(StringUtils.deserialize(SharedConstants.INVALID_PAGE, pageResolver));
                        }
                        case SUCCESS -> handleDisplayList(sender, result, friendService, proxyServer, page);
                        case ERROR_ALREADY_HANDLED -> {}
                        default -> sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                });

        return Command.SINGLE_SUCCESS;
    }

    private static void handleDisplayList(Player sender, @NonNull FriendListResult result,
                                                FriendService friendService, ProxyServer proxyServer, int currentPage) {
        TextComponent.Builder messageBuilder = Component.text().appendNewline();
        List<FriendRelation> items = result.paginationResult().items();
        Component[] friendEntries = new Component[items.size()];

        List<CompletableFuture<Void>> friendDataFutures = new ArrayList<>();

        int index = 0;
        for (FriendRelation relation : items) {
            final int currentIndex = index;
            UUID friendId = relation.getOtherPlayerUuid(sender.getUniqueId());
            String friendName = relation.getOtherPlayerUsername(sender.getUniqueId());

            boolean isActuallyOnline = proxyServer.getPlayer(friendId).isPresent();

            CompletableFuture<FriendSettings> settingsFuture = friendService.getSettings(friendId);
            CompletableFuture<Optional<Instant>> lastSeenFuture = friendService.fetchLastSeen(friendId);

            CompletableFuture<Void> entryFuture = settingsFuture
                    .exceptionally(throwable -> {
                        LOGGER.error("Failed to fetch settings for friend {}", friendId, throwable);
                        return new FriendSettings(friendId);
                    })
                    .thenCombine(lastSeenFuture.exceptionally(throwable -> {
                        LOGGER.error("Failed to fetch last seen for friend {}", friendId, throwable);
                        return Optional.empty();
                    }), (settings, lastSeenOptional) -> {
                        boolean friendAppearsOffline = settings.presenceState() == PresenceState.OFFLINE;
                        boolean friendShowsLastSeen = settings.showLastSeen();
                        boolean friendShowsLocation = settings.showLocation();

                        Component entryComponent;
                        if (isActuallyOnline && !friendAppearsOffline) {
                            Optional<Player> friendPlayer = proxyServer.getPlayer(friendId);
                            Optional<String> serverName = friendPlayer.flatMap(player -> player.getCurrentServer().map(server -> server.getServerInfo().getName()));

                            if (friendShowsLocation && serverName.isPresent()) {
                                entryComponent = StringUtils.deserialize(SharedConstants.BULLET_POINT + FriendProxyConstants.UI_STATUS_ONLINE_WITH_LOCATION,
                                        TagResolver.resolver(
                                                Placeholder.parsed("friend", friendName),
                                                Placeholder.unparsed("server", serverName.get())
                                        ));
                            } else {
                                entryComponent = StringUtils.deserialize(SharedConstants.BULLET_POINT + FriendProxyConstants.UI_STATUS_ONLINE,
                                        Placeholder.unparsed("friend", friendName));
                            }
                        } else {
                            if (lastSeenOptional.isPresent() && friendShowsLastSeen) {
                                Component timestampComponent = StringUtils.formatRelativeTime(lastSeenOptional.get());
                                entryComponent = StringUtils.deserialize(SharedConstants.BULLET_POINT + FriendProxyConstants.UI_STATUS_OFFLINE,
                                        TagResolver.resolver(
                                                Placeholder.unparsed("friend", friendName),
                                                Placeholder.component("timestamp", timestampComponent)
                                        ));
                            } else {
                                entryComponent = StringUtils.deserialize(SharedConstants.BULLET_POINT + FriendProxyConstants.UI_STATUS_OFFLINE_NO_DATA,
                                        Placeholder.unparsed("friend", friendName));
                            }
                        }
                        friendEntries[currentIndex] = entryComponent;
                        return null;
                    });

            friendDataFutures.add(entryFuture);
            index++;
        }

        CompletableFuture.allOf(friendDataFutures.toArray(new CompletableFuture[0]))
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to build friend list for player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                })
                .thenAccept(ignored -> {
                    messageBuilder.append(Component.join(JoinConfiguration.newlines(), Arrays.asList(friendEntries)));
                    messageBuilder.append(Component.newline());

                    if (result.paginationResult().hasMultiplePages()) {
                        messageBuilder.append(Component.newline())
                                .append(StringUtils.deserialize(FriendProxyConstants.UI_LIST_PAGINATION,
                                        TagResolver.resolver(
                                                Placeholder.unparsed("current_page", String.valueOf(currentPage)),
                                                Placeholder.unparsed("maximum_pages", String.valueOf(result.paginationResult().maximumPages()))
                                        )
                                ))
                                .append(Component.newline());
                    }

                    sender.sendMessage(messageBuilder.build());
                });
    }
}
