package net.valoury.bloodstone.server.command;

import net.valoury.bloodstone.server.BloodstoneText;
import net.valoury.bloodstone.server.model.PlayerProfile;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BloodstoneStatisticsCommandTest {

    @Test
    void displaysLogicallyOrderedStatisticsBetweenBlankLines() {
        PlayerProfile profile = new PlayerProfile(
                UUID.randomUUID(),
                "TestPlayer",
                12,
                5,
                7,
                8,
                3,
                2,
                4,
                10,
                false,
                0
        );

        String display = BloodstoneText.legacy(
                BloodstoneStatisticsCommand.createStatisticsDisplay(profile)
        ).replaceAll("§[0-9A-FK-ORa-fk-or]", "");

        assertEquals("\n" + String.join("\n",
                " ► Kills: 12",
                " ► Deaths: 5",
                " ► Ratio: 2.40",
                " ► Assists: 7",
                " ► Carries: 8",
                " ► Rampage: 4/10",
                " ► Dominations: 3",
                " ► Revenges: 2",
                ""
        ), display);
    }
}
