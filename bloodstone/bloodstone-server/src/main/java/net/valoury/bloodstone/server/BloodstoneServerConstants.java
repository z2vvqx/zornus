package net.valoury.bloodstone.server;

import org.bukkit.Material;

import java.util.List;

/**
 * Shared Bloodstone configuration and player-facing presentation.
 *
 * <p>Implementation details and behavior-owned catalogs belong to the class
 * that uses them, not here.</p>
 */
public final class BloodstoneServerConstants {

    // ========================================
    // SERVER CONFIGURATION
    // ========================================

    public static final String WORLD_NAME = "bloodstone";
    public static final String POSTGRESQL_URL = "jdbc:postgresql://localhost:5432/bloodstone";
    public static final String POSTGRESQL_USER = "postgres";
    public static final String POSTGRESQL_PASSWORD = "postword";
    public static final long SHUTDOWN_TIMEOUT_SECONDS = 10;
    public static final long LEADERBOARD_REFRESH_SECONDS = 300L;

    // ========================================
    // MESSAGE CONSTANTS
    // ========================================

    // General
    private static final String MESSAGE_PREFIX = "<dark_aqua>Bloodstone</dark_aqua> <dark_gray>─</dark_gray> ";
    private static final String UNABLE_TO_PROCEED_PREFIX = "Unable to proceed <dark_gray>─</dark_gray> ";

    public static final String ERROR_FORMAT = MESSAGE_PREFIX + "<red><error></red>";
    public static final String COMMAND_PLAYER_ONLY = "This command can only be used by a player.";
    public static final String COMMAND_BLOODSTONE_ONLY = "This command is only available in Bloodstone.";
    public static final String PLAYER_DATA_UNAVAILABLE = "your Bloodstone data is unavailable.";
    public static final String CHAT_DELIVERY_FAILED = "An issue occurred while trying to send this message.";
    public static final String REQUIRED_CURRENCY_FORMAT = "you need <amount> <currency>.";
    public static final String ERROR_IN_BATTLE = UNABLE_TO_PROCEED_PREFIX + "you can't do this in battle.";
    public static final String ERROR_INVENTORY_SPACE = UNABLE_TO_PROCEED_PREFIX + "not enough inventory space.";
    public static final String ERROR_UNRECOGNIZED_ITEM = UNABLE_TO_PROCEED_PREFIX + "unrecognized item type.";
    public static final String ERROR_SHUTTING_DOWN = UNABLE_TO_PROCEED_PREFIX + "Bloodstone is shutting down.";
    public static final String INVENTORY_SPACE_REQUIRED = "You don't have enough inventory space.";
    public static final String PLAYER_DATA_LOADING = "Your Bloodstone data is still loading.";

    // Enchanter
    public static final String ENCHANTER_SHUTTING_DOWN = "Bloodstone is shutting down.";
    public static final String ENCHANTER_ACCESS_REQUIRED = UNABLE_TO_PROCEED_PREFIX + "no enchanter access.";
    public static final String DISENCHANTER_ACCESS_REQUIRED = UNABLE_TO_PROCEED_PREFIX + "no disenchanter access.";
    public static final String ENCHANTER_CAPACITY_REACHED = UNABLE_TO_PROCEED_PREFIX + "four enchanter or disenchanter operations are already active globally.";
    public static final String ENCHANTER_ITEM_REJECTED = UNABLE_TO_PROCEED_PREFIX + "item doesn't accept enchantments.";
    public static final String DISENCHANTER_ITEM_REJECTED = UNABLE_TO_PROCEED_PREFIX + "item doesn't accept disenchanting.";
    public static final String ENCHANTER_ITEM_TOO_POWERFUL = UNABLE_TO_PROCEED_PREFIX + "item is too powerful.";
    public static final String ENCHANTER_HELD_ITEM_CHANGED = "The held item changed before enchanting began.";
    public static final String DISENCHANTER_HELD_ITEM_CHANGED = "The held item changed before disenchanting began.";
    public static final String ENCHANTER_ALREADY_PRESENT = UNABLE_TO_PROCEED_PREFIX + "enchantment already exists.";
    public static final String DISENCHANTER_ENCHANTMENT_MISSING = UNABLE_TO_PROCEED_PREFIX + "item doesn't have this enchantment.";
    public static final String ENCHANTER_HELD_ITEM_RECOVERY = "The held item changed before enchanting began; the reserved item will be recovered.";
    public static final String DISENCHANTER_HELD_ITEM_RECOVERY = "The held item changed before disenchanting began; the reserved item will be recovered.";
    public static final String ENCHANTER_COOLDOWN_ERROR_KEY = "enchanter-cooldown";
    public static final String DISENCHANTER_COOLDOWN_ERROR_KEY = "disenchanter-cooldown";
    public static final String ENCHANTER_COOLDOWN_FORMAT = "cooldown ends <cooldown>.";
    public static final String RESERVED_ITEM_INVENTORY_SPACE_REQUIRED = "make inventory space to recover your reserved item.";

