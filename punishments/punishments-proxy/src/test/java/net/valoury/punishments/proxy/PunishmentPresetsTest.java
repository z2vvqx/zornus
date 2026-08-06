package net.valoury.punishments.proxy;

import net.valoury.punishments.proxy.PunishmentPresets.PunishmentPreset;
import net.valoury.punishments.proxy.PunishmentPresets.PunishmentPresetStep;
import net.valoury.punishments.proxy.model.PunishmentType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PunishmentPresetsTest {
    @Test
    void containsTheConfiguredPunishmentLadders() {
        Map<String, PunishmentPreset> expectedPresets = new LinkedHashMap<>();
        expectedPresets.put("promoting-external-services-heavy", preset(
                "promoting-external-services-heavy",
                "Promoting External Services — Heavy",
                step(PunishmentType.BAN, "7d"),
                step(PunishmentType.BAN, "14d"),
                step(PunishmentType.BAN, "30d")
        ));
        expectedPresets.put("illegitimate-advantages", preset(
                "illegitimate-advantages",
                "Illegitimate Advantages",
                step(PunishmentType.BAN, "1d"),
                step(PunishmentType.BAN, "2d"),
                step(PunishmentType.BAN, "3d")
        ));
        expectedPresets.put("inappropriate-appearance-cape-skin", preset(
                "inappropriate-appearance-cape-skin",
                "Inappropriate Appearance — Cape/Skin",
                step(PunishmentType.WARN, "7d"),
                step(PunishmentType.BAN, "1d"),
                step(PunishmentType.BAN, "2d"),
                step(PunishmentType.BAN, "3d")
        ));
        expectedPresets.put("inappropriate-appearance-name", preset(
                "inappropriate-appearance-name",
                "Inappropriate Appearance — Name",
                step(PunishmentType.BAN, "1d"),
                step(PunishmentType.BAN, "2d"),
                step(PunishmentType.BAN, "3d")
        ));
        expectedPresets.put("server-exploitation-bug-exploitation", preset(
                "server-exploitation-bug-exploitation",
                "Server Exploitation — Bug Exploitation",
                step(PunishmentType.WARN, "7d"),
                step(PunishmentType.BAN, "1d"),
                step(PunishmentType.BAN, "2d"),
                step(PunishmentType.BAN, "3d")
        ));
        expectedPresets.put("server-exploitation-free-kill", preset(
                "server-exploitation-free-kill",
                "Server Exploitation — Free Kill",
                step(PunishmentType.BAN, "1d"),
                step(PunishmentType.BAN, "2d"),
                step(PunishmentType.BAN, "3d")
        ));
        expectedPresets.put("server-exploitation-boost-farming", preset(
                "server-exploitation-boost-farming",
                "Server Exploitation — Boost Farming",
                step(PunishmentType.BAN, "1d"),
                step(PunishmentType.BAN, "2d"),
                step(PunishmentType.BAN, "3d")
        ));
        expectedPresets.put("collaboration-with-cheaters", preset(
                "collaboration-with-cheaters",
                "Collaboration with Cheaters",
                step(PunishmentType.WARN, "7d"),
                step(PunishmentType.BAN, "1d"),
                step(PunishmentType.BAN, "2d"),
                step(PunishmentType.BAN, "3d")
        ));
        expectedPresets.put("promoting-external-services-light", preset(
                "promoting-external-services-light",
                "Promoting External Services — Light",
                step(PunishmentType.WARN, "6h"),
                step(PunishmentType.MUTE, "12h"),
                step(PunishmentType.MUTE, "1d"),
                step(PunishmentType.MUTE, "2d")
        ));
        expectedPresets.put("profane-language", preset(
                "profane-language",
                "Profane Language",
                step(PunishmentType.MUTE, "1d"),
                step(PunishmentType.MUTE, "2d"),
                step(PunishmentType.MUTE, "3d")
        ));
        expectedPresets.put("sensitive-topic-discussions", preset(
                "sensitive-topic-discussions",
                "Sensitive Topic Discussions",
                step(PunishmentType.MUTE, "1d"),
                step(PunishmentType.MUTE, "2d"),
                step(PunishmentType.MUTE, "3d")
        ));
        expectedPresets.put("disrespectful-conduct", preset(
                "disrespectful-conduct",
                "Disrespectful Conduct",
                step(PunishmentType.WARN, "6h"),
                step(PunishmentType.MUTE, "12h"),
                step(PunishmentType.MUTE, "1d"),
                step(PunishmentType.MUTE, "2d")
        ));
        expectedPresets.put("excessive-messaging", preset(
                "excessive-messaging",
                "Excessive Messaging",
                step(PunishmentType.WARN, "6h"),
                step(PunishmentType.MUTE, "6h"),
                step(PunishmentType.MUTE, "12h"),
                step(PunishmentType.MUTE, "1d")
        ));

        assertEquals(List.copyOf(expectedPresets.keySet()), PunishmentPresets.names());
        expectedPresets.forEach((name, expectedPreset) ->
                assertEquals(expectedPreset, PunishmentPresets.find(name).orElseThrow()));
    }

    @Test
    void lookupsAreCaseInsensitiveAndUnknownNamesAreRejected() {
        assertEquals(
                PunishmentPresets.ILLEGITIMATE_ADVANTAGES,
                PunishmentPresets.find("ILLEGITIMATE-ADVANTAGES").orElseThrow()
        );
        assertTrue(PunishmentPresets.find("unknown").isEmpty());
    }

    @Test
    void maximumStepsRepeatForLaterApplications() {
        PunishmentPreset preset = PunishmentPresets.EXCESSIVE_MESSAGING;

        assertEquals(step(PunishmentType.MUTE, "1d"), preset.stepForApplicationNumber(4));
        assertEquals(step(PunishmentType.MUTE, "1d"), preset.stepForApplicationNumber(5));
        assertEquals(step(PunishmentType.MUTE, "1d"), preset.stepForApplicationNumber(100));
        assertThrows(IllegalArgumentException.class, () -> preset.stepForApplicationNumber(0));
    }

    private static PunishmentPreset preset(
            String name,
            String reason,
            PunishmentPresetStep... steps
    ) {
        return new PunishmentPreset(name, reason, List.of(steps));
    }

    private static PunishmentPresetStep step(PunishmentType type, String duration) {
        return new PunishmentPresetStep(type, duration);
    }
}
