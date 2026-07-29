package net.valoury.bloodstone.server.service;

import net.valoury.bloodstone.server.BloodstoneServerConstants;
import net.valoury.bloodstone.server.BloodstoneText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Contract;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class BloodstoneMessageService {

    private static final String UNABLE_TO_PROCEED_PREFIX =
            "Unable to proceed <dark_gray>─</dark_gray> ";
    private static final long ERROR_COOLDOWN_NANOSECONDS =
            TimeUnit.SECONDS.toNanos(1);

    private final Map<ErrorKey, Long> lastErrors = new HashMap<>();

    public boolean sendError(
            Player player,
            String errorTemplate,
            TagResolver... resolvers
    ) {
        return sendError(player, errorTemplate, errorTemplate, resolvers);
    }

    public boolean sendError(
            Player player,
            String errorKey,
            String errorTemplate,
            TagResolver... resolvers
    ) {
        ErrorKey key = new ErrorKey(player.getUniqueId(), errorKey);
        long now = System.nanoTime();
        Long previous = lastErrors.get(key);
        if (previous != null
                && now - previous < ERROR_COOLDOWN_NANOSECONDS) {
            return false;
        }
        lastErrors.put(key, now);
        Component error = BloodstoneText.deserialize(errorTemplate, resolvers);
        BloodstoneText.sendMessage(
                player,
                BloodstoneServerConstants.ERROR_FORMAT,
                Placeholder.component("error", error)
        );
        player.playSound(player.getLocation(), Sound.VILLAGER_NO, 0.7F, 1.0F);
        return true;
    }

    public void sendUnable(
            Player player,
            String reasonTemplate,
            TagResolver... resolvers
    ) {
        sendError(
                player,
                UNABLE_TO_PROCEED_PREFIX + reasonTemplate,
                resolvers
        );
    }

    public void sendUnable(
            Player player,
            String errorKey,
            String reasonTemplate,
            TagResolver... resolvers
    ) {
        sendError(
                player,
                errorKey,
                UNABLE_TO_PROCEED_PREFIX + reasonTemplate,
                resolvers
        );
    }

    public void sendRequiredCurrency(Player player, int amount, Currency currency) {
        sendUnable(
                player,
                BloodstoneServerConstants.REQUIRED_CURRENCY_FORMAT,
                TagResolver.resolver(
                        Placeholder.unparsed("amount", Integer.toString(amount)),
                        Placeholder.unparsed("currency", currency.displayName())
                )
        );
    }

    public void clear(UUID playerId) {
        lastErrors.keySet().removeIf(key -> key.playerId().equals(playerId));
    }

    public void clear() {
        lastErrors.clear();
    }

    public enum Currency {
        BLOOD("blood"),
        BLOOD_ALLOY("blood alloy");

        private final String displayName;

        Currency(String displayName) {
            this.displayName = displayName;
        }

        @Contract(pure = true)
        public String displayName() {
            return displayName;
        }
    }

    private record ErrorKey(UUID playerId, String error) {
    }
}
