package net.valoury.bloodstone.server.service;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BloodstoneItemClassificationTest {

    @Test
    void replacingInclusiveClassificationSetsExclusiveLore() {
        Component existingLore = Component.text("Existing detail");

        List<Component> updatedLore = BloodstoneItemService.replaceClassificationLore(
                List.of(
                        existingLore,
                        BloodstoneItemService.Classification.INCLUSIVE.lore()
                ),
                BloodstoneItemService.Classification.EXCLUSIVE
        );

        assertEquals(
                List.of(
                        existingLore,
                        BloodstoneItemService.Classification.EXCLUSIVE.lore()
                ),
                updatedLore
        );
    }

    @Test
    void replacingClassificationDoesNotDuplicateExclusiveLore() {
        assertEquals(
                List.of(BloodstoneItemService.Classification.EXCLUSIVE.lore()),
                BloodstoneItemService.replaceClassificationLore(
                        List.of(
                                BloodstoneItemService.Classification.INCLUSIVE.lore(),
                                BloodstoneItemService.Classification.EXCLUSIVE.lore()
                        ),
                        BloodstoneItemService.Classification.EXCLUSIVE
                )
        );
    }

    @Test
    void onlyInclusiveIsRemovedByNormalEnchanting() {
        assertTrue(BloodstoneItemService.Classification.INCLUSIVE
                .isRemovedByNormalEnchanting());
        assertFalse(BloodstoneItemService.Classification.EXCLUSIVE
                .isRemovedByNormalEnchanting());
        assertFalse(BloodstoneItemService.Classification.SOULBOUND
                .isRemovedByNormalEnchanting());
    }

    @Test
    void onlySoulboundItemsAreRestrictedFromModification() {
        assertFalse(BloodstoneItemService.Classification.INCLUSIVE
                .isRestrictedFromModification());
        assertFalse(BloodstoneItemService.Classification.EXCLUSIVE
                .isRestrictedFromModification());
        assertTrue(BloodstoneItemService.Classification.SOULBOUND
                .isRestrictedFromModification());
    }
}
