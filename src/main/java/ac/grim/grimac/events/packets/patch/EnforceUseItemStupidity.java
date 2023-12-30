package ac.grim.grimac.events.packets.patch;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.checks.impl.badpackets.BadPacketsU;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

// This runs before anything else, so this way we can simulate receiving flying.
public class EnforceUseItemStupidity extends PacketListenerAbstract {

    public EnforceUseItemStupidity() {
        super(PacketListenerPriority.LOWEST);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        GrimPlayer player = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
        if (player == null) return;

        // Stupidity packet only exists on 1.17+
        if (player.getClientVersion().isOlderThan(ClientVersion.V_1_17)) return;

        if (!player.packetStateData.detectedStupidity && event.getPacketType() == PacketType.Play.Client.USE_ITEM) {
            // The player MUST send a stupidity packet before use item
            player.checkManager.getPacketCheck(BadPacketsU.class).flagAndAlert("type=skipped_use");
            return;
        }

        // If we received a believed stupidity packet, the next packet MUST be USE_ITEM.
        // If not, we were wrong or the client is attempting to fake stupidity.
        if (player.packetStateData.detectedStupidity) {
            if (event.getPacketType() == PacketType.Play.Client.USE_ITEM) {
                // Valid stupidity packet.
                player.packetStateData.detectedStupidity = false;

                // If the last movement was definitely a duplicate packet, and this one is too
                if (player.packetStateData.lastMovementWasDefinitelyOnePointSeventeenDuplicate && player.packetStateData.lastLastStupidity != null && player.packetStateData.lastStupidity != null) {
                    // The player cannot send two duplicate packets with a different look, rotation changes are always sent with a normal flying packet
                    if (player.xRot != player.packetStateData.lastLastStupidity.getYaw() || player.yRot != player.packetStateData.lastLastStupidity.getPitch()) {
                        player.checkManager.getPacketCheck(BadPacketsU.class).flagAndAlert("type=no_rotation");
                    }
                }

                player.packetStateData.lastLastStupidity = player.packetStateData.lastStupidity;
                player.packetStateData.lastStupidity = null;
                player.packetStateData.lastMovementWasDefinitelyOnePointSeventeenDuplicate = true;
                return;
            }

            // We were wrong about it being stupidity, or the client is attempting to fake stupidity, reprocess this packet as non-stupidity
            player.packetStateData.ignoreDuplicatePacket = true;
            player.packetStateData.detectedStupidity = false;
            PacketEvents.getAPI().getPlayerManager().receivePacket(player.bukkitPlayer, new WrapperPlayClientPlayerFlying(true, true, player.packetStateData.packetPlayerOnGround, player.packetStateData.lastStupidity));
        }
    }
}
