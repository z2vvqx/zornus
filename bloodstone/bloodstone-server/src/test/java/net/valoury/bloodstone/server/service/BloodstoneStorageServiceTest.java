package net.valoury.bloodstone.server.service;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BloodstoneStorageServiceTest {

    @Test
    void failedCloseRetriesAreBoundedAndCanBeStopped() {
        assertTrue(BloodstoneStorageService.shouldRetryStorageClose(true, 1));
        assertTrue(BloodstoneStorageService.shouldRetryStorageClose(true, 2));
        assertFalse(BloodstoneStorageService.shouldRetryStorageClose(true, 3));
        assertFalse(BloodstoneStorageService.shouldRetryStorageClose(false, 1));
    }

    @Test
    void matchesOwnedStorageAcrossInventoryWrappers() {
        InventoryHolder holder = () -> null;
        Inventory firstWrapper = inventoryWithHolder(holder);
        Inventory secondWrapper = inventoryWithHolder(holder);

        assertNotSame(firstWrapper, secondWrapper);
        assertTrue(BloodstoneStorageService.isOwnedInventory(firstWrapper, holder));
        assertTrue(BloodstoneStorageService.isOwnedInventory(secondWrapper, holder));
        assertFalse(BloodstoneStorageService.isOwnedInventory(
                secondWrapper,
                () -> null
        ));
    }

    private static Inventory inventoryWithHolder(InventoryHolder holder) {
        return (Inventory) Proxy.newProxyInstance(
                Inventory.class.getClassLoader(),
                new Class<?>[]{Inventory.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getHolder")) {
                        return holder;
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
