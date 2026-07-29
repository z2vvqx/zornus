package net.valoury.friends.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.valoury.friends.proxy.FriendProxyConstants;
import net.valoury.friends.proxy.model.FriendRelation;
import net.valoury.friends.proxy.model.FriendSettings;
import net.valoury.friends.proxy.model.PresenceState;
import net.valoury.friends.proxy.model.result.FriendListResult;
import net.valoury.friends.proxy.service.FriendService;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.utilities.PaginationResult;
import net.valoury.shared.utilities.StringUtils;
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
        return createCommand("list", friendService, proxyServer);
    }

    public static @NonNull BrigadierCommand createShortcut(FriendService friendService, ProxyServer proxyServer) {
        return new BrigadierCommand(createCommand("fl", friendService, proxyServer)
                .requires(source -> source instanceof Player));
    }

    private static LiteralArgumentBuilder<CommandSource> createCommand(
            String commandName,
            FriendService friendService,
            ProxyServer proxyServer
    ) {
        return BrigadierCommand
                .literalArgumentBuilder(commandName)
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
                .thenAccept(result -> {
                    switch (result) {
                        case FriendListResult.Empty ignored ->
                                sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.UI_LIST_EMPTY));
                        case FriendListResult.InvalidPage invalidPage -> {
                            TagResolver pageResolver = TagResolver.resolver(Placeholder.unparsed(
                                    "maximum_pages", String.valueOf(invalidPage.pagination().maximumPages())));
                            sender.sendMessage(StringUtils.deserialize(SharedConstants.INVALID_PAGE, pageResolver));
                        }
                        case FriendListResult.Found found ->
                                handleDisplayList(sender, found.pagination(), friendService, proxyServer, page);
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to fetch friends list for player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }

    private static void handleDisplayList(
            @NonNull Player sender,
            @NonNull PaginationResult<FriendRelation> pagination,
            @NonNull FriendService friendService,
            @NonNull ProxyServer proxyServer,
            int currentPage
    ) {
        TextComponent.Builder messageBuilder = Component.text().appendNewline();
        List<FriendRelation> items = pagination.items();
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

                    if (pagination.hasMultiplePages()) {
                        messageBuilder.append(Component.newline())
                                .append(StringUtils.deserialize(FriendProxyConstants.UI_LIST_PAGINATION,
                                        TagResolver.resolver(
                                                Placeholder.unparsed("current_page", String.valueOf(currentPage)),
                                                Placeholder.unparsed("maximum_pages", String.valueOf(pagination.maximumPages()))
                                        )
                                ))
                                .append(Component.newline());
                    }

                    sender.sendMessage(messageBuilder.build());
                });
    }
}
