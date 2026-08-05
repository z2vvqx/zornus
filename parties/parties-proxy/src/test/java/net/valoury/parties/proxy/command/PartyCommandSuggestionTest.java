package net.valoury.parties.proxy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.Suggestion;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PartyCommandSuggestionTest {

    private static final List<String> PLAYER_ARGUMENT_PATHS = List.of(
            "party invite",
            "party accept",
            "party reject",
            "party uninvite",
            "party kick",
            "party transfer",
            "party join",
            "party promote",
            "party demote"
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

    @Test
    void finiteValueSuggestionsListAllValuesAndFilterTypedPrefixes() {
        CommandDispatcher<CommandSource> dispatcher = dispatcher(new AtomicInteger());

        assertEquals(
                Set.of("all", "friend", "none"),
                suggestionSet(dispatcher, "party settings invites ")
        );
        assertEquals(
                Set.of("friend"),
                suggestionSet(dispatcher, "party settings invites f")
        );
        assertEquals(
                Set.of("private", "public"),
                suggestionSet(dispatcher, "party settings privacy ")
        );
        assertEquals(
                Set.of("private"),
                suggestionSet(dispatcher, "party settings privacy pr")
        );
        assertEquals(
                Set.of("false", "true"),
                suggestionSet(dispatcher, "party settings chat ")
        );
        assertEquals(
                Set.of("true"),
                suggestionSet(dispatcher, "party settings chat t")
        );
        assertEquals(
                Set.of("confirm"),
                suggestionSet(dispatcher, "party disband ")
        );
        assertEquals(
                Set.of("confirm"),
                suggestionSet(dispatcher, "party transfer Alpha c")
        );
    }

    private static CommandDispatcher<CommandSource> dispatcher(
            AtomicInteger onlinePlayerListingCount
    ) {
        CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(PartyCommand.create(
                null,
                proxyServer(onlinePlayerListingCount)
        ).getNode());
        return dispatcher;
    }

    private static Set<String> suggestionSet(
            CommandDispatcher<CommandSource> dispatcher,
            String input
    ) {
        return new TreeSet<>(suggestions(dispatcher, input));
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