    // Random Box and Repair
    public static final String RANDOM_BOX_BLOCK_IN_USE = UNABLE_TO_PROCEED_PREFIX + "this Random Box is already active.";
    public static final String RANDOM_BOX_RESERVATION_REFUNDED = "The Random Box reservation stopped; your payment was restored.";
    public static final String RANDOM_BOX_PAYMENT_REJECTED = UNABLE_TO_PROCEED_PREFIX + "the Random Box payment was not accepted.";
    public static final String RANDOM_BOX_COST_ACTION_BAR_FORMAT = "<red><italic>-<cost> blood</italic></red>";
    public static final String RANDOM_BOX_WHOOSH = MESSAGE_PREFIX + "<light_purple>Whoosh!</light_purple>";
    public static final String REPAIR_CAPACITY_REACHED = UNABLE_TO_PROCEED_PREFIX + "four repair operations are already active globally.";
    public static final String REPAIR_FULL_DURABILITY = UNABLE_TO_PROCEED_PREFIX + "item already at full durability.";
    public static final String REPAIR_PROTECTED_ITEM = UNABLE_TO_PROCEED_PREFIX + "item is protected (Soulbound/Exclusive).";
    public static final String REPAIR_HELD_ITEM_RECOVERY = "The held item changed before repair began; the reserved item will be recovered.";
    public static final String EXPERIENCE_REWARD_ACTION_BAR_FORMAT = "<green>+<levels> levels</green> <dark_gray>|</dark_gray> <green>+<progress> progress</green>";

    // Commands and Shop
    public static final String POTION_HELD_REQUIRED = "You must be holding the potion you want to stack!";
    public static final String POTION_STACK_QUANTITY_REQUIRED = "You don't have enough potions to stack!";
    public static final String POTION_ALREADY_STACKED = "These potions are already stacked!";
    public static final String POTION_STACK_LIMIT = "Stacking these potions would exceed the safe stack limit!";
    public static final String POTIONS_STACKED = MESSAGE_PREFIX + "<green>Stacked all potions of the held type.</green>";
    public static final String BLOOD_EXCHANGE_REFUNDED = "The exchange could not be delivered; your Blood was restored.";
    public static final String BLOOD_ALLOY_EXCHANGE_REFUNDED = "The exchange could not be delivered; your Blood Alloy was restored.";
    public static final String BLOOD_TO_ALLOY_ACTION_BAR_FORMAT = "<red><italic>-<blood> blood</italic></red> <dark_gray>→</dark_gray> <white>+<alloy> blood alloy</white>";
    public static final String ALLOY_TO_BLOOD_ACTION_BAR_FORMAT = "<red><italic>-<alloy> blood alloy</italic></red> <dark_gray>→</dark_gray> <white>+<blood> blood</white>";
    public static final String EFFECT_AXES_ACCESS_REQUIRED = UNABLE_TO_PROCEED_PREFIX + "no Effect Axes access.";
    public static final String PURCHASE_REFUNDED = "The purchase could not be delivered; your Blood Alloy was restored.";
    public static final String BLOOD_ALLOY_COST_ACTION_BAR_FORMAT = "<red><italic>-<cost> blood alloy</italic></red>";

