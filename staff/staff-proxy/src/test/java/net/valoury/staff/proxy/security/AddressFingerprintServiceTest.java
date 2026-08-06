package net.valoury.staff.proxy.security;

import net.valoury.staff.proxy.model.AddressFingerprint;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AddressFingerprintServiceTest {
    private static final byte[] TEST_KEY = new byte[32];

    static {
        Arrays.fill(TEST_KEY, (byte) 0x5A);
    }

    @Test
    void sameAddressProducesSameOpaqueIdentifier() throws Exception {
        AddressFingerprintService service = new AddressFingerprintService(TEST_KEY);
        InetAddress address = InetAddress.getByAddress(new byte[]{10, 20, 30, 40});

        AddressFingerprint first = service.fingerprint(address);
        AddressFingerprint second = service.fingerprint(address);

        assertEquals(first, second);
        assertEquals(52, first.encodedValue().length());
        assertEquals("IP-", first.displayIdentifier().substring(0, 3));
        assertEquals(17, first.displayIdentifier().length());
    }

    @Test
    void differentAddressesProduceDifferentIdentifiers() throws Exception {
        AddressFingerprintService service = new AddressFingerprintService(TEST_KEY);

        AddressFingerprint first = service.fingerprint(
                InetAddress.getByAddress(new byte[]{10, 20, 30, 40})
        );
        AddressFingerprint second = service.fingerprint(
                InetAddress.getByAddress(new byte[]{10, 20, 30, 41})
        );

        assertNotEquals(first, second);
        assertNotEquals(first.displayIdentifier(), second.displayIdentifier());
    }

    @Test
    void ipv4MappedIpv6AddressMatchesCanonicalIpv4Address() throws Exception {
        AddressFingerprintService service = new AddressFingerprintService(TEST_KEY);
        byte[] mappedAddress = new byte[16];
        mappedAddress[10] = (byte) 0xFF;
        mappedAddress[11] = (byte) 0xFF;
        mappedAddress[12] = 10;
        mappedAddress[13] = 20;
        mappedAddress[14] = 30;
        mappedAddress[15] = 40;

        AddressFingerprint ipv4 = service.fingerprint(
                InetAddress.getByAddress(new byte[]{10, 20, 30, 40})
        );
        AddressFingerprint mappedIpv6 = service.fingerprint(
                InetAddress.getByAddress(mappedAddress)
        );

        assertEquals(ipv4, mappedIpv6);
    }

    @Test
    void rejectsShortHmacKeys() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AddressFingerprintService(new byte[31])
        );
    }

    @Test
    void configuredKeyCreatesValidFingerprints() throws Exception {
        AddressFingerprint fingerprint = AddressFingerprintService
                .fromConfiguredKey()
                .fingerprint(InetAddress.getByAddress(new byte[]{10, 20, 30, 40}));

        assertEquals(52, fingerprint.encodedValue().length());
    }
}
