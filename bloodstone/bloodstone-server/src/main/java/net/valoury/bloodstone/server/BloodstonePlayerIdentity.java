package net.valoury.bloodstone.server;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.regex.Pattern;

public final class BloodstonePlayerIdentity {

    public static final String VALID_USERNAME_REGEX =
            "^[A-Za-z0-9_]{1,16}$";

    private static final Pattern VALID_USERNAME_PATTERN =
            Pattern.compile(VALID_USERNAME_REGEX);

    private BloodstonePlayerIdentity() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static boolean isValidUsername(@Nullable String username) {
        return username != null
                && VALID_USERNAME_PATTERN.matcher(username).matches();
    }

    public static String requireValidUsername(String username) {
        Objects.requireNonNull(username, "Username cannot be null");
        if (!isValidUsername(username)) {
            throw new IllegalArgumentException(
                    "Username must match " + VALID_USERNAME_REGEX
            );
        }
        return username;
    }
}
