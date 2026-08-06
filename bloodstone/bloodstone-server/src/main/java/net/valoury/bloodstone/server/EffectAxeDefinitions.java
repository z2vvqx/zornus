package net.valoury.bloodstone.server;

import net.valoury.bloodstone.server.model.BloodstoneRank;
import org.bukkit.Color;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.util.*;

public final class EffectAxeDefinitions {

    public static final EffectAxeDefinition SPEED = new EffectAxeDefinition(
            "speed",
            "<dark_aqua>Speed Axe</dark_aqua>",
            "<gray>Speed I (00:08)</gray>",
            16,
            PotionEffectType.SPEED,
            0,
            Duration.ofSeconds(8),
            EffectTarget.SELF,
            Color.fromRGB(126, 178, 202)
    );
    public static final EffectAxeDefinition STRENGTH = new EffectAxeDefinition(
            "strength",
            "<dark_aqua>Strength Axe</dark_aqua>",
            "<gray>Strength II (00:08)</gray>",
            32,
            PotionEffectType.INCREASE_DAMAGE,
            1,
            Duration.ofSeconds(8),
            EffectTarget.SELF,
            Color.fromRGB(150, 37, 36)
    );
    public static final EffectAxeDefinition WITHER = new EffectAxeDefinition(
            "wither",
            "<dark_aqua>Wither Axe</dark_aqua>",
            "<gray>Wither III (00:06)</gray>",
            24,
            PotionEffectType.WITHER,
            2,
            Duration.ofSeconds(6),
            EffectTarget.VICTIM,
            Color.fromRGB(53, 42, 39)
    );
    public static final EffectAxeDefinition BLINDNESS = new EffectAxeDefinition(
            "blindness",
            "<dark_aqua>Blindness Axe</dark_aqua>",
            "<gray>Blindness III (00:06)</gray>",
            16,
            PotionEffectType.BLINDNESS,
            2,
            Duration.ofSeconds(6),
            EffectTarget.VICTIM,
            Color.fromRGB(31, 31, 35)
    );
    public static final EffectAxeDefinition WEAKNESS = new EffectAxeDefinition(
            "weakness",
            "<dark_aqua>Weakness Axe</dark_aqua>",
            "<gray>Weakness III (00:06)</gray>",
            16,
            PotionEffectType.WEAKNESS,
            2,
            Duration.ofSeconds(6),
            EffectTarget.VICTIM,
            Color.fromRGB(92, 110, 131)
    );
    public static final EffectAxeDefinition POISON = new EffectAxeDefinition(
            "poison",
            "<dark_aqua>Poison Axe</dark_aqua>",
            "<gray>Poison III (00:06)</gray>",
            24,
            PotionEffectType.POISON,
            2,
            Duration.ofSeconds(6),
            EffectTarget.VICTIM,
            Color.fromRGB(79, 150, 50)
    );

    private static final List<EffectAxeDefinition> DEFINITIONS = List.of(
            SPEED,
            STRENGTH,
            WITHER,
            BLINDNESS,
            WEAKNESS,
            POISON
    );
    private static final Map<String, EffectAxeDefinition> DEFINITIONS_BY_ID = createDefinitionIndex();

    private EffectAxeDefinitions() {
    }

    public static @NonNull List<EffectAxeDefinition> values() {
        return DEFINITIONS;
    }

    public static @NonNull Optional<EffectAxeDefinition> find(@NonNull String id) {
        return Optional.ofNullable(DEFINITIONS_BY_ID.get(id.toLowerCase(Locale.ROOT)));
    }

    private static Map<String, EffectAxeDefinition> createDefinitionIndex() {
        Map<String, EffectAxeDefinition> definitionsById = new LinkedHashMap<>();
        for (EffectAxeDefinition definition : DEFINITIONS) {
            EffectAxeDefinition previous = definitionsById.put(definition.id(), definition);
            if (previous != null) {
                throw new IllegalStateException("Duplicate Effect Axe id: " + definition.id());
            }
        }
        return Map.copyOf(definitionsById);
    }

    public enum EffectTarget {
        SELF,
        VICTIM
    }

    public record EffectAxeDefinition(
            @NonNull String id,
            @NonNull String displayNameTemplate,
            @NonNull String effectLoreTemplate,
            int valorianBloodAlloyCost,
            @NonNull PotionEffectType effectType,
            int amplifier,
            @NonNull Duration duration,
            @NonNull EffectTarget target,
            @NonNull Color particleColor
    ) implements EffectAxeItemDefinition {
        public EffectAxeDefinition {
            if (id.isBlank()) {
                throw new IllegalArgumentException("Effect Axe id cannot be blank");
            }
            id = id.toLowerCase(Locale.ROOT);
            if (amplifier < 0) {
                throw new IllegalArgumentException("Effect amplifier cannot be negative");
            }
            if (valorianBloodAlloyCost < 1 || valorianBloodAlloyCost % 4 != 0) {
                throw new IllegalArgumentException(
                        "Valorian Blood Alloy cost must be a positive multiple of four"
                );
            }
            if (duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException("Effect duration must be positive");
            }
        }

        public int bloodAlloyCost(@NonNull BloodstoneRank rank) {
            Objects.requireNonNull(rank, "Bloodstone rank cannot be null");
            return switch (rank) {
                case LEGATE -> valorianBloodAlloyCost * 2;
                case CAVALIER -> valorianBloodAlloyCost * 3 / 2;
                case ARCHON -> valorianBloodAlloyCost * 5 / 4;
                case VALORIAN -> valorianBloodAlloyCost;
            };
        }

        @Override
        public @NonNull List<EffectAxeDefinition> effects() {
            return List.of(this);
        }

        public @NonNull PotionEffect createPotionEffect() {
            return new PotionEffect(effectType, Math.toIntExact(duration.toMillis() / 50L), amplifier);
        }
    }
}
