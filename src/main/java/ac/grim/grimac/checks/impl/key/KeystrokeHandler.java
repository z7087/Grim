package ac.grim.grimac.checks.impl.key;

import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.checks.type.PostPredictionCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.PredictionComplete;
import ac.grim.grimac.utils.lists.EvictingQueue;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;

import static com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.*;


@CheckData(name = "KeystrokeHandler")
public class KeystrokeHandler extends Check implements PacketCheck {
    public KeystrokeEvents playerOn = KeystrokeEvents.OUTSIDE_TICK;

    public KeystrokeHandler(GrimPlayer playerData) {
        super(playerData);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        PacketTypeCommon packetType = event.getPacketType();

        if (KeystrokeEvents.isExempt(player, packetType, event))
            return;

        if (playerOn.isRepeatable() && playerOn.isType(player, packetType, event))
            return;

        checkType(packetType, event);
    }

    protected int checkType(PacketTypeCommon packetType, PacketReceiveEvent event) {
        KeystrokeEvents playerNext = playerOn.getNext(player, packetType, event);
        int i = 0;
        do {
            if (playerNext.isType(player, packetType, event))
                break;
            playerNext = playerNext.getNext(player, packetType, event);
        } while (++i < 300);
        playerOn = playerNext;
        return i;
    }
}
