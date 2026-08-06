package net.valoury.bloodstone.server.model;

import java.util.Locale;

public enum StorageType {
    DEFAULT,
    LEGATE,
    CAVALIER,
    ARCHON,
    VALORIAN,
    EXTRA;

    public String displayName() {
        String lowerCaseName = name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lowerCaseName.charAt(0))
                + lowerCaseName.substring(1);
    }

    public int inventorySize() {
        return switch (this) {
            case DEFAULT, LEGATE -> 27;
            case CAVALIER -> 36;
            case ARCHON -> 45;
            case VALORIAN, EXTRA -> 54;
        };
    }

}
