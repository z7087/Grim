package ac.grim.grimac.checks.impl.badpackets;

import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.checks.type.PostPredictionCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.PredictionComplete;
import ac.grim.grimac.utils.lists.EvictingQueue;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.*;


// damn i mess up this
// client usually send <=1 cpacket helditemchange every tick
// if client got spacket helditemchange between last tick and this tick or didnt send cpacket helditemchange(not sent because of player changed its helditem itsself, not because of a spacket helditemchange) last tick, client can send <=2 cpacket helditemchange packet in this tick

@CheckData(name = "BadPacketsU")
public class BadPacketsU extends Check implements PacketCheck, PostPredictionCheck {
    private final ArrayDeque<PacketTypeCommon> post = new ArrayDeque<>();
    // Due to 1.9+ missing the idle packet, we must queue flags
    // 1.8 clients will have the same logic for simplicity, although it's not needed
    private final List<String> flags = new EvictingQueue<>(20);
    private boolean sentFlying = false;
    private int isExemptFromSwingingCheck = Integer.MIN_VALUE;

    public int baseChanged = 0;
    public int changed = 0;
    public int slotNeedChange = -1;
    public boolean unsure = false;
    public boolean taken = true;

    public BadPacketsU(GrimPlayer playerData) {
        super(playerData);
    }

    @Override
    public void onPredictionComplete(final PredictionComplete predictionComplete) {
        if (!flags.isEmpty()) {
            // Okay, the user might be cheating, let's double check
            // 1.8 clients have the idle packet, and this shouldn't false on 1.8 clients
            // 1.9+ clients have predictions, which will determine if hidden tick skipping occurred
            if (player.isTickingReliablyFor(3)) {
                for (String flag : flags) {
                    flagAndAlert(flag);
                }
            }

            flags.clear();
        }
    }

    @Override
    public void onPacketSend(final PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.HELD_ITEM_CHANGE) {
            WrapperPlayServerHeldItemChange held = new WrapperPlayServerHeldItemChange(event);
            int slot = held.getSlot();
            if (slot >= 0 && slot <= 8) {
                player.sendTransaction();
                player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
                    slotNeedChange = slot;
                    unsure = true;
                    taken = false;
                });
                player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get() + 1, () -> {
                    unsure = false;
                });
            }
        }
    }

    @Override
    public void onPacketReceive(final PacketReceiveEvent event) {
        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            // Don't count teleports or duplicates as movements
            if (player.packetStateData.lastPacketWasTeleport || player.packetStateData.lastPacketWasOnePointSeventeenDuplicate) {
                return;
            }
            WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);
            if (flying.hasPosition())
                baseChanged = 0;
            changed = 0;
        } else {
            if (isTransaction(event.getPacketType())) {
                // latencyUtils runs after us so
                if (changed > ((!taken || unsure) ? 1 : 2)) {
                    flags.add("changed=" + changed);
                }
                changed = 0;
            } else if (event.getPacketType() == PacketType.Play.Client.HELD_ITEM_CHANGE) {
                baseChanged++;
                WrapperPlayServerHeldItemChange held = new WrapperPlayServerHeldItemChange(event);
                if (taken && held.getSlot() == ) {
                } else {
                    changed++;
                }
                if (baseChanged > 40) {
                    flagAndAlert("impossible changed=" + changed);
                }
            }
        }
    }
}