    // Duels
    public static final String DUEL_ARENA_UNAVAILABLE = "the duel arena is unavailable.";
    public static final String DUEL_USAGE = MESSAGE_PREFIX + "<gray>Usage: <white>/duel \\<player|accept|reject\\></white></gray>";
    public static final String DUEL_SELF = "you can't duel yourself.";
    public static final String DUEL_TARGET_OFFLINE = "that player is not online.";
    public static final String DUEL_PLAYER_BUSY = "you already have a pending or active duel.";
    public static final String DUEL_TARGET_BUSY = "that player already has a pending or active duel.";
    public static final String DUEL_PLAYER_UNAVAILABLE = "you must be alive, ready, and outside combat in Bloodstone.";
    public static final String DUEL_TARGET_UNAVAILABLE = "that player must be alive, ready, and outside combat in Bloodstone.";
    public static final String DUEL_NO_REQUEST = "you don't have an incoming duel request.";
    public static final String DUEL_REQUEST_SENT_FORMAT = MESSAGE_PREFIX + "<green>Duel request sent to <white><player></white>.</green>";
    public static final String DUEL_REQUEST_RECEIVED_FORMAT = MESSAGE_PREFIX + "<white><player></white> <yellow>has challenged you to a duel.</yellow> <click:run_command:'/duel accept'><green>/duel accept</green></click> <dark_gray>or</dark_gray> <click:run_command:'/duel reject'><red>/duel reject</red></click>";
    public static final String DUEL_REQUEST_EXPIRED = MESSAGE_PREFIX + "<gray>The duel request expired.</gray>";
    public static final String DUEL_REQUEST_CANCELLED = MESSAGE_PREFIX + "<gray>The duel request was cancelled.</gray>";
    public static final String DUEL_REQUEST_REJECTED = MESSAGE_PREFIX + "<gray>You rejected the duel request.</gray>";
    public static final String DUEL_REJECTED_FORMAT = MESSAGE_PREFIX + "<white><player></white> <red>rejected your duel request.</red>";
    public static final String DUEL_TELEPORT_FAILED = "the duel arena could not be entered.";
    public static final String DUEL_COUNTDOWN_ACTION_BAR_FORMAT = "<yellow>Duel starts in <red><seconds></red>...</yellow>";
    public static final String DUEL_STARTED_ACTION_BAR = "<red><bold>FIGHT!</bold></red>";
    public static final String DUEL_VICTORY_FORMAT = MESSAGE_PREFIX + "<green>You defeated <white><player></white>.</green>";
    public static final String DUEL_DEFEAT_FORMAT = MESSAGE_PREFIX + "<red>You were defeated by <white><player></white>.</red>";
    public static final String DUEL_FORFEIT_VICTORY_FORMAT = MESSAGE_PREFIX + "<green><player> forfeited the duel.</green>";

    // Combat
    public static final String COMBAT_ENTER_ACTION_BAR = "<red>You're now in <underlined>battle</underlined>, don't logout!</red>";
    public static final String COMBAT_EXIT_ACTION_BAR = "<green>You're out of <underlined>battle</underlined>, logout safely.</green>";
    public static final String UNATTRIBUTED_DEATH_MESSAGE = MESSAGE_PREFIX + "<gray>You forgot to remain alive.</gray>";
    public static final String KILLER_MESSAGE_FORMAT = MESSAGE_PREFIX + "<green>You eliminated <white><victim></white> with <gold><health>❤</gold> remaining.</green>";
    public static final String VICTIM_MESSAGE_FORMAT = MESSAGE_PREFIX + "<red>You were eliminated by <white><killer></white> with <gold><health>❤</gold> remaining.</red>";
    public static final String ARROW_FEEDBACK_FORMAT = MESSAGE_PREFIX + "<shot> <green>on <white><victim></white>, they have <gold><health>❤</gold> remaining.</green>";
    public static final String HEADSHOT_DISPLAY = "<red>Headshot</red>";
    public static final String CONTRIBUTION_HEAL_ACTION_BAR_FORMAT = "<green><share><bold>%</bold>     +<healing></green><dark_red><bold>❤</bold></dark_red>";
    public static final String DOMINATION_TITLE = "<red><bold>DOMINATION</bold></red>";
    public static final String DOMINATION_SUBTITLE_FORMAT = "<gray>You dominate <white><victim></white></gray>";
    public static final String DOMINATED_TITLE = "<red><bold>DOMINATED</bold></red>";
    public static final String DOMINATED_SUBTITLE_FORMAT = "<gray>Take revenge on <white><dominator></white></gray>";
    public static final String DOMINATION_LOST_TITLE = "<red><bold>DOMINATION LOST</bold></red>";
    public static final String DOMINATION_LOST_SUBTITLE_FORMAT = "<white><player></white> <gray>earned revenge</gray>";
    public static final String REVENGE_TITLE = "<gold><bold>REVENGE</bold></gold>";
    public static final String REVENGE_SUBTITLE_FORMAT = "<gray>You ended <white><dominator></white>'s domination</gray>";

