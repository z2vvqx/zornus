package net.valoury.discord.api;

import java.time.Duration;
import java.util.regex.Pattern;

public final class ApiConstants {
    public static final Duration ACCOUNT_LINK_CODE_LIFETIME = Duration.ofMinutes(5);
    public static final Duration ACCOUNT_LINK_CODE_ISSUANCE_COOLDOWN = Duration.ofSeconds(30);
    public static final Duration ACCOUNT_LINK_ATTEMPT_WINDOW = Duration.ofMinutes(1);
    public static final int MAXIMUM_ACCOUNT_LINK_ATTEMPTS = 5;
    public static final String ACCOUNT_LINK_CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    public static final int ACCOUNT_LINK_CODE_CHARACTER_COUNT = 12;
    public static final int ACCOUNT_LINK_CODE_GROUP_SIZE = 4;
    public static final int MAXIMUM_ACCOUNT_LINK_CODE_GENERATION_ATTEMPTS = 3;
    public static final Pattern MINECRAFT_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private ApiConstants() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }
}
