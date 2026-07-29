package net.valoury.bloodstone.server.service;

import net.valoury.bloodstone.server.BloodstoneMenuItem;
import net.valoury.bloodstone.server.BloodstoneServerConstants;
import net.valoury.bloodstone.server.BloodstoneText;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

enum EnchantmentToolAction {
    ENCHANT(
            BloodstoneServerConstants.ENCHANTER_MENU_TITLE,
            BloodstoneServerConstants.ENCHANTER_ACCESS_REQUIRED,
            BloodstoneServerConstants.ENCHANTER_ITEM_REJECTED,
            BloodstoneServerConstants.ENCHANTER_HELD_ITEM_CHANGED,
            BloodstoneServerConstants.ENCHANTER_HELD_ITEM_RECOVERY,
            BloodstoneServerConstants.ENCHANTER_COOLDOWN_ERROR_KEY,
            "",
            BloodstoneServerConstants.ENCHANTER_OPTION_ITEM
    ),
    DISENCHANT(
            BloodstoneServerConstants.DISENCHANTER_MENU_TITLE,
            BloodstoneServerConstants.DISENCHANTER_ACCESS_REQUIRED,
            BloodstoneServerConstants.DISENCHANTER_ITEM_REJECTED,
            BloodstoneServerConstants.DISENCHANTER_HELD_ITEM_CHANGED,
            BloodstoneServerConstants.DISENCHANTER_HELD_ITEM_RECOVERY,
            BloodstoneServerConstants.DISENCHANTER_COOLDOWN_ERROR_KEY,
            "disenchant::",
            BloodstoneServerConstants.DISENCHANTER_OPTION_ITEM
    );

    private final String legacyMenuTitle;
    private final String accessRequiredMessage;
    private final String itemRejectedMessage;
    private final String heldItemChangedMessage;
    private final String heldItemRecoveryMessage;
    private final String cooldownErrorKey;
    private final String offerKeyPrefix;
    private final BloodstoneMenuItem optionItem;

    EnchantmentToolAction(
            String menuTitleTemplate,
            String accessRequiredMessage,
            String itemRejectedMessage,
            String heldItemChangedMessage,
            String heldItemRecoveryMessage,
            String cooldownErrorKey,
            String offerKeyPrefix,
            BloodstoneMenuItem optionItem
    ) {
        this.legacyMenuTitle = BloodstoneText.legacy(menuTitleTemplate);
        this.accessRequiredMessage = accessRequiredMessage;
        this.itemRejectedMessage = itemRejectedMessage;
        this.heldItemChangedMessage = heldItemChangedMessage;
        this.heldItemRecoveryMessage = heldItemRecoveryMessage;
        this.cooldownErrorKey = cooldownErrorKey;
        this.offerKeyPrefix = offerKeyPrefix;
        this.optionItem = optionItem;
    }

    String legacyMenuTitle() {
        return legacyMenuTitle;
    }

    static boolean isMenuTitle(String title) {
        for (EnchantmentToolAction action : values()) {
            if (action.legacyMenuTitle.equals(title)) {
                return true;
            }
        }
        return false;
    }

    String accessRequiredMessage() {
        return accessRequiredMessage;
    }

    String itemRejectedMessage() {
        return itemRejectedMessage;
    }

    String heldItemChangedMessage() {
        return heldItemChangedMessage;
    }

    String heldItemRecoveryMessage() {
        return heldItemRecoveryMessage;
    }

    String cooldownErrorKey() {
        return cooldownErrorKey;
    }

    String offerKey(String baseOfferKey) {
        return offerKeyPrefix + baseOfferKey;
    }

    BloodstoneMenuItem optionItem() {
        return optionItem;
    }

    ItemStack transform(
            ItemStack original,
            Enchantment enchantment,
            int level
    ) {
        ItemStack result = original.clone();
        switch (this) {
            case ENCHANT -> result.addUnsafeEnchantment(enchantment, level);
            case DISENCHANT -> result.removeEnchantment(enchantment);
        }
        return result;
    }
}