    // Storage and Player Lifecycle
    public static final String STORAGE_SHUTTING_DOWN = "Bloodstone storage is shutting down.";
    public static final String STORAGE_LOCKED = "This storage is locked.";
    public static final String STORAGE_IN_USE_ERROR_KEY = "storage-in-use";
    public static final String STORAGE_IN_USE_FORMAT = "This storage is already being edited; its lease expires <expiration>.";
    public static final String STORAGE_OPERATION_IN_PROGRESS = "A storage operation is already in progress.";
    public static final String STORAGE_PREVIOUS_SAVE_FAILED = "The previous storage could not be saved safely.";
    public static final String STORAGE_PROFILE_LOADING = "Your Bloodstone profile is still loading.";
    public static final String STORAGE_LOAD_FAILED = "This storage could not be loaded safely.";
    public static final String STORAGE_STALE_SAVE = "Storage changed elsewhere; your stale edit was not saved.";
    public static final String EXTRA_STORAGE_PURCHASE_PENDING = "An Extra Storage purchase is already in progress.";
    public static final String EXTRA_STORAGE_PROFILE_NOT_READY = "Your profile is not ready; the cost was not charged.";
    public static final String EXTRA_STORAGE_COST_ACTION_BAR_FORMAT = "<red><italic>-<price> blood alloy</italic></red>";
    public static final String EXTRA_STORAGE_PURCHASED = MESSAGE_PREFIX + "<green>You purchased Extra Storage!</green>";
    public static final String EXTRA_STORAGE_RECOVERING = "The purchase result is being recovered from storage.";
    public static final String SOULBOUND_RETURN_ACTION_BAR = "<dark_purple>Your Soulbound items returned.</dark_purple>";
    public static final String PLAYER_DATA_LOAD_FAILED_KICK = "<red>Your Bloodstone data could not be loaded safely.</red>";
    public static final String BASELINE_RESTORED_ACTION_BAR = "<green>Your baseline kit has been restored.</green>";

    // ========================================
    // DISPLAY CONSTANTS
    // ========================================

    public static final String CHAT_FORMAT = "<dark_gray>❘</dark_gray> <gray><ratio></gray> <dark_gray>❘</dark_gray> <suffix><player> <dark_gray>»</dark_gray> <white><message></white>";
    public static final String PLAYER_LEADERBOARD_ENTRY_FORMAT = "<white><player></white><guild> <icon> <gray><value></gray>";
    public static final String GUILD_LEADERBOARD_ENTRY_FORMAT = "<guild> <icon> <gray><value></gray>";
    public static final String EMPTY_LEADERBOARD_ENTRY = "<dark_gray><strikethrough>--</strikethrough> <strikethrough>--</strikethrough> <strikethrough>--</strikethrough> <strikethrough>--</strikethrough></dark_gray> <icon> <gray>0</gray>";

    // ========================================
    // MENU CONSTANTS
    // ========================================

    // Navigation
    public static final int MENU_ROWS = 5;
    public static final int MENU_BACK_SLOT = 29;
    public static final int MENU_HOME_SLOT = 31;
    public static final int MENU_EXIT_SLOT = 33;
    public static final BloodstoneMenuItem MENU_BACK_ITEM = new BloodstoneMenuItem(Material.ARROW, "<green><bold>BACK</bold></green>");
    public static final BloodstoneMenuItem MENU_HOME_ITEM = new BloodstoneMenuItem(Material.BED, "<gold><bold>HOME</bold></gold>");
    public static final BloodstoneMenuItem MENU_EXIT_ITEM = new BloodstoneMenuItem(Material.BARRIER, "<red><bold>EXIT</bold></red>");

    // Main Menu
    public static final String MAIN_MENU_TITLE = "Bloodstone";
    public static final int MAIN_MENU_GEAR_SLOT = 11;
    public static final int MAIN_MENU_ARMOR_SLOT = 12;
    public static final int MAIN_MENU_EFFECT_AXES_SLOT = 13;
    public static final int MAIN_MENU_POTIONS_SLOT = 14;
    public static final int MAIN_MENU_EXCHANGE_SLOT = 15;
    public static final int MAIN_MENU_EXIT_SLOT = 31;
    public static final BloodstoneMenuItem MAIN_MENU_GEAR_ITEM = new BloodstoneMenuItem(Material.DIAMOND_SWORD, "<aqua><bold>Gear</bold></aqua>");
    public static final BloodstoneMenuItem MAIN_MENU_ARMOR_ITEM = new BloodstoneMenuItem(Material.DIAMOND_CHESTPLATE, "<dark_aqua><bold>Armor</bold></dark_aqua>");
    public static final BloodstoneMenuItem MAIN_MENU_EFFECT_AXES_ITEM = new BloodstoneMenuItem(Material.DIAMOND_AXE, "<blue><bold>Effect Axes</bold></blue>");
    public static final BloodstoneMenuItem MAIN_MENU_POTIONS_ITEM = new BloodstoneMenuItem(Material.POTION, "<dark_aqua><bold>Potions</bold></dark_aqua>");
    public static final BloodstoneMenuItem MAIN_MENU_EXCHANGE_ITEM = new BloodstoneMenuItem(Material.REDSTONE, "<aqua><bold>Exchange</bold></aqua>");

