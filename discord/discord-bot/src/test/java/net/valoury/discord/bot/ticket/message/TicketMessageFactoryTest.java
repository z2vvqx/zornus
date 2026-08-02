package net.valoury.discord.bot.ticket.message;

import net.dv8tion.jda.api.components.Component;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.valoury.discord.bot.DiscordBotConstants;
import net.valoury.discord.bot.message.DiscordMessageFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TicketMessageFactoryTest {
    private final TicketMessageFactory messageFactory = new TicketMessageFactory(new DiscordMessageFactory());

    @Test
    void panelUsesNormalMessageContentWithButton() {
        String expectedPanelText = """
                ## !مسائل [فالوري](<https://discord.gg/invite/xkzC7meRdG>) ― نحن هنا دائماً لمساعدتك 🔍
                يجب عليك الإلتزام [بالقواعد العامة](<https://discord.com/channels/623179535008923684/1279208422595629120>) قبل إتباع هذه الإرشادات لكي تتمكن من إستخدام نظام المسائل.

                قدم لمحة عامة مختصرة عن مشكلتك لمساعدتنا في تقديم المساعدة بشكل أكثر فعالية. **:قدم وصفاً موجزاً**  **•**  📖_   _
                شارك جميع المعلومات والتفاصيل ذات الصلة بمشكلتك حتى نتمكن من حلها دون الحاجة إلى طلب معلومات إضافية منك. **:تضمين جميع التفاصيل اللازمة**  **•**  ✍️_   _

                **نحن نسعى للرد في غضون ساعة، ولكن يرجى السماح بما يصل إلى ٢٤ ساعة للرد.**
                """.strip();

        try (MessageCreateData panel = messageFactory.ticketPanel(123)) {
            ActionRow buttonRow = (ActionRow) panel.getComponents().getFirst();

            assertFalse(panel.isUsingComponentsV2());
            assertEquals(expectedPanelText, DiscordBotConstants.TICKET_PANEL_TEXT);
            assertEquals(expectedPanelText, panel.getContent());
            assertEquals(1, panel.getComponents().size());
            assertEquals(Component.Type.ACTION_ROW, buttonRow.getType());
            assertEquals("ticket:open:123", buttonRow.getButtons().getFirst().getCustomId());
            assertEquals("\u200B", buttonRow.getButtons().getFirst().getLabel());
        }
    }

    @Test
    void openingMessageUsesDynamicOpenerFieldsInsideContainer() {
        Container container = messageFactory.ticketOpening(123, 456);

        assertEquals(1, container.getComponents().size());
        assertEquals("""
                        ## 🔑 A ticket has gotten open
                        <@&123>

                        **OPENER:** <@456>  **REASON:** UNSPECIFIED  **ID:** `456`

                        Thank you for your patience. A staff member will be with you shortly to assist. In the meantime, please provide as much detail as possible about your issue to help us understand and address your concern more efficiently.
                        """.strip(),
                container.getComponents().getFirst().asTextDisplay().getContent());
    }
}
