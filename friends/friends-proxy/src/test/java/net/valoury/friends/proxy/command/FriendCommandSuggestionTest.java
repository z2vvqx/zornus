package net.valoury.friends.proxy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.Suggestion;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FriendCommandSuggestionTest {

    private static final List<String> PLAYER_ARGUMENT_PATHS = List.of(
            "friend add",
            "friend accept",
            "friend reject",
            "friend revoke",
            "friend remove",
            "friend message",
            "friend jump"
    );

    @Test
    void playerSuggestionsRequireAFirstCharacterWithoutListingPlayers() {
        AtomicInteger onlinePlayerListingCount = new AtomicInteger();
        CommandDispatcher<CommandSource> dispatcher = dispatcher(onlinePlayerListingCount);

        for (String commandPath : PLAYER_ARGUMENT_PATHS) {
            assertEquals(List.of(), suggestions(dispatcher, commandPath + " "));
        }

        assertEquals(0, onlinePlayerListingCount.get());
    }

    @Test
    void playerSuggestionsMatchTheTypedPrefixForEveryPlayerArgument() {
        CommandDispatcher<CommandSource> dispatcher = dispatcher(new AtomicInteger());

        for (String commandPath : PLAYER_ARGUMENT_PATHS) {
            assertEquals(
                    List.of("Alpha", "Alpine"),
                    suggestions(dispatcher, commandPath + " al")
            );
        }
    }

    private static CommandDispatcher<CommandSource> dispatcher(
            AtomicInteger onlinePlayerListingCount
    ) {
        CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(FriendCommand.create(
                null,
                proxyServer(onlinePlayerListingCount)
        ).getNode());
        return dispatcher;
    }

    private static List<String> suggestions(
            CommandDispatcher<CommandSource> dispatcher,
            String input
    ) {
        CommandSource source = player("Tester");
        return dispatcher.getCompletionSuggestions(dispatcher.parse(input, source))
                .join()
                .getList()
                .stream()
                .map(Suggestion::getText)
                .toList();
    }

    private static ProxyServer proxyServer(AtomicInteger onlinePlayerListingCount) {
        List<Player> onlinePlayers = List.of(
                player("Beta"),
                player("Alpine"),
                player("Alpha")
        );
        return proxy(ProxyServer.class, (instance, method, arguments) -> {
            if (method.getName().equals("getAllPlayers")) {
                onlinePlayerListingCount.incrementAndGet();
                return onlinePlayers;
            }
            throw new AssertionError("Unexpected proxy call: " + method.getName());
        });
    }

    private static Player player(String username) {
        return proxy(Player.class, (instance, method, arguments) -> {
            if (method.getName().equals("getUsername")) {
                return username;
            }
            throw new AssertionError("Unexpected player call: " + method.getName());
        });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                handler
        ));
    }
}
