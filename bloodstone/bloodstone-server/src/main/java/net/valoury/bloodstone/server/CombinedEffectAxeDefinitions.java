package net.valoury.bloodstone.server;

import net.valoury.bloodstone.server.EffectAxeDefinitions.EffectAxeDefinition;
import org.jspecify.annotations.NonNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class CombinedEffectAxeDefinitions {

    public static final CombinedEffectAxeDefinition BERSERKER =
            definition("speed_strength", "Berserker Axe",
                    EffectAxeDefinitions.SPEED, EffectAxeDefinitions.STRENGTH);
    public static final CombinedEffectAxeDefinition REAPER =
            definition("speed_wither", "Reaper Axe",
                    EffectAxeDefinitions.SPEED, EffectAxeDefinitions.WITHER);
    public static final CombinedEffectAxeDefinition PHANTOM =
            definition("speed_blindness", "Phantom Axe",
                    EffectAxeDefinitions.SPEED, EffectAxeDefinitions.BLINDNESS);
    public static final CombinedEffectAxeDefinition PREDATOR =
            definition("speed_weakness", "Predator Axe",
                    EffectAxeDefinitions.SPEED, EffectAxeDefinitions.WEAKNESS);
    public static final CombinedEffectAxeDefinition VIPER =
            definition("speed_poison", "Viper Axe",
                    EffectAxeDefinitions.SPEED, EffectAxeDefinitions.POISON);
    public static final CombinedEffectAxeDefinition RUIN =
            definition("strength_wither", "Ruin Axe",
                    EffectAxeDefinitions.STRENGTH, EffectAxeDefinitions.WITHER);
    public static final CombinedEffectAxeDefinition DREAD =
            definition("strength_blindness", "Dread Axe",
                    EffectAxeDefinitions.STRENGTH, EffectAxeDefinitions.BLINDNESS);
    public static final CombinedEffectAxeDefinition TYRANT =
            definition("strength_weakness", "Tyrant Axe",
                    EffectAxeDefinitions.STRENGTH, EffectAxeDefinitions.WEAKNESS);
    public static final CombinedEffectAxeDefinition VENOMFANG =
            definition("strength_poison", "Venomfang Axe",
                    EffectAxeDefinitions.STRENGTH, EffectAxeDefinitions.POISON);
    public static final CombinedEffectAxeDefinition VOID =
            definition("wither_blindness", "Void Axe",
                    EffectAxeDefinitions.WITHER, EffectAxeDefinitions.BLINDNESS);
    public static final CombinedEffectAxeDefinition DECAY =
            definition("wither_weakness", "Decay Axe",
                    EffectAxeDefinitions.WITHER, EffectAxeDefinitions.WEAKNESS);
    public static final CombinedEffectAxeDefinition PLAGUE =
            definition("wither_poison", "Plague Axe",
                    EffectAxeDefinitions.WITHER, EffectAxeDefinitions.POISON);
    public static final CombinedEffectAxeDefinition OPPRESSION =
            definition("blindness_weakness", "Oppression Axe",
                    EffectAxeDefinitions.BLINDNESS, EffectAxeDefinitions.WEAKNESS);
    public static final CombinedEffectAxeDefinition NIGHTSHADE =
            definition("blindness_poison", "Nightshade Axe",
                    EffectAxeDefinitions.BLINDNESS, EffectAxeDefinitions.POISON);
    public static final CombinedEffectAxeDefinition AFFLICTION =
            definition("weakness_poison", "Affliction Axe",
                    EffectAxeDefinitions.WEAKNESS, EffectAxeDefinitions.POISON);

    private static final List<CombinedEffectAxeDefinition> DEFINITIONS = List.of(
            BERSERKER,
            REAPER,
            PHANTOM,
            PREDATOR,
            VIPER,
            RUIN,
            DREAD,
            TYRANT,
            VENOMFANG,
            VOID,
            DECAY,
            PLAGUE,
            OPPRESSION,
            NIGHTSHADE,
            AFFLICTION
    );
    private static final Map<String, CombinedEffectAxeDefinition> DEFINITIONS_BY_ID =
            createDefinitionIndex();
    private static final Map<EffectPair, CombinedEffectAxeDefinition> DEFINITIONS_BY_PAIR =
            createPairIndex();

    private CombinedEffectAxeDefinitions() {
    }

    public static @NonNull List<CombinedEffectAxeDefinition> values() {
        return DEFINITIONS;
    }

    public static @NonNull Optional<CombinedEffectAxeDefinition> find(@NonNull String id) {
        return Optional.ofNullable(DEFINITIONS_BY_ID.get(id.toLowerCase(Locale.ROOT)));
    }

    public static @NonNull Optional<CombinedEffectAxeDefinition> find(
            @NonNull EffectAxeDefinition firstEffect,
            @NonNull EffectAxeDefinition secondEffect
    ) {
        return Optional.ofNullable(DEFINITIONS_BY_PAIR.get(
                EffectPair.of(firstEffect, secondEffect)
        ));
    }

    private static CombinedEffectAxeDefinition definition(
            String id,
            String name,
            EffectAxeDefinition firstEffect,
            EffectAxeDefinition secondEffect
    ) {
        return new CombinedEffectAxeDefinition(
                id,
                "<dark_aqua>" + name + "</dark_aqua>",
                firstEffect,
                secondEffect
        );
    }

    private static Map<String, CombinedEffectAxeDefinition> createDefinitionIndex() {
        Map<String, CombinedEffectAxeDefinition> definitionsById = new LinkedHashMap<>();
        for (CombinedEffectAxeDefinition definition : DEFINITIONS) {
            CombinedEffectAxeDefinition previous =
                    definitionsById.put(definition.id(), definition);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate combined Effect Axe id: " + definition.id()
                );
            }
        }
        return Map.copyOf(definitionsById);
    }

    private static Map<EffectPair, CombinedEffectAxeDefinition> createPairIndex() {
        Map<EffectPair, CombinedEffectAxeDefinition> definitionsByPair = new LinkedHashMap<>();
        for (CombinedEffectAxeDefinition definition : DEFINITIONS) {
            EffectPair pair = EffectPair.of(
                    definition.firstEffect(),
                    definition.secondEffect()
            );
            CombinedEffectAxeDefinition previous =
                    definitionsByPair.put(pair, definition);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate combined Effect Axe pair: " + pair
                );
            }
        }
        return Map.copyOf(definitionsByPair);
    }

    public record CombinedEffectAxeDefinition(
            @NonNull String id,
            @NonNull String displayNameTemplate,
            @NonNull EffectAxeDefinition firstEffect,
            @NonNull EffectAxeDefinition secondEffect
    ) implements EffectAxeItemDefinition {

        public CombinedEffectAxeDefinition {
            if (id.isBlank()) {
                throw new IllegalArgumentException(
                        "Combined Effect Axe id cannot be blank"
                );
            }
            id = id.toLowerCase(Locale.ROOT);
            Objects.requireNonNull(
                    displayNameTemplate,
                    "Combined Effect Axe display name cannot be null"
            );
            Objects.requireNonNull(firstEffect, "First effect cannot be null");
            Objects.requireNonNull(secondEffect, "Second effect cannot be null");
            if (firstEffect.equals(secondEffect)) {
                throw new IllegalArgumentException(
                        "A combined Effect Axe requires two different effects"
                );
            }
        }

        @Override
        public @NonNull List<EffectAxeDefinition> effects() {
            return List.of(firstEffect, secondEffect);
        }
    }

    private record EffectPair(String firstId, String secondId) {

        private static EffectPair of(
                EffectAxeDefinition firstEffect,
                EffectAxeDefinition secondEffect
        ) {
            if (firstEffect.id().compareTo(secondEffect.id()) <= 0) {
                return new EffectPair(firstEffect.id(), secondEffect.id());
            }
            return new EffectPair(secondEffect.id(), firstEffect.id());
        }
    }
}
