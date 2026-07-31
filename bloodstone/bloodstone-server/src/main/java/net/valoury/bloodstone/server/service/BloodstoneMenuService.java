package net.valoury.bloodstone.server.service;

import net.valoury.bloodstone.server.BloodstoneServerConstants;
import net.valoury.bloodstone.server.BloodstoneText;
import net.valoury.bloodstone.server.EffectAxeDefinitions;
import net.valoury.bloodstone.server.model.BloodstoneRank;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class BloodstoneMenuService {

    private static final int BLOOD_PER_ALLOY = 64;
    private static final int ALLOY_PER_EXCHANGE = 1;
    private static final Set<Component> MENU_TITLES = Set.of(
            BloodstoneText.deserialize(BloodstoneServerConstants.MAIN_MENU_TITLE),
            BloodstoneText.deserialize(BloodstoneServerConstants.GEAR_MENU_TITLE),
            BloodstoneText.deserialize(BloodstoneServerConstants.ARMOR_MENU_TITLE),
            BloodstoneText.deserialize(BloodstoneServerConstants.EFFECT_AXES_MENU_TITLE),
            BloodstoneText.deserialize(BloodstoneServerConstants.POTIONS_MENU_TITLE),
            BloodstoneText.deserialize(BloodstoneServerConstants.EXCHANGE_MENU_TITLE),
            BloodstoneText.deserialize(BloodstoneServerConstants.TRASH_MENU_TITLE)
    );

    private final BloodstoneItemService itemService;
    private final BloodstonePresentationService presentationService;
    private final BloodstoneMessageService messageService;

    public BloodstoneMenuService(
            BloodstoneItemService itemService,
            BloodstonePresentationService presentationService,
            BloodstoneMessageService messageService
    ) {
        this.itemService = itemService;
        this.presentationService = presentationService;
        this.messageService = messageService;
    }

    public boolean isInBloodstone(Player player) {
        return BloodstoneServerConstants.WORLD_NAME.equals(player.getWorld().getName());
    }

    public void openMainMenu(Player player) {
        Inventory inventory = createNavigationInventory(
                BloodstoneServerConstants.MAIN_MENU_TITLE
        );
        inventory.setItem(
                BloodstoneServerConstants.MAIN_MENU_GEAR_SLOT,
                BloodstoneServerConstants.MAIN_MENU_GEAR_ITEM.create()
        );
        inventory.setItem(
                BloodstoneServerConstants.MAIN_MENU_ARMOR_SLOT,
                BloodstoneServerConstants.MAIN_MENU_ARMOR_ITEM.create()
        );
        inventory.setItem(
                BloodstoneServerConstants.MAIN_MENU_EFFECT_AXES_SLOT,
                BloodstoneServerConstants.MAIN_MENU_EFFECT_AXES_ITEM.create()
        );
        inventory.setItem(
                BloodstoneServerConstants.MAIN_MENU_POTIONS_SLOT,
                BloodstoneServerConstants.MAIN_MENU_POTIONS_ITEM.create()
        );
        inventory.setItem(
                BloodstoneServerConstants.MAIN_MENU_EXCHANGE_SLOT,
                BloodstoneServerConstants.MAIN_MENU_EXCHANGE_ITEM.create()
        );
        open(player, inventory);
    }

    public void openTrash(Player player) {
        open(player, Bukkit.createInventory(
                null,
                45,
                BloodstoneText.deserialize(BloodstoneServerConstants.TRASH_MENU_TITLE)
        ));
    }

    public void handleInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !isInBloodstone(player)) {
            return;
        }
        Component title = event.getView().title();
        if (!isMenuTitle(title)) {
            return;
        }
        if (matchesTitle(title, BloodstoneServerConstants.TRASH_MENU_TITLE)) {
            return;
        }

        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot == BloodstoneServerConstants.MENU_EXIT_SLOT
                || (matchesTitle(title, BloodstoneServerConstants.MAIN_MENU_TITLE)
                && slot == BloodstoneServerConstants.MAIN_MENU_EXIT_SLOT)) {
            player.closeInventory();
            return;
        }
        if ((slot == BloodstoneServerConstants.MENU_BACK_SLOT
                || slot == BloodstoneServerConstants.MENU_HOME_SLOT)
                && !matchesTitle(title, BloodstoneServerConstants.MAIN_MENU_TITLE)) {
            openMainMenu(player);
            return;
        }

        if (matchesTitle(title, BloodstoneServerConstants.MAIN_MENU_TITLE)) {
            handleMainClick(player, slot);
        } else if (matchesTitle(title, BloodstoneServerConstants.GEAR_MENU_TITLE)) {
            handleGearClick(player, slot);
        } else if (matchesTitle(title, BloodstoneServerConstants.ARMOR_MENU_TITLE)) {
            handleArmorClick(player, slot);
        } else if (matchesTitle(
                title,
                BloodstoneServerConstants.EFFECT_AXES_MENU_TITLE
        )) {
            handleEffectAxeClick(player, slot);
        } else if (matchesTitle(title, BloodstoneServerConstants.POTIONS_MENU_TITLE)) {
            handlePotionClick(player, slot);
        } else if (matchesTitle(title, BloodstoneServerConstants.EXCHANGE_MENU_TITLE)) {
            handleExchangeClick(player, slot);
        }
    }

    public void stackHeldPotions(Player player) {
        ItemStack heldItem = player.getItemInHand();
        if (heldItem == null || heldItem.getType() != Material.POTION) {
            reject(player, BloodstoneServerConstants.POTION_HELD_REQUIRED);
            return;
        }

        long total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.isSimilar(heldItem)) {
                total += item.getAmount();
            }
        }
        if (total < 2) {
            reject(player, BloodstoneServerConstants.POTION_STACK_QUANTITY_REQUIRED);
            return;
        }
        if (heldItem.getAmount() == total) {
            reject(player, BloodstoneServerConstants.POTION_ALREADY_STACKED);
            return;
        }
        if (total > 64) {
            reject(player, BloodstoneServerConstants.POTION_STACK_LIMIT);
            return;
        }

        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item != null && item.isSimilar(heldItem)) {
                player.getInventory().clear(slot);
            }
        }
        ItemStack stacked = heldItem.clone();
        stacked.setAmount((int) total);
        player.setItemInHand(stacked);
        BloodstoneText.sendMessage(
                player,
                BloodstoneServerConstants.POTIONS_STACKED
        );
        player.playSound(player.getLocation(), Sound.ITEM_PICKUP, 1.0F, 1.0F);
    }

    public boolean exchangeBloodForAlloy(Player player) {
        if (itemService.countBlood(player.getInventory()) < BLOOD_PER_ALLOY) {
            messageService.sendRequiredCurrency(
                    player,
                    BLOOD_PER_ALLOY,
                    BloodstoneMessageService.Currency.BLOOD
            );
            return false;
        }
        ItemStack reward = itemService.createBloodAlloy(ALLOY_PER_EXCHANGE);
        if (!canFit(player.getInventory(), reward)) {
            reject(player, BloodstoneServerConstants.ERROR_INVENTORY_SPACE);
            return false;
        }
        if (!itemService.removeBlood(player.getInventory(), BLOOD_PER_ALLOY)) {
            messageService.sendRequiredCurrency(
                    player,
                    BLOOD_PER_ALLOY,
                    BloodstoneMessageService.Currency.BLOOD
            );
            return false;
        }
        if (!player.getInventory().addItem(reward).isEmpty()) {
            itemService.addBlood(player.getInventory(), BLOOD_PER_ALLOY);
            reject(player, BloodstoneServerConstants.BLOOD_EXCHANGE_REFUNDED);
            return false;
        }
        BloodstoneText.sendActionBar(
                player,
                BloodstoneServerConstants.BLOOD_TO_ALLOY_ACTION_BAR_FORMAT,
                exchangeAmountResolvers()
        );
        player.playSound(player.getLocation(), Sound.IRONGOLEM_THROW, 1.0F, 1.65F);
        return true;
    }

    public boolean exchangeAlloyForBlood(Player player) {
        if (itemService.countBloodAlloy(player.getInventory()) < ALLOY_PER_EXCHANGE) {
            messageService.sendRequiredCurrency(
                    player,
                    ALLOY_PER_EXCHANGE,
                    BloodstoneMessageService.Currency.BLOOD_ALLOY
            );
            return false;
        }
        ItemStack reward = itemService.createBlood(BLOOD_PER_ALLOY);
        if (!canFit(player.getInventory(), reward)) {
            reject(player, BloodstoneServerConstants.ERROR_INVENTORY_SPACE);
            return false;
        }
        if (!itemService.removeBloodAlloy(
                player.getInventory(),
                ALLOY_PER_EXCHANGE
        )) {
            messageService.sendRequiredCurrency(
                    player,
                    ALLOY_PER_EXCHANGE,
                    BloodstoneMessageService.Currency.BLOOD_ALLOY
            );
            return false;
        }
        if (!player.getInventory().addItem(reward).isEmpty()) {
            itemService.addBloodAlloy(
                    player.getInventory(),
                    ALLOY_PER_EXCHANGE
            );
            reject(player, BloodstoneServerConstants.BLOOD_ALLOY_EXCHANGE_REFUNDED);
            return false;
        }
        BloodstoneText.sendActionBar(
                player,
                BloodstoneServerConstants.ALLOY_TO_BLOOD_ACTION_BAR_FORMAT,
                exchangeAmountResolvers()
        );
        player.playSound(player.getLocation(), Sound.IRONGOLEM_THROW, 1.0F, 1.65F);
        return true;
    }

    private void handleMainClick(Player player, int slot) {
        switch (slot) {
            case BloodstoneServerConstants.MAIN_MENU_GEAR_SLOT -> openGearMenu(player);
            case BloodstoneServerConstants.MAIN_MENU_ARMOR_SLOT -> openArmorMenu(player);
            case BloodstoneServerConstants.MAIN_MENU_EFFECT_AXES_SLOT -> openEffectAxesMenu(player);
            case BloodstoneServerConstants.MAIN_MENU_POTIONS_SLOT -> openPotionsMenu(player);
            case BloodstoneServerConstants.MAIN_MENU_EXCHANGE_SLOT -> openExchangeMenu(player);
            default -> {
            }
        }
    }

    private void openGearMenu(Player player) {
        Inventory inventory = createNavigationInventory(
                BloodstoneServerConstants.GEAR_MENU_TITLE
        );
        setProduct(inventory, 10, BloodstoneItemService.ShopProduct.SHARPNESS_IV_SWORD);
        setProduct(inventory, 11, BloodstoneItemService.ShopProduct.SHARPNESS_V_SWORD);
        setProduct(inventory, 13, BloodstoneItemService.ShopProduct.POWER_V_BOW);
        setProduct(inventory, 15, BloodstoneItemService.ShopProduct.SHARPNESS_IV_AXE);
        setProduct(inventory, 16, BloodstoneItemService.ShopProduct.SHARPNESS_V_AXE);
        open(player, inventory);
    }

    private void openArmorMenu(Player player) {
        Inventory inventory = createNavigationInventory(
                BloodstoneServerConstants.ARMOR_MENU_TITLE
        );
        setProduct(inventory, 11, BloodstoneItemService.ShopProduct.PROTECTION_IV_HELMET);
        setProduct(inventory, 12, BloodstoneItemService.ShopProduct.PROTECTION_IV_CHESTPLATE);
        setProduct(inventory, 14, BloodstoneItemService.ShopProduct.PROTECTION_IV_LEGGINGS);
        setProduct(inventory, 15, BloodstoneItemService.ShopProduct.PROTECTION_IV_BOOTS);
        open(player, inventory);
    }

    private void openEffectAxesMenu(Player player) {
        Inventory inventory = createNavigationInventory(
                BloodstoneServerConstants.EFFECT_AXES_MENU_TITLE
        );
        BloodstoneRank rank = BloodstoneRank.resolve(player);
        List<EffectAxeDefinitions.EffectAxeDefinition> definitions =
                effectAxesByDescendingPrice(rank);
        int[] slots = {10, 11, 12, 14, 15, 16};
        for (int index = 0; index < definitions.size(); index++) {
            EffectAxeDefinitions.EffectAxeDefinition definition =
                    definitions.get(index);
            ItemStack display = itemService.createEffectAxeMenuDisplay(
                    definition
            );
            appendPriceLore(
                    display,
                    definition.bloodAlloyCost(rank),
                    BloodstoneMessageService.Currency.BLOOD_ALLOY.displayName(),
                    List.of(BloodstoneServerConstants.EFFECT_AXE_RANK_PRICE_LORE)
            );
            inventory.setItem(slots[index], display);
        }
        open(player, inventory);
    }

    private void openPotionsMenu(Player player) {
        Inventory inventory = createNavigationInventory(
                BloodstoneServerConstants.POTIONS_MENU_TITLE
        );
        setProduct(inventory, 11, BloodstoneItemService.ShopProduct.GOLDEN_APPLE);
        setProduct(inventory, 12, BloodstoneItemService.ShopProduct.STRENGTH_POTION);
        setProduct(inventory, 13, BloodstoneItemService.ShopProduct.RESISTANCE_POTION);
        setProduct(inventory, 14, BloodstoneItemService.ShopProduct.SPEED_POTION);
        setProduct(inventory, 15, BloodstoneItemService.ShopProduct.FIRE_RESISTANCE_POTION);
        open(player, inventory);
    }

    private void openExchangeMenu(Player player) {
        Inventory inventory = createNavigationInventory(
                BloodstoneServerConstants.EXCHANGE_MENU_TITLE
        );
        ItemStack alloy = itemService.prepareForMenuDisplay(
                itemService.createBloodAlloy(ALLOY_PER_EXCHANGE)
        );
        appendPriceLore(
                alloy,
                BLOOD_PER_ALLOY,
                BloodstoneMessageService.Currency.BLOOD.displayName()
        );
        inventory.setItem(12, alloy);
        ItemStack blood = itemService.prepareForMenuDisplay(
                itemService.createBlood(BLOOD_PER_ALLOY)
        );
        appendPriceLore(
                blood,
                ALLOY_PER_EXCHANGE,
                BloodstoneMessageService.Currency.BLOOD_ALLOY.displayName()
        );
        inventory.setItem(14, blood);
        open(player, inventory);
    }

    private void handleGearClick(Player player, int slot) {
        switch (slot) {
            case 10 -> purchase(player, BloodstoneItemService.ShopProduct.SHARPNESS_IV_SWORD);
            case 11 -> purchase(player, BloodstoneItemService.ShopProduct.SHARPNESS_V_SWORD);
            case 13 -> purchase(player, BloodstoneItemService.ShopProduct.POWER_V_BOW);
            case 15 -> purchase(player, BloodstoneItemService.ShopProduct.SHARPNESS_IV_AXE);
            case 16 -> purchase(player, BloodstoneItemService.ShopProduct.SHARPNESS_V_AXE);
            default -> {
            }
        }
    }

    private void handleArmorClick(Player player, int slot) {
        switch (slot) {
            case 11 -> purchase(player, BloodstoneItemService.ShopProduct.PROTECTION_IV_HELMET);
            case 12 -> purchase(player, BloodstoneItemService.ShopProduct.PROTECTION_IV_CHESTPLATE);
            case 14 -> purchase(player, BloodstoneItemService.ShopProduct.PROTECTION_IV_LEGGINGS);
            case 15 -> purchase(player, BloodstoneItemService.ShopProduct.PROTECTION_IV_BOOTS);
            default -> {
            }
        }
    }

    private void handleEffectAxeClick(Player player, int slot) {
        int index = switch (slot) {
            case 10 -> 0;
            case 11 -> 1;
            case 12 -> 2;
            case 14 -> 3;
            case 15 -> 4;
            case 16 -> 5;
            default -> -1;
        };
        if (index < 0) {
            return;
        }
        BloodstoneRank rank = BloodstoneRank.resolve(player);
        EffectAxeDefinitions.EffectAxeDefinition definition =
                effectAxesByDescendingPrice(rank).get(index);
        purchaseForAlloy(
                player,
                itemService.createEffectAxe(definition),
                definition.bloodAlloyCost(rank)
        );
    }

    static List<EffectAxeDefinitions.EffectAxeDefinition> effectAxesByDescendingPrice(
            BloodstoneRank rank
    ) {
        Objects.requireNonNull(rank, "Bloodstone rank cannot be null");
        return EffectAxeDefinitions.values().stream()
                .sorted(Comparator.comparingInt(
                        (EffectAxeDefinitions.EffectAxeDefinition definition) ->
                                definition.bloodAlloyCost(rank)
                ).reversed())
                .toList();
    }

    private void handlePotionClick(Player player, int slot) {
        switch (slot) {
            case 11 -> purchase(player, BloodstoneItemService.ShopProduct.GOLDEN_APPLE);
            case 12 -> purchase(player, BloodstoneItemService.ShopProduct.STRENGTH_POTION);
            case 13 -> purchase(player, BloodstoneItemService.ShopProduct.RESISTANCE_POTION);
            case 14 -> purchase(player, BloodstoneItemService.ShopProduct.SPEED_POTION);
            case 15 -> purchase(player, BloodstoneItemService.ShopProduct.FIRE_RESISTANCE_POTION);
            default -> {
            }
        }
    }

    private void handleExchangeClick(Player player, int slot) {
        if (slot == 12) {
            exchangeBloodForAlloy(player);
        } else if (slot == 14) {
            exchangeAlloyForBlood(player);
        }
    }

    private void purchase(Player player, BloodstoneItemService.ShopProduct product) {
        purchaseForAlloy(player, itemService.createShopItem(product), product.bloodAlloyCost());
    }

    private void purchaseForAlloy(Player player, ItemStack reward, int price) {
        if (itemService.countBloodAlloy(player.getInventory()) < price) {
            messageService.sendRequiredCurrency(
                    player,
                    price,
                    BloodstoneMessageService.Currency.BLOOD_ALLOY
            );
            return;
        }
        if (!canFit(player.getInventory(), reward)) {
            reject(player, BloodstoneServerConstants.INVENTORY_SPACE_REQUIRED);
            player.closeInventory();
            return;
        }
        if (!itemService.removeBloodAlloy(player.getInventory(), price)) {
            messageService.sendRequiredCurrency(
                    player,
                    price,
                    BloodstoneMessageService.Currency.BLOOD_ALLOY
            );
            return;
        }
        if (!player.getInventory().addItem(reward).isEmpty()) {
            itemService.addBloodAlloy(player.getInventory(), price);
            reject(player, BloodstoneServerConstants.PURCHASE_REFUNDED);
            return;
        }
        BloodstoneText.sendActionBar(
                player,
                BloodstoneServerConstants.BLOOD_ALLOY_COST_ACTION_BAR_FORMAT,
                Placeholder.unparsed("cost", Integer.toString(price))
        );
        player.playSound(player.getLocation(), Sound.LEVEL_UP, 0.7F, 1.8F);
    }

    private Inventory createNavigationInventory(String title) {
        Inventory inventory = Bukkit.createInventory(
                null,
                BloodstoneServerConstants.MENU_ROWS * 9,
                BloodstoneText.deserialize(title)
        );
        if (!BloodstoneServerConstants.MAIN_MENU_TITLE.equals(title)) {
            inventory.setItem(
                    BloodstoneServerConstants.MENU_BACK_SLOT,
                    BloodstoneServerConstants.MENU_BACK_ITEM.create()
            );
            inventory.setItem(
                    BloodstoneServerConstants.MENU_HOME_SLOT,
                    BloodstoneServerConstants.MENU_HOME_ITEM.create()
            );
            inventory.setItem(
                    BloodstoneServerConstants.MENU_EXIT_SLOT,
                    BloodstoneServerConstants.MENU_EXIT_ITEM.create()
            );
        } else {
            inventory.setItem(
                    BloodstoneServerConstants.MAIN_MENU_EXIT_SLOT,
                    BloodstoneServerConstants.MENU_EXIT_ITEM.create()
            );
        }
        return inventory;
    }

    private void setProduct(Inventory inventory, int slot, BloodstoneItemService.ShopProduct product) {
        ItemStack display = itemService.createShopMenuDisplay(product);
        appendPriceLore(
                display,
                product.bloodAlloyCost(),
                BloodstoneMessageService.Currency.BLOOD_ALLOY.displayName()
        );
        inventory.setItem(slot, display);
    }

    private void appendPriceLore(ItemStack item, int price, String currency) {
        appendPriceLore(item, price, currency, List.of());
    }

    private void appendPriceLore(
            ItemStack item,
            int price,
            String currency,
            List<String> priceContextLore
    ) {
        ItemMeta itemMeta = item.getItemMeta();
        List<Component> lore = itemMeta.hasLore()
                ? new ArrayList<>(itemMeta.lore())
                : new ArrayList<>();
        lore.add(Component.empty());
        priceContextLore.stream()
                .map(BloodstoneText::deserialize)
                .forEach(lore::add);
        lore.add(BloodstoneText.deserialize(
                BloodstoneServerConstants.MENU_PRICE_LORE_FORMAT,
                Placeholder.unparsed("price", Integer.toString(price)),
                Placeholder.unparsed("currency", currency)
        ));
        lore.add(Component.empty());
        lore.add(BloodstoneText.deserialize(
                BloodstoneServerConstants.MENU_PURCHASE_LORE
        ));
        itemMeta.lore(lore);
        item.setItemMeta(itemMeta);
    }

    private TagResolver exchangeAmountResolvers() {
        return TagResolver.resolver(
                Placeholder.unparsed("blood", Integer.toString(BLOOD_PER_ALLOY)),
                Placeholder.unparsed(
                        "alloy",
                        Integer.toString(ALLOY_PER_EXCHANGE)
                )
        );
    }

    private boolean canFit(Inventory inventory, ItemStack candidate) {
        int remaining = candidate.getAmount();
        for (ItemStack item : inventory.getContents()) {
            if (item == null || item.getType() == Material.AIR) {
                return true;
            }
            if (item.isSimilar(candidate)) {
                remaining -= Math.max(0, item.getMaxStackSize() - item.getAmount());
                if (remaining <= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isMenuTitle(Component title) {
        return MENU_TITLES.contains(title);
    }

    private boolean matchesTitle(Component title, String titleTemplate) {
        return BloodstoneText.deserialize(titleTemplate).equals(title);
    }

    private void open(Player player, Inventory inventory) {
        player.openInventory(inventory);
        presentationService.playMenuNavigation(player);
    }

    private void reject(Player player, String message) {
        messageService.sendError(player, message);
    }

}
