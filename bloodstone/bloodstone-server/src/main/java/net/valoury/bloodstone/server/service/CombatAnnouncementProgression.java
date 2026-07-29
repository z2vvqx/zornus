package net.valoury.bloodstone.server.service;

import java.util.List;

final class CombatAnnouncementProgression {

    private static final List<ProgressionMilestone> PROGRESSION_MILESTONES = List.of(
            new ProgressionMilestone(5, 3),
            new ProgressionMilestone(10, 6),
            new ProgressionMilestone(15, 9),
            new ProgressionMilestone(20, 12),
            new ProgressionMilestone(25, 15),
            new ProgressionMilestone(50, 30),
            new ProgressionMilestone(75, 45),
            new ProgressionMilestone(100, 60)
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
