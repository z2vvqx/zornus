package net.valoury.bloodstone.server.service;

import net.kyori.adventure.text.Component;
import net.valoury.bloodstone.server.model.BloodstoneItemClassification;
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
                        BloodstoneItemClassification.INCLUSIVE.lore()
                ),
                BloodstoneItemClassification.EXCLUSIVE
        );

        assertEquals(
                List.of(
                        existingLore,
                        BloodstoneItemClassification.EXCLUSIVE.lore()
                ),
                updatedLore
        );
    }

    @Test
    void replacingClassificationDoesNotDuplicateExclusiveLore() {
        assertEquals(
                List.of(BloodstoneItemClassification.EXCLUSIVE.lore()),
                BloodstoneItemService.replaceClassificationLore(
                        List.of(
                                BloodstoneItemClassification.INCLUSIVE.lore(),
                                BloodstoneItemClassification.EXCLUSIVE.lore()
                        ),
                        BloodstoneItemClassification.EXCLUSIVE
                )
        );
    }

    @Test
    void onlyInclusiveIsRemovedByNormalEnchanting() {
        assertTrue(BloodstoneItemClassification.INCLUSIVE
                .isRemovedByNormalEnchanting());
        assertFalse(BloodstoneItemClassification.EXCLUSIVE
                .isRemovedByNormalEnchanting());
        assertFalse(BloodstoneItemClassification.SOULBOUND
                .isRemovedByNormalEnchanting());
    }

    @Test
    void onlySoulboundItemsAreRestrictedFromModification() {
        assertFalse(BloodstoneItemClassification.INCLUSIVE
                .isRestrictedFromModification());
        assertFalse(BloodstoneItemClassification.EXCLUSIVE
                .isRestrictedFromModification());
        assertTrue(BloodstoneItemClassification.SOULBOUND
                .isRestrictedFromModification());
    }
}
