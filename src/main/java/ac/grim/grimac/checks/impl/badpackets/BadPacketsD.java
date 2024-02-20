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
    public static final boolean hackfix = false;
    // 1.19.3+: https://bugs.mojang.com/browse/MC-259376
    // 1.8.8-: https://bugs.mojang.com/browse/MC-45104
    // mojang fixed this and removed the fix after 8 years?
    private final boolean wtf = player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_19_3) || player.getClientVersion().isOlderThan(ClientVersion.V_1_9);
    private final boolean mod360 = player.getClientVersion().isOlderThan(ClientVersion.V_1_19_3);
    private boolean d = false;
    private Vector3f exemptVec;

    public BadPacketsD(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.PLAYER_POSITION_AND_LOOK && wtf) {
            WrapperPlayServerPlayerPositionAndLook teleport = new WrapperPlayServerPlayerPositionAndLook(event);
            if (teleport.isRelativeFlag(RelativeFlag.PITCH)) {
                if (teleport.getPitch() != 0) {
                    if (hackfix)
                        teleport.setPitch(0f);
                    else
                        d = true;
                }
            } else {
                if (teleport.getPitch() > 90 || teleport.getPitch() < -90) {
                    if (hackfix)
                        teleport.setPitch(teleport.getPitch() > 90 ? 90f : -90f);
                    else
                        d = true;
                }
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
            boolean flag = false;
            if (packet.getLocation().getPitch() > 90 || packet.getLocation().getPitch() < -90) {
                if (wtf && player.packetStateData.lastPacketWasTeleport && d)
                    exemptVec = new Vector3f(packet.getLocation().getYaw(), packet.getLocation().getPitch(), 0);

                if (exemptVec == null || exemptVec.getX() != packet.getLocation().getYaw() || exemptVec.getY() != packet.getLocation().getPitch()) {
                    exemptVec = null;
                    flag = true;
                }
            } else {
                exemptVec = null;
            }

            if (mod360 && player.packetStateData.lastPacketWasTeleport) {
                if (packet.getLocation().getYaw() >= 360 || packet.getLocation().getYaw() <= -360 || packet.getLocation().getPitch() >= 360 || packet.getLocation().getPitch() <= -360) {
                    flag = true;
                }
            }

            if (flag) {
                flagAndAlert();
                if (player.packetStateData.lastPacketWasTeleport || player.packetStateData.lastPacketWasOnePointSeventeenDuplicate) {
                    player.getSetbackTeleportUtil().executeNonSimulatingSetback();
                } else {
                    player.getSetbackTeleportUtil().executeViolationSetback();
                }
                event.setCancelled(true);
                player.onPacketCancel();
            }

        }
    }
}
