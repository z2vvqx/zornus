package net.valoury.punishments.proxy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.Suggestion;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.valoury.punishments.proxy.PunishmentPresets;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PunishmentCommandSuggestionTest {

    private static final List<String> PLAYER_ARGUMENT_PATHS = List.of(
            "punishment impose ban",
            "punishment impose mute",
            "punishment impose warn",
            "punishment impose kick",
            "punishment impose preset",
            "punishment revoke ban",
            "punishment revoke mute",
            "punishment check ban",
            "punishment check mute",
            "punishment history"
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
    void presetSuggestionsListAllPresetsAndFilterTypedPrefixes() {
        CommandDispatcher<CommandSource> dispatcher = dispatcher(new AtomicInteger());
        Set<String> presetNames = new TreeSet<>(PunishmentPresets.names());
        Set<String> presetsStartingWithS = PunishmentPresets.names().stream()
                .filter(presetName -> presetName.startsWith("s"))
                .collect(Collectors.toCollection(TreeSet::new));

        assertEquals(
                presetNames,
                suggestionSet(dispatcher, "punishment impose preset Alpha ")
        );
        assertEquals(
                presetsStartingWithS,
                suggestionSet(dispatcher, "punishment impose preset Alpha s")
        );
    }

    private static CommandDispatcher<CommandSource> dispatcher(
            AtomicInteger onlinePlayerListingCount
    ) {
        CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(PunishmentCommand.create(
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
        CommandSource source = permission -> Tristate.TRUE;
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
