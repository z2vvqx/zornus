package net.valoury.bloodstone.server.service;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.color.AlphaColor;
import com.github.retrooper.packetevents.protocol.particle.Particle;
import com.github.retrooper.packetevents.protocol.particle.data.ParticleColorData;
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChangeGameState;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle;
import net.valoury.bloodstone.server.BloodstoneServerConstants;
import net.valoury.bloodstone.server.BloodstoneText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

public final class BloodstonePresentationService {

    private static final int EFFECT_AXE_PARTICLE_COUNT = 40;
    private static final double EFFECT_AXE_PARTICLE_OFFSET_X = 1.4D;
    private static final double EFFECT_AXE_PARTICLE_OFFSET_Y = 0.0D;
    private static final double EFFECT_AXE_PARTICLE_OFFSET_Z = 1.4D;
    private static final int REVENGE_TARGET_PARTICLE_COUNT = 6;
    private static final double REVENGE_TARGET_PARTICLE_RADIUS = 0.3D;
    private static final int PARTICLE_VISIBILITY_RADIUS = 64;

    public void playBloodDropStep(Location location) {
        location.getWorld().spigot().playEffect(
                location.clone().add(0.0D, 1.0D, 0.0D),
                Effect.STEP_SOUND,
                Material.REDSTONE_BLOCK.getId(),
                0,
                0.55F,
                0.55F,
                0.55F,
                0.1F,
                18,
                PARTICLE_VISIBILITY_RADIUS
        );
    }

