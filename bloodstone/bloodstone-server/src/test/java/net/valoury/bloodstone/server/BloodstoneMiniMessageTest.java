package net.valoury.bloodstone.server;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.util.adventure.AdventureSerializer;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class BloodstoneMiniMessageTest {

    private static final TagResolver TEST_PLACEHOLDERS = TagResolver.resolver(
            Stream.of(
                            "error",
                            "ratio",
                            "player",
                            "playername",
                            "message",
                            "amount",
                            "currency",
                            "blood",
                            "alloy",
                            "cost",
                            "enchantment",
                            "cooldown",
                            "seconds",
                            "victim",
                            "killer",
                            "health",
                            "armor",
                            "shot",
                            "share",
                            "healing",
                            "guild",
                            "record",
                            "text",
                            "weapon",
                            "value",
                            "storage",
                            "price",
                            "requirement",
                            "level",
                            "name",
                            "tag",
                            "progress",
                            "levels"
                    )
                    .map(name -> Placeholder.unparsed(name, "value"))
                    .toArray(TagResolver[]::new)
    );

    @Test
    void presentationConstantsUseValidStrictMiniMessageWithoutLegacyCodes()
            throws IllegalAccessException {
        for (Field field : BloodstoneServerConstants.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            Object value = field.get(null);
            if (value instanceof String template) {
                assertTemplate(field.getName(), template);
            } else if (value instanceof List<?> templates) {
                for (int index = 0; index < templates.size(); index++) {
                    Object entry = templates.get(index);
                    if (entry instanceof String template) {
                        assertTemplate(field.getName() + "[" + index + "]", template);
                    }
                }
            } else if (value instanceof BloodstoneMenuItem menuItem) {
                assertTemplate(field.getName() + ".name", menuItem.nameTemplate());
                for (int index = 0; index < menuItem.loreTemplates().size(); index++) {
                    assertTemplate(
                            field.getName() + ".lore[" + index + "]",
                            menuItem.loreTemplates().get(index)
                    );
                }
            }
        }
        for (EffectAxeDefinitions.EffectAxeDefinition definition
                : EffectAxeDefinitions.values()) {
            assertTemplate(
                    definition.id() + ".name",
                    definition.displayNameTemplate()
            );
            assertTemplate(
                    definition.id() + ".lore",
                    definition.effectLoreTemplate()
            );
        }
        for (CombinedEffectAxeDefinitions.CombinedEffectAxeDefinition definition
                : CombinedEffectAxeDefinitions.values()) {
            assertTemplate(
                    definition.id() + ".name",
                    definition.displayNameTemplate()
            );
        }
    }

    @Test
    void carbonInventoryTitleRoundTripRetainsComponentIdentity() {
        for (String titleTemplate : List.of(
                BloodstoneServerConstants.MAIN_MENU_TITLE,
                BloodstoneServerConstants.GEAR_MENU_TITLE,
                BloodstoneServerConstants.ARMOR_MENU_TITLE,
                BloodstoneServerConstants.EFFECT_AXES_MENU_TITLE,
                BloodstoneServerConstants.POTIONS_MENU_TITLE,
                BloodstoneServerConstants.EXCHANGE_MENU_TITLE,
                BloodstoneServerConstants.TRASH_MENU_TITLE,
                BloodstoneServerConstants.STORAGE_MENU_TITLE,
                BloodstoneServerConstants.ENCHANTER_MENU_TITLE,
                BloodstoneServerConstants.DISENCHANTER_MENU_TITLE,
                BloodstoneServerConstants.AXE_FUSER_MENU_TITLE
        )) {
            var title = BloodstoneText.deserialize(titleTemplate);
            assertEquals(
                    title,
                    BloodstoneText.legacyComponent(BloodstoneText.legacy(title))
            );
        }
    }

    @Test
    void legacyActionBarPayloadEmbedsFormattingCodes() {
        TextComponent actionBarPayload =
                BloodstoneText.embedLegacyActionBarFormatting(
                        BloodstoneText.deserialize(
                                "<red><italic>-8 blood</italic></red>"
                        )
                );

        assertEquals("\u00A7c\u00A7o-8 blood", actionBarPayload.content());
        String actionBarJson = AdventureSerializer
                .serializer(ClientVersion.V_1_8)
                .asJson(actionBarPayload);
        assertEquals(
                actionBarPayload,
                AdventureSerializer.serializer(ClientVersion.V_1_8)
                        .fromJson(actionBarJson)
        );
    }

    private void assertTemplate(String name, String template) {
        assertFalse(
                template.indexOf('\u00A7') >= 0,
                () -> name + " contains a legacy section color code"
        );
        assertDoesNotThrow(
                () -> BloodstoneText.deserialize(template, TEST_PLACEHOLDERS),
                () -> name + " is not valid strict MiniMessage"
        );
    }
}
