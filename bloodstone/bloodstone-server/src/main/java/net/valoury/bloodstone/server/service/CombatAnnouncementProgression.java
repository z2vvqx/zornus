package net.valoury.bloodstone.server.service;

import java.util.List;

final class CombatAnnouncementProgression {

    private static final List<ProgressionMilestone> PROGRESSION_MILESTONES = List.of(
            new ProgressionMilestone(5, 4),
            new ProgressionMilestone(10, 8),
            new ProgressionMilestone(15, 12),
            new ProgressionMilestone(25, 20),
            new ProgressionMilestone(50, 40),
            new ProgressionMilestone(75, 60),
            new ProgressionMilestone(100, 80)
    );

    private CombatAnnouncementProgression() {
    }

    static boolean isRampageMilestone(int killCount) {
        for (ProgressionMilestone milestone : PROGRESSION_MILESTONES) {
            if (milestone.rampageKillCount() == killCount) {
                return true;
            }
        }
        return false;
    }

    static boolean isDominationMilestone(int killCount) {
        for (ProgressionMilestone milestone : PROGRESSION_MILESTONES) {
            if (milestone.dominationKillCount() == killCount) {
                return true;
            }
        }
        return false;
    }

    private record ProgressionMilestone(
            int rampageKillCount,
            int dominationKillCount
    ) {
    }
}
