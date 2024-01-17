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
    outTick, inTick;
}

@CheckData(name = "BadPacketsR")
public class BadPacketsR extends Check implements PacketCheck {
    public BadPacketsR(GrimPlayer player) {
        super(player);
    }

    private long lastTransReceivedTime = -1; // useless?
    private long lastTransSentTime = -1;
    private int skippedTicks = 0;
    private Status clientStatus = Status.outTick;

    // let's hope clients won't gc in process packets
    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (player.compensatedEntities.getSelf().isDead || player.gamemode == GameMode.SPECTATOR) {
            lastTransReceivedTime = -1;
            lastTransSentTime = -1;
            skippedTicks = 0;
            return;
        }
        if (isTransaction(event.getPacketType()) && player.packetStateData.lastTransactionPacketWasValid) {
            if (lastTransReceivedTime == -1 || lastTransSentTime == -1) {
                lastTransReceivedTime = player.getPlayerClockAtLeast();
                lastTransSentTime = System.nanoTime();
                skippedTicks = 0;
            }
            // player didn't send any flying but send a valid transaction after last check
            // and this transaction is after last transaction that we save 50ms
            // the player must after some gameloops and skipped all ticks that ran in these gameloops
            else if (player.getPlayerClockAtLeast() - lastTransSentTime >= 55e6) {
                lastTransReceivedTime = player.getPlayerClockAtLeast();
                lastTransSentTime = System.nanoTime();
                skippedTicks++;
            }
            // client ran some ticks and send a transaction
            else if (clientStatus != Status.outTick) {
                lastTransReceivedTime = player.getPlayerClockAtLeast();
                lastTransSentTime = System.nanoTime();
                skippedTicks++;
            }
            if (skippedTicks > 20) {
                flagAndAlert(""+skippedTicks);
                player.compensatedWorld.removeInvalidPistonLikeStuff(0);
            }
            clientStatus = Status.outTick;
        } else if (player.checkManager.getPostPredictionCheck(PostCheck.class).shouldCountPacketForPost(event.getPacketType())) {
            clientStatus = Status.inTick;
        } else if ((event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION ||
                event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION) && !player.compensatedEntities.getSelf().inVehicle()) {
            lastTransReceivedTime = -1;
            lastTransSentTime = -1;
            skippedTicks = 0;
            clientStatus = Status.outTick;
        } else if (event.getPacketType() == PacketType.Play.Client.STEER_VEHICLE && player.compensatedEntities.getSelf().inVehicle()) {
            lastTransReceivedTime = -1;
            lastTransSentTime = -1;
            skippedTicks = 0;
            clientStatus = Status.outTick;
        }
    }

}
