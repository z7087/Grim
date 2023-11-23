package ac.grim.grimac.checks.impl.badpackets;

import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.impl.post.PostCheck;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;

enum Status {
    outTick, beforeTick, afterTick;
}

@CheckData(name = "BadPacketsR", decay = 0.1)
public class BadPacketsR extends Check implements PacketCheck {
    public BadPacketsR(final GrimPlayer player) {
        super(player);
    }

    private boolean hadClientRunTick = false;
    private long lastTransTime = -1;
    private Status clientStatus = Status.outTick;
    private int oldTransId = 0;

    @Override
    public void onPacketReceive(final PacketReceiveEvent event) {
        if (isTransaction(event.getPacketType()) && player.packetStateData.lastTransactionPacketWasValid) {
            // client ran some ticks and send a transaction
            if (clientStatus != Status.outTick) {
                lastTransTime = System.currentTimeMillis();
                oldTransId = player.lastTransactionSent.get();
                shouldCheckNextTick = true;
                hadClientRunTick = false;
            }
            clientStatus = Status.outTick;
        } else if (player.checkManager.getPostPredictionCheck(PostCheck.class).shouldCountPacketForPost(event.getPacketType())) {
            if (shouldCheckNextTick) {
                long diff = (System.currentTimeMillis() - lastTransTime);
                if (diff > 1000) {
                    //TODO: figure out why spectators are flagging this
                    if (lastTransTime != -1 && hadClientRunTick && player.gamemode != GameMode.SPECTATOR) {
                        flagAndAlert("lst=" + diff + "ms");
                    } else {
                        reward();
                    }
                    player.compensatedWorld.removeInvalidPistonLikeStuff(oldTransId);
                }
            }
            hadClientRunTick = true;
            clientStatus = Status.beforeTick;
        } else if ((event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION ||
                event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION) && !player.compensatedEntities.getSelf().inVehicle()) {
            hadClientRunTick = true;
            clientStatus = Status.afterTick;
        } else if (event.getPacketType() == PacketType.Play.Client.STEER_VEHICLE && player.compensatedEntities.getSelf().inVehicle()) {
            hadClientRunTick = true;
            clientStatus = Status.afterTick;
        }
    }

}
