package net.valoury.bloodstone.server.model;

import java.util.Locale;

public enum StorageType {
    DEFAULT("DEFAULT"),
    LEGATE("IRON"),
    JUSTICAR("GOLD"),
    REGENT("DIAMOND"),
    ARCHON("EMERALD"),
    EXTRA("EXTRA");

    private final String persistenceKey;

    StorageType(String persistenceKey) {
        this.persistenceKey = persistenceKey;
    }

    public String displayName() {
        String lowerCaseName = name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lowerCaseName.charAt(0))
                + lowerCaseName.substring(1);
    }

    public int inventorySize() {
        return switch (this) {
            case DEFAULT, LEGATE -> 27;
            case JUSTICAR -> 36;
            case REGENT -> 45;
            case ARCHON, EXTRA -> 54;
        };
    }

    public String persistenceKey() {
        return persistenceKey;
    }
}
