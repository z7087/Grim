package ac.grim.grimac.checks.impl.badpackets;

import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

@CheckData(name = "BadPacketsH")
public class BadPacketsH extends Check implements PacketCheck {
    private boolean sentAnimation = player.getClientVersion().isNewerThan(ClientVersion.V_1_8);

    public BadPacketsH(final GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        PacketType type = event.getPacketType();
        if (type == PacketType.Play.Client.ANIMATION) {
            sentAnimation = true;
        } else if (type == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
            if (packet.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                if (!sentAnimation && flagAndAlert()) {
                    event.setCancelled(true);
                }
                sentAnimation = false;
                return;
            }
        }

        if ((WrapperPlayClientPlayerFlying.isFlying(type)
            || type == PacketType.Play.Client.INTERACT_ENTITY
            || type == PacketType.Play.Client.PLAYER_DIGGING
            || type == PacketType.Play.Client.USE_ITEM
            || type == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT
            || type == PacketType.Play.Client.PONG
            || type == PacketType.Play.Client.WINDOW_CONFIRMATION) {

            if (!sentAnimation && player.getClientVersion().isNewerThan(ClientVersion.V_1_8)) {
                flagAndAlert();
                sentAnimation = true;
            }
        }
    }
}
