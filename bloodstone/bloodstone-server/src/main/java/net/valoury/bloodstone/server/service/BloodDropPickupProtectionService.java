package net.valoury.bloodstone.server.service;

import org.bukkit.entity.Item;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public final class BloodDropPickupProtectionService {

    private static final String EXCLUDED_PLAYER_METADATA_KEY =
            "valoury_bloodstone_excluded_player";

    private final Plugin plugin;

    public BloodDropPickupProtectionService(Plugin plugin) {
        this.plugin = Objects.requireNonNull(
                plugin,
                "Plugin cannot be null"
        );
    }

    public void preventPickupBy(Item droppedBlood, UUID playerId) {
        Objects.requireNonNull(droppedBlood, "Dropped Blood cannot be null");
        Objects.requireNonNull(playerId, "Player id cannot be null");
        droppedBlood.setMetadata(
                EXCLUDED_PLAYER_METADATA_KEY,
                new FixedMetadataValue(plugin, playerId)
        );
    }

    public boolean isPickupPrevented(Item droppedBlood, UUID playerId) {
        Objects.requireNonNull(droppedBlood, "Dropped Blood cannot be null");
        Objects.requireNonNull(playerId, "Player id cannot be null");
        return playerId.equals(excludedPlayerId(droppedBlood));
    }

    public boolean hasPickupRestriction(Item droppedBlood) {
        Objects.requireNonNull(droppedBlood, "Dropped Blood cannot be null");
        return excludedPlayerId(droppedBlood) != null;
    }

    public void clearPickupRestriction(Item droppedBlood) {
        Objects.requireNonNull(droppedBlood, "Dropped Blood cannot be null");
        droppedBlood.removeMetadata(EXCLUDED_PLAYER_METADATA_KEY, plugin);
    }

    public boolean hasConflictingPickupRestriction(
            Item firstDrop,
            Item secondDrop
    ) {
        Objects.requireNonNull(firstDrop, "First Blood drop cannot be null");
        Objects.requireNonNull(secondDrop, "Second Blood drop cannot be null");
        return arePickupRestrictionsConflicting(
                excludedPlayerId(firstDrop),
                excludedPlayerId(secondDrop)
        );
    }

    static boolean arePickupRestrictionsConflicting(
            @Nullable UUID firstExcludedPlayerId,
            @Nullable UUID secondExcludedPlayerId
    ) {
        return !Objects.equals(
                firstExcludedPlayerId,
                secondExcludedPlayerId
        );
    }

    private @Nullable UUID excludedPlayerId(Item droppedBlood) {
        for (MetadataValue metadataValue
                : droppedBlood.getMetadata(EXCLUDED_PLAYER_METADATA_KEY)) {
            if (metadataValue.getOwningPlugin() != plugin) {
                continue;
            }
            if (metadataValue.value() instanceof UUID playerId) {
                return playerId;
            }
        }
        return null;
    }
}
