package net.valoury.bloodstone.server.model;

import net.kyori.adventure.text.Component;
import net.valoury.bloodstone.server.BloodstoneText;

public enum BloodstoneItemClassification {
    INCLUSIVE("classification.inclusive", "<gray>Inclusive</gray>"),
    EXCLUSIVE("classification.exclusive", "<gray>Exclusive</gray>"),
    SOULBOUND("classification.soulbound", "<gray>Soulbound</gray>");

    private final String internalId;
    private final Component lore;

    BloodstoneItemClassification(
            String internalId,
            String loreTemplate
    ) {
        this.internalId = internalId;
        this.lore = BloodstoneText.deserialize(loreTemplate);
    }

    public String internalId() {
        return internalId;
    }

    public Component lore() {
        return lore;
    }

    public boolean isRemovedByNormalEnchanting() {
        return this == INCLUSIVE;
    }

    public boolean isRestrictedFromModification() {
        return this == SOULBOUND;
    }
}
