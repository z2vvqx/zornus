package net.valoury.bloodstone.server.service;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.valoury.bloodstone.server.BloodstoneServerConstants;
import net.valoury.bloodstone.server.BloodstoneText;
import net.valoury.bloodstone.server.model.BloodstoneRank;
import org.bukkit.Sound;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class BloodstoneUtilityStationService {

    private final BloodstoneCombatService combatService;
    private final BloodstoneCurrencyService currencyService;
    private final BloodstoneMenuService menuService;
    private final BloodstonePresentationService presentationService;
    private final BloodstoneMessageService messageService;

    public BloodstoneUtilityStationService(
            BloodstoneCombatService combatService,
            BloodstoneCurrencyService currencyService,
            BloodstoneMenuService menuService,
            BloodstonePresentationService presentationService,
            BloodstoneMessageService messageService
    ) {
        this.combatService = combatService;
        this.currencyService = currencyService;
        this.menuService = menuService;
        this.presentationService = presentationService;
        this.messageService = messageService;
    }

    public void handle(Player player, Sign sign) {
        String instruction = sign.getLine(1).trim();
        if (instruction.equalsIgnoreCase("spawn")) {
            player.performCommand("spawn");
            player.playSound(
                    player.getLocation(),
                    Sound.NOTE_STICKS,
                    1.0F,
                    presentationService.randomPitch(0.9F, 1.1F)
            );
        } else if (instruction.equalsIgnoreCase("heal")) {
            heal(player);
        } else if (instruction.equalsIgnoreCase("trash")) {
            menuService.openTrash(player);
        } else if (instruction.equalsIgnoreCase("EXP")) {
            giveExperience(player);
        }
    }

    private void heal(Player player) {
        for (PotionEffectType harmfulEffect : List.of(
                PotionEffectType.POISON,
                PotionEffectType.WITHER,
                PotionEffectType.WEAKNESS,
                PotionEffectType.BLINDNESS
        )) {
            if (player.hasPotionEffect(harmfulEffect)) {
                player.removePotionEffect(harmfulEffect);
            }
        }
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.setFireTicks(0);
        presentationService.playMenuNavigation(player);
    }

    private void giveExperience(Player player) {
        if (combatService.isTagged(player.getUniqueId())) {
            reject(player, BloodstoneServerConstants.ERROR_IN_BATTLE);
            return;
        }
        boolean ranked = BloodstoneRank.resolve(player).isPaid();
        if (!ranked
                && !currencyService.removeBlood(player.getInventory(), 1)) {
            messageService.sendRequiredCurrency(
                    player,
                    1,
                    BloodstoneMessageService.Currency.BLOOD
            );
            return;
        }
        int levels = ranked
                ? ThreadLocalRandom.current().nextInt(1, 6)
                : ThreadLocalRandom.current().nextInt(1, 3);
        float progress = ranked
                ? (float) ThreadLocalRandom.current()
                .nextDouble(0.25, 0.75)
                : (float) ThreadLocalRandom.current()
                .nextDouble(0.25, 0.50);
        float combinedProgress = player.getExp() + progress;
        int bonusLevels = (int) Math.floor(combinedProgress);
        player.setLevel(player.getLevel() + levels + bonusLevels);
        player.setExp(combinedProgress - bonusLevels);
        player.playSound(
                player.getLocation(),
                Sound.ORB_PICKUP,
                1.0F,
                1.2F
        );
        if (ThreadLocalRandom.current().nextDouble() < 0.025) {
            player.playSound(
                    player.getLocation(),
                    Sound.LEVEL_UP,
                    1.0F,
                    1.1F
            );
        }
        BloodstoneText.sendActionBar(
                player,
                BloodstoneServerConstants.EXPERIENCE_REWARD_ACTION_BAR_FORMAT,
                Placeholder.unparsed("levels", Integer.toString(levels)),
                Placeholder.unparsed(
                        "progress",
                        String.format(Locale.US, "%.2f", progress)
                )
        );
    }

    private void reject(Player player, String message) {
        messageService.sendError(player, message);
    }
}
