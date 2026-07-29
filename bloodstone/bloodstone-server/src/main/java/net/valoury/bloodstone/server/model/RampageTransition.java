package net.valoury.bloodstone.server.model;

public record RampageTransition(int current, int best, boolean newBest) {

    public static RampageTransition afterKill(int current, int best) {
        if (current < 0 || best < current) {
            throw new IllegalArgumentException("Rampage values are inconsistent");
        }
        int updatedCurrent = Math.addExact(current, 1);
        return new RampageTransition(
                updatedCurrent,
                Math.max(best, updatedCurrent),
                updatedCurrent > best
        );
    }

    public static RampageTransition afterDeathThenKill(int best) {
        if (best < 0) {
            throw new IllegalArgumentException("Best rampage cannot be negative");
        }
        return new RampageTransition(1, Math.max(best, 1), best < 1);
    }
}
