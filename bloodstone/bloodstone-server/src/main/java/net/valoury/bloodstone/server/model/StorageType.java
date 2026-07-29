package net.valoury.bloodstone.server.model;

import java.util.Locale;

public enum StorageType {
    DEFAULT,
    IRON,
    GOLD,
    DIAMOND,
    EMERALD,
    EXTRA;

    public String displayName() {
        String lowerCaseName = name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lowerCaseName.charAt(0))
                + lowerCaseName.substring(1);
    }

    public int inventorySize() {
        return switch (this) {
            case DEFAULT, IRON -> 27;
            case GOLD -> 36;
            case DIAMOND -> 45;
            case EMERALD, EXTRA -> 54;
        };
    }
}
