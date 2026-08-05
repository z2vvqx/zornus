package net.valoury.guilds.proxy.command;

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

class GuildCommandSuggestionTest {

    private static final List<String> PLAYER_ARGUMENT_PATHS = List.of(
            "guild invite",
            "guild revoke",
            "guild kick",
            "guild transfer",
            "guild promote",
            "guild demote"
    );
    private static final Set<String> GUILD_COLORS = Set.of(
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
            "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple",
            "yellow", "white"
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
    void colorSuggestionsListAllColorsAndFilterTypedPrefixes() {
        CommandDispatcher<CommandSource> dispatcher = dispatcher(new AtomicInteger());

        assertEquals(GUILD_COLORS, suggestionSet(dispatcher, "guild color "));
        assertEquals(Set.of("black", "blue"), suggestionSet(dispatcher, "guild color b"));
        assertEquals(Set.of("aqua"), suggestionSet(dispatcher, "guild color aq"));
    }

    @Test
    void otherFiniteValueSuggestionsListAllValuesAndFilterTypedPrefixes() {
        CommandDispatcher<CommandSource> dispatcher = dispatcher(new AtomicInteger());

        assertEquals(
                Set.of("all", "friend", "none"),
                suggestionSet(dispatcher, "guild settings invites ")
        );
        assertEquals(
                Set.of("friend"),
                suggestionSet(dispatcher, "guild settings invites f")
        );
        assertEquals(
                Set.of("private", "public"),
                suggestionSet(dispatcher, "guild settings privacy ")
        );
        assertEquals(
                Set.of("private"),
                suggestionSet(dispatcher, "guild settings privacy pr")
        );
        assertEquals(
                Set.of("false", "true"),
                suggestionSet(dispatcher, "guild settings chat ")
        );
        assertEquals(Set.of("confirm"), suggestionSet(dispatcher, "guild delete "));
        assertEquals(
                Set.of("confirm"),
                suggestionSet(dispatcher, "guild rename NewGuild c")
        );
        assertEquals(
                Set.of("confirm"),
                suggestionSet(dispatcher, "guild transfer Alpha c")
        );
    }

    private static CommandDispatcher<CommandSource> dispatcher(
            AtomicInteger onlinePlayerListingCount
    ) {
        CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(GuildCommand.create(
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
