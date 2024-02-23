package ac.grim.grimac.checks.impl.badpackets;

import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.checks.type.PostPredictionCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.PredictionComplete;
import ac.grim.grimac.utils.lists.EvictingQueue;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerHeldItemChange;

import java.util.List;

// damn i mess up this
// client usually send <=1 cpacket helditemchange every tick
// if client got spacket helditemchange between last tick and this tick or didnt send cpacket helditemchange(not sent because of player changed its helditem itsself, not because of a spacket helditemchange) last tick, client can send <=2 cpacket helditemchange packet in this tick

@CheckData(name = "BadPacketsU")
public class BadPacketsU extends Check implements PacketCheck, PostPredictionCheck {
    private final List<String> flags = new EvictingQueue<>(20);

    public int baseChanged = 0;
    public int changed = 0;
    public int slotAbleChange = -1;
    public int slotNeedChange = -1;
    public boolean exemptNext = true;

    public BadPacketsU(GrimPlayer playerData) {
        super(playerData);
    }

    @Override
    public void onPredictionComplete(final PredictionComplete predictionComplete) {
        if (slotNeedChange != -1) {
            if (slotAbleChange == -1 || slotAbleChange == slotNeedChange)
                flagAndAlert("ignored server held_item_change 1");
            slotNeedChange = -1;
        }
        if (player.isTickingReliablyFor(3)) {
            if (!flags.isEmpty()) {
                for (String flag : flags) {
                    flagAndAlert(flag);
                }
            }
            if (changed == 0) {
                exemptNext = true;
            }
        } else {
            if (changed <= 19) {
                exemptNext = true;
            }
        }
        flags.clear();
        baseChanged = 0;
        changed = 0;
    }

    @Override
    public void onPacketSend(final PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.HELD_ITEM_CHANGE) {
            WrapperPlayServerHeldItemChange held = new WrapperPlayServerHeldItemChange(event);
            int slot = held.getSlot();
            if (slot >= 0 && slot <= 8) {
                player.sendTransaction();
                player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
                    slotAbleChange = slot;
                });
                player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get() + 1, () -> {
                    if (slotAbleChange != -1) {
                        if (player.packetStateData.lastSlotSelected == slot)
                            slotNeedChange = -1;
                        else
                            slotNeedChange = slot;
                    }
                    //slotAbleChange = slot;
                    slotAbleChange = -1;
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
            if (event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION || event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
                flags.clear();
                baseChanged = 0;
            }
            changed = 0;
        } else {
            if (event.getPacketType() == PacketType.Play.Client.HELD_ITEM_CHANGE) {
                baseChanged++;
                if (baseChanged > 40) {
                    flagAndAlert("impossible baseChanged=" + baseChanged);
                }

                WrapperPlayClientHeldItemChange held = new WrapperPlayClientHeldItemChange(event);
                if (held.getSlot() < 0 || held.getSlot() > 8) {
                    flagAndAlert("invalid held slot " + held.getSlot());
                    return;
                }
                if (exemptNext || held.getSlot() == slotAbleChange || held.getSlot() == slotNeedChange) {
                    exemptNext = false;
                    if (held.getSlot() == slotAbleChange) {
                        slotAbleChange = -1;
                        slotNeedChange = -1;
                    }
                    if (held.getSlot() == slotNeedChange) {
                        slotNeedChange = -1;
                    }
                } else {
                    changed++;
                }
                if (slotNeedChange != -1) {
                    if (slotAbleChange == -1 || slotAbleChange == slotNeedChange)
                        flagAndAlert("ignored server held_item_change 2");
                    slotNeedChange = -1;
                }
                if (changed > 20) {
                    flagAndAlert("impossible changed=" + changed);
                } else if (changed > 1) {
                    // let's believe client has idle packet
                    // if not, exempt at elsewhere
                    flags.add("changed=" + changed);
                }
            }
        }
    }
}
