package net.valoury.staff.proxy.model;

import net.valoury.staff.proxy.StaffProxyConstants;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.regex.Pattern;

public record AddressFingerprint(@NonNull String encodedValue) {
    private static final Pattern ENCODED_VALUE_PATTERN =
            Pattern.compile("[0-9A-HJKMNP-TV-Z]{52}");

    public AddressFingerprint {
        Objects.requireNonNull(encodedValue, "Encoded address fingerprint cannot be null");
        if (!ENCODED_VALUE_PATTERN.matcher(encodedValue).matches()) {
            throw new IllegalArgumentException("Encoded address fingerprint is invalid");
        }
    }

    public @NonNull String displayIdentifier() {
        String visibleValue = encodedValue.substring(
                0,
                StaffProxyConstants.DISPLAY_FINGERPRINT_CHARACTERS
        );
        return "IP-" + visibleValue.substring(0, 4)
                + "-" + visibleValue.substring(4, 8)
                + "-" + visibleValue.substring(8, 12);
    }
}
