package ac.grim.grimac.checks.impl.badpackets;

import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.teleport.RelativeFlag;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerPositionAndLook;

@CheckData(name = "BadPacketsD")
public class BadPacketsD extends Check implements PacketCheck {
    boolean d = false;
    Vector3f exemptVec;

    public BadPacketsD(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.PLAYER_POSITION_AND_LOOK && player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_19_3)) {
            WrapperPlayServerPlayerPositionAndLook teleport = new WrapperPlayServerPlayerPositionAndLook(event);
            if (teleport.isRelativeFlag(RelativeFlag.PITCH)) {
                if (teleport.getPitch() != 0)
                    d = true;
            } else {
                if (teleport.getPitch() > 90 || teleport.getPitch() < -90)
                    d = true;
            }
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        // there is a rotation desync with a teleport
        // client yaw0 pitch90  server yaw0 pitch90
        // client gets a teleport: yaw0 pitch91  server yaw0 pitch91
        // client changes rotation and runs a tick: yaw0 pitch 90  server yaw0 pitch91
        // maybe should do something
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_ROTATION || event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
            WrapperPlayClientPlayerFlying packet = new WrapperPlayClientPlayerFlying(event);
            if (packet.getLocation().getPitch() > 90 || packet.getLocation().getPitch() < -90) {
                if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_19_3) && player.packetStateData.lastPacketWasTeleport && d) {
                    exemptVec = new Vector3f(packet.getLocation().getYaw(), packet.getLocation().getPitch(), 0);
                }
                if (exemptVec == null || exemptVec.getX() != packet.getLocation().getYaw() || exemptVec.getY() != packet.getLocation().getPitch()) {
                    exemptVec = null;
                    flagAndAlert();
                    if (player.packetStateData.lastPacketWasTeleport || player.packetStateData.lastPacketWasOnePointSeventeenDuplicate) {
                        packet.getLocation().setPitch(packet.getLocation().getPitch() > 90 ? 90 : -90);
                        if (player.packetStateData.lastPacketWasOnePointSeventeenDuplicate) {
                            // :(
                            player.yRot = packet.getLocation().getPitch();
                        }
                    } else {
                        player.getSetbackTeleportUtil().executeViolationSetback();
                        event.setCancelled(true);
                        player.onPacketCancel();
                    }
                }
            } else {
                exemptVec = null;
            }
        }
    }
}
