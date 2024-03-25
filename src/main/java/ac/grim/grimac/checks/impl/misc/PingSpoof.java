package ac.grim.grimac.checks.impl.misc;

import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.impl.badpackets.BadPacketsO;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.data.Pair;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientKeepAlive;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerKeepAlive;

import java.util.LinkedList;
import java.util.Queue;

// this check can and only can check keepalive pingspoof
// transaction is sync to client thread, and client thread can freeze by many ways
// not stable enough with other anticheats that send transaction
// lets hope bundle wont break this :)
@CheckData(name = "PingSpoof")
public class PingSpoof extends Check implements PacketCheck {
    // keepAlive is async to client thread, so i think lower is safe (or not if client gc for 30s)
    private static final long TIMED_OUT_IF_PASSED = 30 * (1000 * 1000);
    Queue<Pair<Long, Long>> keepaliveMap = new LinkedList<>();
    long keepAliveClock = -1;

    // why playerClockAtLeast is System.nanoTime() by default?
    boolean state = false;

    public PingSpoof(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.KEEP_ALIVE) {
            WrapperPlayServerKeepAlive keepAlive = new WrapperPlayServerKeepAlive(event);
            keepaliveMap.add(new Pair<>(keepAlive.getId(), System.nanoTime()));
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.KEEP_ALIVE) {
            WrapperPlayClientKeepAlive packet = new WrapperPlayClientKeepAlive(event);

            long id = packet.getId();
            boolean hasID = false;
            int skipped = 0;

            for (Pair<Long, Long> iterator : keepaliveMap) {
                if (iterator.getFirst() == id) {
                    hasID = true;
                    keepAliveClock = iterator.getSecond();
                    break;
                }
                skipped++;
            }

            if (hasID) {
                Pair<Long, Long> data = null;
                do {
                    data = keepaliveMap.poll();
                } while (data != null && data.getFirst() != id);

                if (System.nanoTime() - keepAliveClock >= TIMED_OUT_IF_PASSED) {
                    player.timedOut();
                    return;
                }

                // idk what the jointime check for but transaction handler has this
                // if we sent keepalive packet A and transaction packet B, and the player replys B before A, then we can know the player is spoofing keepalive ping
                if (state && System.currentTimeMillis() - player.joinTime <= 5000 && player.getPlayerClockAtLeast() > keepAliveClock) {
                    if (flag())
                        alert(String.format("diff: %.5f", (double)(player.getPlayerClockAtLeast() - keepAliveClock) / 1.0e9));
                }
                // do we flag twice?
                else if (skipped > 0) {
                    flagAndAlert("skipped: " + skipped);
                }
            } else {
                player.checkManager.getPacketCheck(BadPacketsO.class).flagAndAlert("ID: " + id);
            }
        }
        if (player.packetStateData.lastTransactionPacketWasValid && isTransaction(event.getPacketType())) {
            state = true;
            if (keepAliveClock != -1 && System.nanoTime() - keepAliveClock >= TIMED_OUT_IF_PASSED) {
                player.timedOut();
                return;
            }
            Pair<Long, Long> firstData = keepaliveMap.peek();
            // if we sent keepalive packet A and transaction packet B, and the player replys B and didnt reply A, then we can know the player is spoofing keepalive ping
            if (firstData != null && player.getPlayerClockAtLeast() > firstData.getSecond()) {
                if (flag())
                    alert(String.format("diff: %.5f", (double)(player.getPlayerClockAtLeast() - firstData.getSecond()) / 1.0e9));
            }
        }
    }
}
