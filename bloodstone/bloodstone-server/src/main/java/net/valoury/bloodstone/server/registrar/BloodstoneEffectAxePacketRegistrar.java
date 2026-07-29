package net.valoury.bloodstone.server.registrar;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import net.valoury.bloodstone.server.service.BloodstoneCombatService;
import net.valoury.bloodstone.server.service.BloodstoneMainThreadExecutor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BloodstoneEffectAxePacketRegistrar extends SimplePacketListenerAbstract {

    private final BloodstoneCombatService combatService;
    private final BloodstoneMainThreadExecutor mainThreadExecutor;
    private final Map<UUID, Integer> heldSlots = new HashMap<>();

    public BloodstoneEffectAxePacketRegistrar(
            BloodstoneCombatService combatService,
            BloodstoneMainThreadExecutor mainThreadExecutor
    ) {
        super(PacketListenerPriority.HIGH);
        this.combatService = combatService;
        this.mainThreadExecutor = mainThreadExecutor;
    }

    public void register() {
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    public void unregister() {
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
        heldSlots.clear();
    }

    public void handleDisconnect(UUID playerId) {
        heldSlots.remove(playerId);
    }

    @Override
    public void onPacketPlayReceive(PacketPlayReceiveEvent event) {
        UUID playerId = event.getUser().getUUID();
        if (event.getPacketType() == PacketType.Play.Client.HELD_ITEM_CHANGE) {
            int heldSlot = new WrapperPlayClientHeldItemChange(event).getSlot();
            mainThreadExecutor.execute(() -> trackHeldSlot(playerId, heldSlot));
            return;
        }
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) {
            return;
        }
        WrapperPlayClientInteractEntity interaction =
                new WrapperPlayClientInteractEntity(event);
        if (interaction.getAction()
                != WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
            return;
        }
        int victimEntityId = interaction.getEntityId();
        mainThreadExecutor.execute(() -> handleAttack(playerId, victimEntityId));
    }

    private void trackHeldSlot(UUID playerId, int heldSlot) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline() && heldSlot >= 0 && heldSlot <= 8) {
            heldSlots.put(playerId, heldSlot);
        }
    }

    private void handleAttack(UUID attackerId, int victimEntityId) {
        Player attacker = Bukkit.getPlayer(attackerId);
        if (attacker == null || !attacker.isOnline()) {
            return;
        }
        Entity target = SpigotReflectionUtil.getEntityById(
                attacker.getWorld(),
                victimEntityId
        );
        if (!(target instanceof Player victim) || victim.equals(attacker)) {
            return;
        }
        int heldSlot = heldSlots.getOrDefault(
                attackerId,
                attacker.getInventory().getHeldItemSlot()
        );
        combatService.handleEffectAxeAttack(attacker, victim, heldSlot);
    }
}