    public void playEffectAxeParticles(Player effectRecipient, Color color) {
        Location origin = effectRecipient.getLocation();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int particleIndex = 0;
             particleIndex < EFFECT_AXE_PARTICLE_COUNT;
             particleIndex++) {
            WrapperPlayServerParticle particlePacket =
                    createEffectAxeParticlePacket(
                            new Vector3d(
                                    origin.getX() + random.nextDouble(
                                            -EFFECT_AXE_PARTICLE_OFFSET_X,
                                            EFFECT_AXE_PARTICLE_OFFSET_X
                                    ),
                                    origin.getY()
                                            + EFFECT_AXE_PARTICLE_OFFSET_Y,
                                    origin.getZ() + random.nextDouble(
                                            -EFFECT_AXE_PARTICLE_OFFSET_Z,
                                            EFFECT_AXE_PARTICLE_OFFSET_Z
                                    )
                            ),
                            color
                    );
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                PacketEvents.getAPI().getPlayerManager()
                        .sendPacket(viewer, particlePacket);
            }
        }
    }

    private static WrapperPlayServerParticle createEffectAxeParticlePacket(
            Vector3d position,
            Color color
    ) {
        float red = Math.max(0.01F, color.getRed() / 255.0F);
        float green = color.getGreen() / 255.0F;
        float blue = color.getBlue() / 255.0F;
        ParticleColorData colorData = new ParticleColorData(new AlphaColor(
                color.getRed(),
                color.getGreen(),
                color.getBlue()
        ));
        return new WrapperPlayServerParticle(
                new Particle<>(ParticleTypes.ENTITY_EFFECT, colorData),
                true,
                position,
                new Vector3f(red, green, blue),
                1.0F,
                0,
                true
        );
    }

    public void playEffectAxeSound() {
        float pitch = randomPitch(0.9F, 1.1F);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.playSound(viewer.getLocation(), Sound.GLASS, 1.0F, pitch);
        }
    }

    public void playEffectAxeBreak(Player player) {
        Location breakLocation = player.getEyeLocation();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.playSound(
                    viewer.getLocation(),
                    Sound.ITEM_BREAK,
                    1.0F,
                    1.0F
            );
            viewer.spigot().playEffect(
                    breakLocation,
                    Effect.TILE_BREAK,
                    20,
                    0,
                    0.35F,
                    0.35F,
                    0.35F,
                    0.1F,
                    22,
                    Integer.MAX_VALUE
            );
        }
    }

    public void playHeal(Player player, boolean removedHarmfulEffect) {
        playColoredParticles(player.getLocation().clone().add(0.0, 1.0, 0.0),
                Color.fromRGB(40, 210, 80), 32, 0.75F);
        player.getWorld().spigot().playEffect(
                player.getLocation().clone().add(0.0, 1.0, 0.0),
                Effect.POTION_BREAK,
                0,
                0,
                0.55F,
                0.7F,
                0.55F,
                0.05F,
                20,
                PARTICLE_VISIBILITY_RADIUS
        );
        if (removedHarmfulEffect) {
            playColoredParticles(player.getLocation().clone().add(0.0, 1.0, 0.0),
                    Color.fromRGB(35, 35, 40), 20, 0.9F);
        }
        player.playSound(player.getLocation(), Sound.SUCCESSFUL_HIT, 0.5F, 1.2F);
    }

    public void playRampageAnnouncement(
            Player player,
            Component title,
            Component subtitle
    ) {
        player.playSound(player.getLocation(), Sound.LEVEL_UP, 1.0F, 1.45F);
        BloodstoneText.showTitle(player, title, subtitle);
    }

    public void playDominationRespawn(
            Player dominatedPlayer,
            Component dominatorName
    ) {
        playGuardianTitle(
                dominatedPlayer,
                BloodstoneText.deserialize(
                        BloodstoneServerConstants.DOMINATED_TITLE
                ),
                BloodstoneText.deserialize(
                        BloodstoneServerConstants.DOMINATED_SUBTITLE_FORMAT,
                        Placeholder.component("dominator", dominatorName)
                )
        );
    }

    public void playDomination(
            Player dominator,
            Component dominatedPlayerName
    ) {
        playGuardianTitle(
                dominator,
                BloodstoneText.deserialize(
                        BloodstoneServerConstants.DOMINATION_TITLE
                ),
                BloodstoneText.deserialize(
                        BloodstoneServerConstants.DOMINATION_SUBTITLE_FORMAT,
                        Placeholder.component("victim", dominatedPlayerName)
                )
        );
    }

    public void playDominationLost(
            Player formerDominator,
            Component revengePlayerName
    ) {
        playGuardianTitle(
                formerDominator,
                BloodstoneText.deserialize(
                        BloodstoneServerConstants.DOMINATION_LOST_TITLE
                ),
                BloodstoneText.deserialize(
                        BloodstoneServerConstants.DOMINATION_LOST_SUBTITLE_FORMAT,
                        Placeholder.component("player", revengePlayerName)
                )
        );
    }

    public void playRevenge(Player player, Component formerDominatorName) {
        playGuardianTitle(
                player,
                BloodstoneText.deserialize(
                        BloodstoneServerConstants.REVENGE_TITLE
                ),
                BloodstoneText.deserialize(
                        BloodstoneServerConstants.REVENGE_SUBTITLE_FORMAT,
                        Placeholder.component(
                                "dominator",
                                formerDominatorName
                        )
                )
        );
    }

    public void playRevengeTargetParticles(
            Player dominatedPlayer,
            Player dominator
    ) {
        Location origin = dominator.getLocation().clone()
                .add(0.0D, dominator.getEyeHeight() + 0.45D, 0.0D);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int particleIndex = 0;
             particleIndex < REVENGE_TARGET_PARTICLE_COUNT;
             particleIndex++) {
            Location particleLocation = origin.clone().add(
                    random.nextDouble(
                            -REVENGE_TARGET_PARTICLE_RADIUS,
                            REVENGE_TARGET_PARTICLE_RADIUS
                    ),
                    random.nextDouble(0.0D, REVENGE_TARGET_PARTICLE_RADIUS),
                    random.nextDouble(
                            -REVENGE_TARGET_PARTICLE_RADIUS,
                            REVENGE_TARGET_PARTICLE_RADIUS
                    )
            );
            dominatedPlayer.spigot().playEffect(
                    particleLocation,
                    Effect.COLOURED_DUST,
                    0,
                    0,
                    1.0F,
                    0.0F,
                    0.0F,
                    1.0F,
                    0,
                    Integer.MAX_VALUE
            );
        }
    }

    public void playMenuNavigation(Player player) {
        player.playSound(
                player.getLocation(),
                Sound.NOTE_STICKS,
                1.0F,
                randomPitch(0.9F, 1.1F)
        );
    }

    public void playSoulboundReturn(Player player) {
        player.playSound(player.getLocation(), Sound.ENDERMAN_TELEPORT, 0.8F, 1.8F);
        player.getWorld().spigot().playEffect(
                player.getLocation().clone().add(0.0, 1.0, 0.0),
                Effect.PORTAL,
                0,
                0,
                0.55F,
                0.8F,
                0.55F,
                0.2F,
                36,
                PARTICLE_VISIBILITY_RADIUS
        );
        BloodstoneText.sendActionBar(
                player,
                BloodstoneServerConstants.SOULBOUND_RETURN_ACTION_BAR
        );
    }

    public float randomPitch(float minimum, float maximum) {
        return (float) ThreadLocalRandom.current().nextDouble(minimum, maximum);
    }

    private void playGuardianTitle(
            Player player,
            Component title,
            Component subtitle
    ) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(
                player,
                new WrapperPlayServerChangeGameState(
                        WrapperPlayServerChangeGameState.Reason
                                .PLAY_ELDER_GUARDIAN_MOB_APPEARANCE,
                        0.0F
                )
        );
        BloodstoneText.showTitle(player, title, subtitle);
    }

    private void playColoredParticles(Location location, Color color, int count, float radius) {
        float red = Math.max(0.01F, color.getRed() / 255.0F);
        float green = color.getGreen() / 255.0F;
        float blue = color.getBlue() / 255.0F;
        location.getWorld().spigot().playEffect(
                location,
                Effect.COLOURED_DUST,
                0,
                0,
                red,
                green,
                blue,
                radius,
                count,
                PARTICLE_VISIBILITY_RADIUS
        );
    }
}
