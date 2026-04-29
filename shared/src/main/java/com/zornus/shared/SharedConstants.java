package com.zornus.shared;

public final class SharedConstants {

    private SharedConstants() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }

    public static final String BULLET_POINT = " <dark_gray>•</dark_gray> ";

    public static final int ENTRIES_PER_PAGE = 16;

    public static final String PLAYER_NOT_FOUND = "<red>Unable to recognize this player.</red>";

    public static final String INVALID_PAGE = "<red>Invalid page number. There's only <maximum_pages> page(s).</red>";

    public static final String PLAYERS_ONLY = "<red>This command can only be used by players.</red>";

    public static final String ERROR_UNEXPECTED = "<red>Encountered a fatal internal exception during execution.</red>";

}
