package net.valoury.bloodstone.server;

import net.valoury.bloodstone.server.CombinedEffectAxeDefinitions.CombinedEffectAxeDefinition;
import net.valoury.bloodstone.server.EffectAxeDefinitions.EffectAxeDefinition;
import org.jspecify.annotations.NonNull;

import java.util.List;

public sealed interface EffectAxeItemDefinition permits
        EffectAxeDefinition,
        CombinedEffectAxeDefinition {

    @NonNull String id();

    @NonNull String displayNameTemplate();

    @NonNull List<EffectAxeDefinition> effects();
}
