package ac.grim.grimac.checks.impl.movement;

import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PostPredictionCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.PredictionComplete;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

@CheckData(name = "NegativeTimer", configName = "NegativeTimer", setback = 10)
public class NegativeTimerCheck extends TimerCheck implements PostPredictionCheck {
    int maxPingTransaction = 5;

    public NegativeTimerCheck(GrimPlayer player) {
        super(player);
        timerBalanceRealTime = System.nanoTime() + clockDrift;
    }

    @Override
    public void onPacketReceive(final PacketReceiveEvent event) {
        // 1.17 duplicate packet is in tick
        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType()) && !player.packetStateData.lastPacketWasTeleport) {
            if ((System.nanoTime() - player.getPlayerClockAtLeast()) > maxPingTransaction * 1e9) {
                player.timedOut();
            }
        }
    }

    @Override
    public void reload() {
        super.reload();
        clockDrift = (long) (getConfig().getDoubleElse(getConfigName() + ".drift", 1200.0) * 1e6);
    }
}
