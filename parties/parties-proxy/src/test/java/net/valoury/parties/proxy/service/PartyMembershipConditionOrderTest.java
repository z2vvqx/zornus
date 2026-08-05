package net.valoury.parties.proxy.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.luckperms.api.LuckPerms;
import net.valoury.friends.api.FriendshipService;
import net.valoury.parties.proxy.PartyProxyConstants;
import net.valoury.parties.proxy.model.result.PartyResults;
import net.valoury.parties.proxy.storage.PartyStorage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class PartyMembershipConditionOrderTest {

    @Test
    void rejectsMissingPartyBeforeMemberOnlyInputValidation() {
        Player sender = player(UUID.randomUUID());
        PartyService service = createService();

        assertInstanceOf(
                PartyResults.UpdateSetting.NotInParty.class,
                service.updateGroupPrivacy(sender.getUniqueId(), "invalid").join()
        );
        assertInstanceOf(
                PartyResults.SendChat.NotInParty.class,
                service.sendPartyChat(
                        sender,
                        "x".repeat(PartyProxyConstants.MAX_MESSAGE_LENGTH + 1)
                ).join()
        );
        assertInstanceOf(
                PartyResults.KickMember.NotInParty.class,
                service.kickMember(sender, null, null).join()
        );
        assertInstanceOf(
                PartyResults.ChangeModeratorRole.NotInParty.class,
                service.promoteModerator(sender, null).join()
        );
        assertInstanceOf(
                PartyResults.TransferLeadership.NotInParty.class,
                service.transferLeadership(sender, null, false).join()
        );
    }

    @Test
    void describesTheRequiredPartyActionInComparableErrors() {
        assertEquals(
                "<red>You must be in a party to use party chat.</red>",
                PartyProxyConstants.CHAT_ERROR_NOT_IN_PARTY
        );
        assertEquals(
                "<red>You must be in a party to manage moderator roles.</red>",
                PartyProxyConstants.ROLE_ERROR_NOT_IN_PARTY
        );
        assertEquals(
                "<red>You must be in a party to change its privacy.</red>",
                PartyProxyConstants.SETTINGS_ERROR_NOT_IN_PARTY
        );
    }

    private static PartyService createService() {
        PartyStorage storage = proxy(PartyStorage.class, (instance, method, arguments) -> {
            if (method.getName().equals("getPlayerParty")) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            throw new AssertionError("Unexpected storage call: " + method.getName());
        });
        FriendshipService friendshipService = (firstPlayerId, secondPlayerId) -> {
            throw new AssertionError("Unexpected friendship lookup");
        };
        return new PartyService(
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
