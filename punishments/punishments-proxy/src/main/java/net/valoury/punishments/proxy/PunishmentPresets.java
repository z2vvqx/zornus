package net.valoury.punishments.proxy;

import net.valoury.punishments.proxy.model.PunishmentType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class PunishmentPresets {
    public static final PunishmentPreset SWEARING = new PunishmentPreset(
            "swearing",
            "Swearing",
            List.of(
                    new PunishmentPresetStep(PunishmentType.WARN, "7d"),
                    new PunishmentPresetStep(PunishmentType.MUTE, "12h"),
                    new PunishmentPresetStep(PunishmentType.MUTE, "1d")
            )
    );
    public static final PunishmentPreset CHEATING = new PunishmentPreset(
            "cheating",
            "Cheating",
            List.of(
                    new PunishmentPresetStep(PunishmentType.KICK, null),
                    new PunishmentPresetStep(PunishmentType.BAN, "1h"),
                    new PunishmentPresetStep(PunishmentType.BAN, "24h"),
                    new PunishmentPresetStep(PunishmentType.BAN, "3d")
            )
    );

    private static final List<PunishmentPreset> PRESETS = List.of(SWEARING, CHEATING);
    private static final Map<String, PunishmentPreset> PRESETS_BY_NAME = createPresetIndex();

    private PunishmentPresets() {
    }

    public static @NonNull Optional<PunishmentPreset> find(@NonNull String presetName) {
        return Optional.ofNullable(PRESETS_BY_NAME.get(presetName.toLowerCase(Locale.ROOT)));
    }

    public static @NonNull List<String> names() {
        return PRESETS.stream()
                .map(PunishmentPreset::name)
                .toList();
    }

    private static Map<String, PunishmentPreset> createPresetIndex() {
        Map<String, PunishmentPreset> presetsByName = new LinkedHashMap<>();
        for (PunishmentPreset preset : PRESETS) {
            PunishmentPreset previousPreset = presetsByName.put(preset.name(), preset);
            if (previousPreset != null) {
                throw new IllegalStateException("Duplicate punishment preset name: " + preset.name());
            }
        }
        return Map.copyOf(presetsByName);
    }

    public record PunishmentPreset(
            @NonNull String name,
            @NonNull String reason,
            @NonNull List<PunishmentPresetStep> steps
    ) {
        public PunishmentPreset {
            if (name.isBlank()) {
                throw new IllegalArgumentException("Preset name cannot be blank");
            }
            name = name.toLowerCase(Locale.ROOT);
            if (reason.isBlank()) {
                throw new IllegalArgumentException("Preset reason cannot be blank");
            }
            if (steps.isEmpty()) {
                throw new IllegalArgumentException("Preset must contain at least one step");
            }
            steps = List.copyOf(steps);
        }

        public @NonNull PunishmentPresetStep stepForApplicationNumber(int applicationNumber) {
            if (applicationNumber < 1) {
                throw new IllegalArgumentException("Application number must be positive");
            }
            int stepIndex = Math.min(applicationNumber, steps.size()) - 1;
            return steps.get(stepIndex);
        }
    }

    public record PunishmentPresetStep(
            @NonNull PunishmentType type,
            @Nullable String duration
    ) {
        public PunishmentPresetStep {
            if (type == PunishmentType.KICK && duration != null) {
                throw new IllegalArgumentException("Kick preset steps cannot have a duration");
            }
            if (type != PunishmentType.KICK && (duration == null || duration.isBlank())) {
                throw new IllegalArgumentException("Non-kick preset steps require a duration");
            }
        }
    }
}
