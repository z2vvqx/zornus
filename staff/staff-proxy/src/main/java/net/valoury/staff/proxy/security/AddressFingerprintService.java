package net.valoury.staff.proxy.security;

import net.valoury.staff.proxy.StaffProxyConstants;
import net.valoury.staff.proxy.model.AddressFingerprint;
import org.jspecify.annotations.NonNull;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

public final class AddressFingerprintService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final byte[] DOMAIN_SEPARATOR =
            "valoury:staff-address:v1\0".getBytes(StandardCharsets.UTF_8);
    private static final char[] CROCKFORD_BASE32_ALPHABET =
            "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    private final @NonNull SecretKey hmacKey;

    public AddressFingerprintService(byte @NonNull [] hmacKeyBytes) {
        Objects.requireNonNull(hmacKeyBytes, "Address HMAC key cannot be null");
        if (hmacKeyBytes.length < StaffProxyConstants.MINIMUM_ADDRESS_HMAC_KEY_BYTES) {
            throw new IllegalArgumentException(
                    "Address HMAC key must contain at least "
                            + StaffProxyConstants.MINIMUM_ADDRESS_HMAC_KEY_BYTES
                            + " bytes"
            );
        }
        this.hmacKey = new SecretKeySpec(hmacKeyBytes.clone(), HMAC_ALGORITHM);
    }

    public static @NonNull AddressFingerprintService fromConfiguredKey() {
        try {
            return new AddressFingerprintService(Base64.getDecoder().decode(
                    StaffProxyConstants.ADDRESS_HMAC_KEY
            ));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Configured Staff address HMAC key must decode to at least "
                            + StaffProxyConstants.MINIMUM_ADDRESS_HMAC_KEY_BYTES
                            + " bytes",
                    exception
            );
        }
    }

    public @NonNull AddressFingerprint fingerprint(@NonNull InetAddress address) {
        Objects.requireNonNull(address, "Address cannot be null");
        byte[] canonicalAddress = canonicalAddressBytes(address.getAddress());
        try {
            Mac hmac = Mac.getInstance(HMAC_ALGORITHM);
            hmac.init(hmacKey);
            hmac.update(DOMAIN_SEPARATOR);
            return new AddressFingerprint(encodeCrockfordBase32(hmac.doFinal(canonicalAddress)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to fingerprint connection address", exception);
        }
    }

    private static byte[] canonicalAddressBytes(byte[] addressBytes) {
        if (isIpv4MappedIpv6Address(addressBytes)) {
            return Arrays.copyOfRange(addressBytes, 12, 16);
        }
        return addressBytes.clone();
    }

    private static boolean isIpv4MappedIpv6Address(byte[] addressBytes) {
        if (addressBytes.length != 16) {
            return false;
        }
        for (int index = 0; index < 10; index++) {
            if (addressBytes[index] != 0) {
                return false;
            }
        }
        return addressBytes[10] == (byte) 0xFF && addressBytes[11] == (byte) 0xFF;
    }

    private static String encodeCrockfordBase32(byte[] value) {
        StringBuilder encoded = new StringBuilder((value.length * 8 + 4) / 5);
        int bitBuffer = 0;
        int bufferedBitCount = 0;
        for (byte currentByte : value) {
            bitBuffer = (bitBuffer << 8) | Byte.toUnsignedInt(currentByte);
            bufferedBitCount += 8;
            while (bufferedBitCount >= 5) {
                int alphabetIndex = (bitBuffer >>> (bufferedBitCount - 5)) & 0x1F;
                encoded.append(CROCKFORD_BASE32_ALPHABET[alphabetIndex]);
                bufferedBitCount -= 5;
            }
        }
        if (bufferedBitCount > 0) {
            int alphabetIndex = (bitBuffer << (5 - bufferedBitCount)) & 0x1F;
            encoded.append(CROCKFORD_BASE32_ALPHABET[alphabetIndex]);
        }
        return encoded.toString();
    }
}
