package ac.grim.grimac.checks.impl.badpackets;

import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.events.packets.patch.TeleportStupidityHandler;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

@CheckData(name = "BadPacketsU")
public class BadPacketsU extends Check implements PacketCheck {

    Vector3f lastNonStupidityLook = null;
    Vector3f lastStupidityLook = null;

    public BadPacketsU(final GrimPlayer player) {
        super(player);
    }

    // if player sent a stupidity and the stupidity's rotation is different from last location, the next flying's rotation must same to the stupidity's rotation
    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        // Stupidity only exists on 1.17 client
        if (player.getClientVersion().isOlderThan(ClientVersion.V_1_17))
            return;

        // We can only check 1.9+ server cause we can't know older server's correct flying type
        if (!TeleportStupidityHandler.isSupportVersion(player.getClientVersion(), PacketEvents.getAPI().getServerManager().getVersion()))
            return;


        if (player.compensatedEntities.getSelf().inVehicle() || player.compensatedEntities.getSelf().isDead) {
            lastNonStupidityLook = null;
            lastStupidityLook = null;
            return;
        }

        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);

            if (lastNonStupidityLook != null) {
                if (lastStupidityLook != null) {
                    if (player.packetStateData.lastPacketWasTeleport || !flying.hasRotationChanged() || flying.getLocation().getYaw() != lastStupidityLook.getX() || flying.getLocation().getPitch() != lastStupidityLook.getY()) {
                        flagAndAlert("type=impossible_stupidity");
                    }
                    if (player.packetStateData.lastPacketWasOnePointSeventeenDuplicate && flying.getLocation().getYaw() == lastStupidityLook.getX() && flying.getLocation().getPitch() == lastStupidityLook.getY()) {
                        // player sends a duplicate stupidity, deny it?
                        if (shouldModifyPackets()) {
                            //event.setCancelled(true);
                        }
                    }
                    lastStupidityLook = null;
                }

                if (player.packetStateData.lastPacketWasOnePointSeventeenDuplicate) {
                    if (flying.getLocation().getYaw() != lastNonStupidityLook.getX() || flying.getLocation().getPitch() != lastNonStupidityLook.getY()) {
                        lastStupidityLook = new Vector3f(flying.getLocation().getYaw(), flying.getLocation().getPitch(), 0f);
                    }
                }
            }
            if (!player.packetStateData.lastPacketWasOnePointSeventeenDuplicate)
                lastNonStupidityLook = new Vector3f(player.xRot, player.yRot, 0f);
        }

    }

}
