package net.valoury.bloodstone.server.service;

import net.kyori.adventure.text.Component;
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

    private final Component menuTitle;
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
        this.menuTitle = BloodstoneText.deserialize(menuTitleTemplate);
        this.accessRequiredMessage = accessRequiredMessage;
        this.itemRejectedMessage = itemRejectedMessage;
        this.heldItemChangedMessage = heldItemChangedMessage;
        this.heldItemRecoveryMessage = heldItemRecoveryMessage;
        this.cooldownErrorKey = cooldownErrorKey;
        this.offerKeyPrefix = offerKeyPrefix;
        this.optionItem = optionItem;
    }

    Component menuTitle() {
        return menuTitle;
    }

    static boolean isMenuTitle(Component title) {
        for (EnchantmentToolAction action : values()) {
            if (action.menuTitle.equals(title)) {
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

    boolean isSelectionAvailable(int currentLevel, int offeredLevel) {
        return switch (this) {
            case ENCHANT -> currentLevel != offeredLevel;
            case DISENCHANT -> currentLevel > 0;
        };
    }

    String unavailableSelectionMessage() {
        return switch (this) {
            case ENCHANT -> BloodstoneServerConstants.ENCHANTER_ALREADY_PRESENT;
            case DISENCHANT ->
                    BloodstoneServerConstants.DISENCHANTER_ENCHANTMENT_MISSING;
        };
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
