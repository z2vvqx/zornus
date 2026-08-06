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
    public static final PunishmentPreset PROMOTING_EXTERNAL_SERVICES_HEAVY = new PunishmentPreset(
            "promoting-external-services-heavy",
            "Promoting External Services — Heavy",
            List.of(
                    new PunishmentPresetStep(PunishmentType.BAN, "7d"),
                    new PunishmentPresetStep(PunishmentType.BAN, "14d"),
                    new PunishmentPresetStep(PunishmentType.BAN, "30d")
            )
    );
    public static final PunishmentPreset ILLEGITIMATE_ADVANTAGES = new PunishmentPreset(
            "illegitimate-advantages",
            "Illegitimate Advantages",
            List.of(
                    new PunishmentPresetStep(PunishmentType.BAN, "1d"),
                    new PunishmentPresetStep(PunishmentType.BAN, "2d"),
                    new PunishmentPresetStep(PunishmentType.BAN, "3d")
            )
    );
    public static final PunishmentPreset INAPPROPRIATE_APPEARANCE_CAPE_SKIN = new PunishmentPreset(
            "inappropriate-appearance-cape-skin",
            "Inappropriate Appearance — Cape/Skin",
            List.of(
                    new PunishmentPresetStep(PunishmentType.WARN, "7d"),
                    new PunishmentPresetStep(PunishmentType.BAN, "1d"),
                    new PunishmentPresetStep(PunishmentType.BAN, "2d"),
                    new PunishmentPresetStep(PunishmentType.BAN, "3d")
            )
    );
    public static final PunishmentPreset INAPPROPRIATE_APPEARANCE_NAME = new PunishmentPreset(
            "inappropriate-appearance-name",
            "Inappropriate Appearance — Name",
            List.of(
                    new PunishmentPresetStep(PunishmentType.BAN, "1d"),
                    new PunishmentPresetStep(PunishmentType.BAN, "2d"),
                    new PunishmentPresetStep(PunishmentType.BAN, "3d")
            )
    );
    public static final PunishmentPreset SERVER_EXPLOITATION_BUG_EXPLOITATION = new PunishmentPreset(
            "server-exploitation-bug-exploitation",
            "Server Exploitation — Bug Exploitation",
            List.of(
                    new PunishmentPresetStep(PunishmentType.WARN, "7d"),
                    new PunishmentPresetStep(PunishmentType.BAN, "1d"),
                    new PunishmentPresetStep(PunishmentType.BAN, "2d"),
                    new PunishmentPresetStep(PunishmentType.BAN, "3d")
            )
    );
    public static final PunishmentPreset SERVER_EXPLOITATION_FREE_KILL = new PunishmentPreset(
            "server-exploitation-free-kill",
            "Server Exploitation — Free Kill",
            List.of(
                    new PunishmentPresetStep(PunishmentType.BAN, "1d"),
                    new PunishmentPresetStep(PunishmentType.BAN, "2d"),
                    new PunishmentPresetStep(PunishmentType.BAN, "3d")
            )
    );
    public static final PunishmentPreset SERVER_EXPLOITATION_BOOST_FARMING = new PunishmentPreset(
            "server-exploitation-boost-farming",
            "Server Exploitation — Boost Farming",
            List.of(
                    new PunishmentPresetStep(PunishmentType.BAN, "1d"),
                    new PunishmentPresetStep(PunishmentType.BAN, "2d"),
                    new PunishmentPresetStep(PunishmentType.BAN, "3d")
            )
    );
    public static final PunishmentPreset COLLABORATION_WITH_CHEATERS = new PunishmentPreset(
            "collaboration-with-cheaters",
            "Collaboration with Cheaters",
            List.of(
                    new PunishmentPresetStep(PunishmentType.WARN, "7d"),
                    new PunishmentPresetStep(PunishmentType.BAN, "1d"),
                    new PunishmentPresetStep(PunishmentType.BAN, "2d"),
                    new PunishmentPresetStep(PunishmentType.BAN, "3d")
            )
    );
    public static final PunishmentPreset PROMOTING_EXTERNAL_SERVICES_LIGHT = new PunishmentPreset(
            "promoting-external-services-light",
            "Promoting External Services — Light",
            List.of(
                    new PunishmentPresetStep(PunishmentType.WARN, "6h"),
                    new PunishmentPresetStep(PunishmentType.MUTE, "12h"),
                    new PunishmentPresetStep(PunishmentType.MUTE, "1d"),
                    new PunishmentPresetStep(PunishmentType.MUTE, "2d")
            )
    );
    public static final PunishmentPreset PROFANE_LANGUAGE = new PunishmentPreset(
            "profane-language",
            "Profane Language",
            List.of(
                    new PunishmentPresetStep(PunishmentType.MUTE, "1d"),
                    new PunishmentPresetStep(PunishmentType.MUTE, "2d"),
                    new PunishmentPresetStep(PunishmentType.MUTE, "3d")
            )
    );
    public static final PunishmentPreset SENSITIVE_TOPIC_DISCUSSIONS = new PunishmentPreset(
            "sensitive-topic-discussions",
            "Sensitive Topic Discussions",
            List.of(
                    new PunishmentPresetStep(PunishmentType.MUTE, "1d"),
                    new PunishmentPresetStep(PunishmentType.MUTE, "2d"),
                    new PunishmentPresetStep(PunishmentType.MUTE, "3d")
            )
    );
    public static final PunishmentPreset DISRESPECTFUL_CONDUCT = new PunishmentPreset(
            "disrespectful-conduct",
            "Disrespectful Conduct",
            List.of(
                    new PunishmentPresetStep(PunishmentType.WARN, "6h"),
                    new PunishmentPresetStep(PunishmentType.MUTE, "12h"),
                    new PunishmentPresetStep(PunishmentType.MUTE, "1d"),
                    new PunishmentPresetStep(PunishmentType.MUTE, "2d")
            )
    );
    public static final PunishmentPreset EXCESSIVE_MESSAGING = new PunishmentPreset(
            "excessive-messaging",
            "Excessive Messaging",
            List.of(
                    new PunishmentPresetStep(PunishmentType.WARN, "6h"),
                    new PunishmentPresetStep(PunishmentType.MUTE, "6h"),
                    new PunishmentPresetStep(PunishmentType.MUTE, "12h"),
                    new PunishmentPresetStep(PunishmentType.MUTE, "1d")
            )
    );

    private static final List<PunishmentPreset> PRESETS = List.of(
            PROMOTING_EXTERNAL_SERVICES_HEAVY,
            ILLEGITIMATE_ADVANTAGES,
            INAPPROPRIATE_APPEARANCE_CAPE_SKIN,
            INAPPROPRIATE_APPEARANCE_NAME,
            SERVER_EXPLOITATION_BUG_EXPLOITATION,
            SERVER_EXPLOITATION_FREE_KILL,
            SERVER_EXPLOITATION_BOOST_FARMING,
            COLLABORATION_WITH_CHEATERS,
            PROMOTING_EXTERNAL_SERVICES_LIGHT,
            PROFANE_LANGUAGE,
            SENSITIVE_TOPIC_DISCUSSIONS,
            DISRESPECTFUL_CONDUCT,
            EXCESSIVE_MESSAGING
    );
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