    // Shop Menus
    public static final String GEAR_MENU_TITLE = "Bloodstone: Gear";
    public static final String ARMOR_MENU_TITLE = "Bloodstone: Armor";
    public static final String EFFECT_AXES_MENU_TITLE = "Bloodstone: Effect Axes";
    public static final String POTIONS_MENU_TITLE = "Bloodstone: Potions";
    public static final String EXCHANGE_MENU_TITLE = "Bloodstone: Exchange";
    public static final String TRASH_MENU_TITLE = "Trash";
    public static final String MENU_PRICE_LORE_FORMAT = " <gray>Price: <dark_red><bold><price>⛃</bold> <currency></dark_red></gray>";
    public static final String MENU_PURCHASE_LORE = "<green>➟ Click to purchase</green>";

    // Enchanter Menu
    public static final String ENCHANTER_MENU_TITLE = "Enchanter";
    public static final String DISENCHANTER_MENU_TITLE = "Disenchanter";
    public static final BloodstoneMenuItem ENCHANTER_OPTION_ITEM = new BloodstoneMenuItem(
            Material.ENCHANTED_BOOK,
            "<dark_purple><enchantment>: <level></dark_purple>",
            List.of("", " <gray>Apply this enchantment to the held item.</gray>", "", "<green>➟ Click to apply this enchantment!</green>")
    );

    public static final BloodstoneMenuItem DISENCHANTER_OPTION_ITEM = new BloodstoneMenuItem(
            Material.ENCHANTED_BOOK,
            "<dark_purple><enchantment>: <level></dark_purple>",
            List.of(
                    "",
                    " <gray>Remove this enchantment from the held item.</gray>",
                    "",
                    "<green>➟ Click to remove this enchantment!</green>"
            )
    );

    // Storage Menu
    public static final String STORAGE_MENU_TITLE = "Bloodstone: Storages";
    public static final int STORAGE_MENU_ROWS = 3;
    public static final int DEFAULT_STORAGE_SLOT = 10;
    public static final int IRON_STORAGE_SLOT = 11;
    public static final int GOLD_STORAGE_SLOT = 12;
    public static final int DIAMOND_STORAGE_SLOT = 13;
    public static final int EMERALD_STORAGE_SLOT = 14;
    public static final int EXTRA_STORAGE_SLOT = 15;
    public static final int GUILD_STASH_SLOT = 16;
    public static final String STORAGE_INVENTORY_TITLE_FORMAT = "Storages: <storage>";
    public static final BloodstoneMenuItem STORAGE_UNLOCKED_ITEM = new BloodstoneMenuItem(
            Material.STORAGE_MINECART,
            "<dark_aqua><bold><storage> STORAGE</bold></dark_aqua>",
            List.of("", "<gray>You're eligible for this storage.</gray>", "", "<green>➟ Click to open this storage.</green>")
    );
    public static final BloodstoneMenuItem STORAGE_LOCKED_ITEM = new BloodstoneMenuItem(
            Material.MINECART,
            "<red><bold><storage> STORAGE</bold></red>",
            List.of("", "<gray>You're not eligible for this storage.</gray>", "", "<red>➟ Requires the <storage> rank.</red>")
    );
    public static final BloodstoneMenuItem EXTRA_STORAGE_UNLOCKED_ITEM = new BloodstoneMenuItem(
            Material.STORAGE_MINECART,
            "<dark_aqua><bold>EXTRA STORAGE</bold></dark_aqua>",
            List.of("", "<gray>You're eligible for this storage.</gray>", "", "<green>➟ Click to open this storage.</green>")
    );
    public static final BloodstoneMenuItem EXTRA_STORAGE_LOCKED_ITEM = new BloodstoneMenuItem(
            Material.MINECART,
            "<red><bold>EXTRA STORAGE</bold></red>",
            List.of(
                    "",
                    "<gray>You're not eligible for this storage.</gray>",
                    "",
                    " <gray>Price: <dark_red><bold><price>⛃</bold> blood alloy</dark_red></gray>",
                    "",
                    "<green>➟ Click to purchase this storage</green>"
            )
    );
    public static final BloodstoneMenuItem GUILD_STASH_ITEM = new BloodstoneMenuItem(
            Material.MINECART,
            "<red><bold>Guild Stash — Soon</bold></red>",
            List.of("", "<gray>This feature is not available yet.</gray>")
    );

    private BloodstoneServerConstants() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }
}
