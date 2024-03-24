package ac.grim.grimac.checks.impl.misc;

import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.data.Pair;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientKeepAlive;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerKeepAlive;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

// this check can and only can check keepalive pingspoof
// transaction is sync to client thread, and client thread can freeze by many ways
@CheckData(name = "PingSpoof")
public class PingSpoof extends Check implements PacketCheck {
    Queue<Pair<Long, Long>> keepaliveMap = new ConcurrentLinkedQueue<>();
    long keepAliveClock = -1;

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

                // wtf this for?
                if (System.currentTimeMillis() - player.joinTime <= 5000)
                    return;

                // timeout player after 60s
                if (System.nanoTime() - keepAliveClock >= 60e9)
                    player.timedOut();
                // if we sent keepalive packet A and transaction packet B, and the player replys B before A, then we can know the player is spoofing keepalive ping
                else if (player.getPlayerClockAtLeast() > keepAliveClock)
                    flagAndAlert("diff: " + (player.getPlayerClockAtLeast() - keepAliveClock));
                // do we flag twice?
                else if (skipped > 0)
                    flagAndAlert("skipped: " + skipped);
            }
        }
        if (event.getPacketType() == PacketType.Play.Client.WINDOW_CONFIRMATION && player.packetStateData.lastTransactionPacketWasValid) {
            if (keepAliveClock != -1 && System.nanoTime() - keepAliveClock >= 60e9) {
                player.timedOut();
                return;
            }
            Pair<Long, Long> firstData = keepaliveMap.peek();
            // if we sent keepalive packet A and transaction packet B, and the player replys B and didnt reply A, then we can know the player is spoofing keepalive ping
            if (firstData != null && player.getPlayerClockAtLeast() > firstData.getSecond())
                flagAndAlert("diff: " + (player.getPlayerClockAtLeast() - firstData.getSecond()));
        }
    }
}
