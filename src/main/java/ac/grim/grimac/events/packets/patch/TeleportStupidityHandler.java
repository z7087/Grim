package ac.grim.grimac.events.packets.patch;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.checks.impl.badpackets.BadPacketsU;
import ac.grim.grimac.events.packets.CheckManagerListener;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

// This runs before anything else, so this way we can simulate receiving flying.
public class TeleportStupidityHandler extends PacketListenerAbstract {

    public TeleportStupidityHandler() {
        super();
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        GrimPlayer player = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
        if (player == null) return;

        // Ignore resend
        if (player.packetStateData._disableLowestLogger) {
            return;
        }

        // If we cancelled a likely stupidity, this is the next packet, reset disablelogger
        if (player.packetStateData._disableListenerLogger)
            player.packetStateData._disableListenerLogger = false;

        // We can't know the flying packet's type in 1.8-client or 1.8- server
        // 1.8- server and 1.8- client have no confirm-teleport packet
        // ViaVersion cancels use-item packet sometimes cause shield-blocking, and 1.8- client has no stupidity packet
        if (!isSupportVersion(player.getClientVersion(), PacketEvents.getAPI().getServerManager().getVersion()))
            return;

        // Stupidity packet only exists on 1.17+
        if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_17)) {
            boolean isUseItemPacket = isUseItem(event);

            if (player.packetStateData.lastLikelyStupidity == null && isUseItemPacket) {
                // The player MUST send a stupidity packet before use item
                player.checkManager.getPacketCheck(BadPacketsU.class).flagAndAlert("type=skipped_use");
            }
            // If we received a believed stupidity packet, the next packet MUST be USE_ITEM.
            // If not, we were wrong or the client is attempting to fake stupidity.
            else if (player.packetStateData.lastLikelyStupidity != null) {
                if (isUseItemPacket) {
                    // The last packet is definitely a stupidity, resend it as stupidity
                    player.packetStateData.lastPacketWasDefinitelyOnePointSeventeenDuplicate = true;
                    Location lastStupidity = player.packetStateData.lastLikelyStupidity;
                    player.packetStateData.lastLikelyStupidity = null;

                    player.packetStateData._disableLowestLogger = true;
                    try {
                        PacketEvents.getAPI().getPlayerManager().receivePacket(player.bukkitPlayer, new WrapperPlayClientPlayerFlying(true, true, player.packetStateData.packetPlayerOnGround, lastStupidity));
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        player.packetStateData._disableLowestLogger = false;
                    }
                } else {
                    // We were wrong about it being stupidity, or the client is attempting to fake stupidity, reprocess this packet as non-stupidity
                    Location lastFlying = player.packetStateData.lastLikelyStupidity;
                    player.packetStateData.lastLikelyStupidity = null;

                    player.packetStateData._disableLowestLogger = true;
                    try {
                        PacketEvents.getAPI().getPlayerManager().receivePacket(player.bukkitPlayer, new WrapperPlayClientPlayerFlying(true, true, player.packetStateData.packetPlayerOnGround, lastFlying));
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        player.packetStateData._disableLowestLogger = false;
                    }
                }
            }

            if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
                WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);
                if (!player.packetStateData.confirmedTeleport && CheckManagerListener.isMojangStupid(player, flying)) {
                    // This packet is likely a stupidity, cancel and save it util we can get its type
                    event.setCancelled(true);
                    player.packetStateData._disableListenerLogger = true;
                    player.packetStateData.lastLikelyStupidity = flying.getLocation();
                    return;
                }
            }

        }

        if (player.packetStateData.confirmedTeleport) {
            boolean lastConfirmValid = false;
            if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
                WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);
                lastConfirmValid = flying.hasPositionChanged() && flying.hasRotationChanged();
            }

            if (lastConfirmValid) {
                player.packetStateData.lastPacketWasDefinitelyTeleport = true;
            } else {
                player.checkManager.getPacketCheck(BadPacketsU.class).flagAndAlert("type=invalid_confirm_teleport");
            }

            player.packetStateData.confirmedTeleport = false;
        }

        if (event.getPacketType() == PacketType.Play.Client.TELEPORT_CONFIRM) {
            player.packetStateData.confirmedTeleport = true;
        }
    }

    private static boolean isUseItem(PacketReceiveEvent event) {
        // Do we need to support 1.8 servers for this...
        // Via broke a lot, wait for grim runs in front of via
        // why grim runs after via

        if (event.getPacketType() == PacketType.Play.Client.USE_ITEM)
            return true;
        else if (PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_9) && event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            return (new WrapperPlayClientPlayerBlockPlacement(event)).getFace() == BlockFace.OTHER;
        }
        return false;
    }

    public static boolean isSupportVersion(ClientVersion clientVer, ServerVersion serverVer) {
        return clientVer.isNewerThanOrEquals(ClientVersion.V_1_9) && serverVer.isNewerThanOrEquals(ServerVersion.V_1_9);
    }

}
