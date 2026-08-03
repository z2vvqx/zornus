package net.valoury.bloodstone.server.model;

public enum ReservedItemDeliveryOutcome {
    DELIVERED,
    DROPPED,
    ALREADY_PRESENT,
    INVENTORY_FULL,
    PLAYER_OFFLINE;

    public boolean wasDelivered() {
        return this == DELIVERED || this == DROPPED;
    }
}
