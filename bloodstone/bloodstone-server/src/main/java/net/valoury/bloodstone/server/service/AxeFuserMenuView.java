package net.valoury.bloodstone.server.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.valoury.bloodstone.server.BloodstoneServerConstants;
import net.valoury.bloodstone.server.BloodstoneText;
import net.valoury.bloodstone.server.EffectAxeDefinitions.EffectAxeDefinition;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class AxeFuserMenuView {

    static final int FUSE_BUTTON_SLOT = 31;

    private static final int MENU_SIZE = 45;
    private static final int RESULT_PREVIEW_SLOT = 13;
    private static final int[] EFFECT_AXE_SLOTS = {10, 11, 12, 14, 15, 16};

    private final BloodstoneItemService itemService;
    private final Component title = BloodstoneText.deserialize(
            BloodstoneServerConstants.AXE_FUSER_MENU_TITLE
    );

    AxeFuserMenuView(BloodstoneItemService itemService) {
        this.itemService = itemService;
    }

    Inventory createInventory() {
        return Bukkit.createInventory(null, MENU_SIZE, title);
    }

    void render(
            Inventory inventory,
            List<EffectAxeDefinition> orderedEffects,
            List<EffectAxeDefinition> selectedEffects,
            @Nullable ItemStack resultPreview,
            boolean missingSelectedAxes
    ) {
        for (int definitionIndex = 0;
             definitionIndex < orderedEffects.size();
             definitionIndex++) {
            EffectAxeDefinition definition =
                    orderedEffects.get(definitionIndex);
            boolean selected = selectedEffects.contains(definition);
            ItemStack display = itemService.createEffectAxeFuserDisplay(
                    definition,
                    selected
            );
            appendSelectionLore(display, selected);
            inventory.setItem(EFFECT_AXE_SLOTS[definitionIndex], display);
        }
        inventory.setItem(
                FUSE_BUTTON_SLOT,
                BloodstoneServerConstants.AXE_FUSER_FUSE_ITEM.create(
                        Placeholder.unparsed(
                                "price",
                                Integer.toString(
                                        BloodstoneAxeFuserService
                                                .FUSION_BLOOD_ALLOY_COST
                                )
                        )
                )
        );
        inventory.clear(RESULT_PREVIEW_SLOT);
        if (resultPreview != null) {
            inventory.setItem(RESULT_PREVIEW_SLOT, resultPreview);
        } else if (missingSelectedAxes) {
            inventory.setItem(RESULT_PREVIEW_SLOT, missingAxesDisplay());
        }
    }

    Optional<EffectAxeDefinition> clickedDefinition(
            int slot,
            List<EffectAxeDefinition> orderedEffects
    ) {
        for (int definitionIndex = 0;
             definitionIndex < EFFECT_AXE_SLOTS.length;
             definitionIndex++) {
            if (EFFECT_AXE_SLOTS[definitionIndex] == slot) {
                return Optional.of(orderedEffects.get(definitionIndex));
            }
        }
        return Optional.empty();
    }

    boolean matchesTitle(Component candidate) {
        return title.equals(candidate);
    }

    private void appendSelectionLore(ItemStack item, boolean selected) {
        ItemMeta itemMeta = item.getItemMeta();
        List<Component> lore = itemMeta.hasLore()
                ? new ArrayList<>(itemMeta.lore())
                : new ArrayList<>();
        lore.addAll(BloodstoneText.deserializeLines(
                selected
                        ? BloodstoneServerConstants.AXE_FUSER_SELECTED_AXE_LORE
                        : BloodstoneServerConstants.AXE_FUSER_UNSELECTED_AXE_LORE
        ));
        itemMeta.lore(lore);
        item.setItemMeta(itemMeta);
    }

    private ItemStack missingAxesDisplay() {
        return BloodstoneServerConstants.AXE_FUSER_MISSING_AXES_ITEM.create();
    }
}
