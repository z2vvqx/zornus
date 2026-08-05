package net.valoury.guilds.proxy.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.luckperms.api.LuckPerms;
import net.valoury.friends.api.FriendshipService;
import net.valoury.guilds.proxy.GuildProxyConstants;
import net.valoury.guilds.proxy.model.result.GuildResults;
import net.valoury.guilds.proxy.storage.GuildStorage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class GuildMembershipConditionOrderTest {

    @Test
    void rejectsMissingGuildBeforeColorValidation() {
        GuildService service = createService();

        GuildResults.UpdateColor result = service.updateGuildColor(
                player(UUID.randomUUID()),
                "invalid-color"
        ).join();

        assertInstanceOf(GuildResults.UpdateColor.NotInGuild.class, result);
    }

    @Test
    void rejectsMissingGuildBeforeOtherMemberOnlyInputValidation() {
        Player sender = player(UUID.randomUUID());
        GuildService service = createService();

        assertInstanceOf(
                GuildResults.UpdateTag.NotInGuild.class,
                service.updateGuildTag(sender, "!").join()
        );
        assertInstanceOf(
                GuildResults.Rename.NotInGuild.class,
                service.renameGuild(sender, null, false).join()
        );
        assertInstanceOf(
                GuildResults.UpdateSetting.NotInGuild.class,
                service.updateSettings(sender, "privacy", "invalid").join()
        );
        assertInstanceOf(
                GuildResults.SendChat.NotInGuild.class,
                service.sendGuildChat(
                        sender,
                        "x".repeat(GuildProxyConstants.MAX_MESSAGE_LENGTH + 1)
                ).join()
        );
    }

    @Test
    void describesTheRequiredGuildActionInColorError() {
        assertEquals(
                "<red>You must be in a guild to change its color.</red>",
                GuildProxyConstants.COLOR_ERROR_NOT_IN_GUILD
        );
    }

    private static GuildService createService() {
        GuildStorage storage = proxy(GuildStorage.class, (instance, method, arguments) -> {
            if (method.getName().equals("getPlayerGuild")) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            throw new AssertionError("Unexpected storage call: " + method.getName());
        });
        FriendshipService friendshipService = (firstPlayerId, secondPlayerId) -> {
            throw new AssertionError("Unexpected friendship lookup");
        };
        return new GuildService(
                storage,
                failingProxy(ProxyServer.class),
                friendshipService,
                failingProxy(LuckPerms.class)
        );
    }

    private static Player player(UUID playerId) {
        return proxy(Player.class, (instance, method, arguments) -> {
            if (method.getName().equals("getUniqueId")) {
                return playerId;
            }
            throw new AssertionError("Unexpected player call: " + method.getName());
        });
    }

    private static <T> T failingProxy(Class<T> type) {
        return proxy(type, (instance, method, arguments) -> {
            throw new AssertionError("Unexpected " + type.getSimpleName() + " call: "
                    + method.getName());
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
