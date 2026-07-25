package com.zornus.friends.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.zornus.friends.proxy.FriendProxyConstants;
import com.zornus.friends.proxy.model.FriendRequest;
import com.zornus.friends.proxy.model.result.FriendRequestListResult;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command for viewing pending friend requests with pagination.
 */
public final class FriendRequestsCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(FriendRequestsCommand.class);

    public static LiteralArgumentBuilder<CommandSource> create(FriendService friendService) {
        return BrigadierCommand
                .literalArgumentBuilder("requests")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(FriendProxyConstants.USAGE_REQUESTS));
                    return Command.SINGLE_SUCCESS;
                })
                .then(createTypeBranch("incoming", friendService))
                .then(createTypeBranch("outgoing", friendService));
    }

    private static LiteralArgumentBuilder<CommandSource> createTypeBranch(String type, FriendService friendService) {
        return BrigadierCommand
                .literalArgumentBuilder(type)
                .executes(context -> handleListRequests(context, friendService, type, 1))
                .then(BrigadierCommand
                        .requiredArgumentBuilder("page", IntegerArgumentType.integer(1))
                        .executes(context -> {
                            int page = IntegerArgumentType.getInteger(context, "page");
                            return handleListRequests(context, friendService, type, page);
                        })
                );
    }

    private static int handleListRequests(@NonNull CommandContext<CommandSource> context, FriendService friendService,
                                      @NonNull String type, int page) {
        if (!(context.getSource() instanceof Player sender)) {
            return Command.SINGLE_SUCCESS;
        }

        CompletableFuture<FriendRequestListResult> future = type.equals("incoming")
                ? friendService.getIncomingRequestsList(sender.getUniqueId(), page)
                : friendService.getOutgoingRequestsList(sender.getUniqueId(), page);

        future.thenAccept(result -> {
                    switch (result) {
                        case FriendRequestListResult.Empty ignored -> {
                            String emptyMessage = type.equalsIgnoreCase("incoming")
                                    ? FriendProxyConstants.UI_REQUESTS_INCOMING_EMPTY
                                    : FriendProxyConstants.UI_REQUESTS_OUTGOING_EMPTY;
                            sender.sendMessage(StringUtils.deserialize(emptyMessage));
                        }
                        case FriendRequestListResult.InvalidPage invalidPage -> {
                            TagResolver pageResolver = TagResolver.resolver(Placeholder.unparsed(
                                    "maximum_pages", String.valueOf(invalidPage.pagination().maximumPages())));
                            sender.sendMessage(StringUtils.deserialize(SharedConstants.INVALID_PAGE, pageResolver));
                        }
                        case FriendRequestListResult.Found found ->
                                handleDisplayRequestsPage(sender, found.pagination(), type, page);
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to get friend requests for player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }

    private static void handleDisplayRequestsPage(
            @NonNull Player sender,
            @NonNull PaginationResult<FriendRequest> pagination,
            @NonNull String type,
            int currentPage
    ) {
        TextComponent.Builder messageBuilder = Component.text().appendNewline();

        boolean isIncoming = type.equalsIgnoreCase("incoming");
        String entryFormat = isIncoming
                ? FriendProxyConstants.UI_REQUESTS_INCOMING_ENTRY
                : FriendProxyConstants.UI_REQUESTS_OUTGOING_ENTRY;

        List<Component> requestEntries = new ArrayList<>();
        for (FriendRequest request : pagination.items()) {
            String playerName = isIncoming ? request.senderUsername() : request.receiverUsername();
            Component timestampComponent = StringUtils.formatRelativeTime(request.timestamp());

            requestEntries.add(StringUtils.deserialize(
                    SharedConstants.BULLET_POINT + entryFormat,
                    TagResolver.resolver(
                            Placeholder.parsed("player", StringUtils.escapeTags(playerName)),
                            Placeholder.component("timestamp", timestampComponent)
                    )
            ));
        }

        messageBuilder.append(Component.join(JoinConfiguration.newlines(), requestEntries));
        messageBuilder.append(Component.newline());

        if (pagination.hasMultiplePages()) {
            messageBuilder.append(Component.newline())
                    .append(StringUtils.deserialize(FriendProxyConstants.UI_REQUESTS_PAGINATION,
                            TagResolver.resolver(
                                    Placeholder.unparsed("current_page", String.valueOf(currentPage)),
                                    Placeholder.unparsed("maximum_pages", String.valueOf(pagination.maximumPages())),
                                    Placeholder.unparsed("type", type)
                            )
                    ))
                    .append(Component.newline());
        }

        sender.sendMessage(messageBuilder.build());
    }
}
