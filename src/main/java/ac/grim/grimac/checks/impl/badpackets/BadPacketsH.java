package ac.grim.grimac.checks.impl.badpackets;

import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

@CheckData(name = "BadPacketsH")
public class BadPacketsH extends Check implements PacketCheck {
    private boolean sentAttack = player.getClientVersion().isOlderThanOrEquals(ClientVersion.V_1_8);

    public BadPacketsH(final GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        PacketTypeCommon type = event.getPacketType();
        if (type == PacketType.Play.Client.ANIMATION) {
            sentAttack = false;
        } else if (type == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
            if (packet.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                if (sentAttack && flagAndAlert()) {
                    event.setCancelled(true);
                }
                sentAttack = true;
                return;
            }
        }

        if (WrapperPlayClientPlayerFlying.isFlying(type)
            || type == PacketType.Play.Client.INTERACT_ENTITY
            || type == PacketType.Play.Client.PLAYER_DIGGING
            || type == PacketType.Play.Client.USE_ITEM
            || type == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT
            || type == PacketType.Play.Client.PONG
            || type == PacketType.Play.Client.WINDOW_CONFIRMATION) {

            if (sentAttack && player.getClientVersion().isNewerThan(ClientVersion.V_1_8)) {
                flagAndAlert();
            }
            sentAttack = player.getClientVersion().isOlderThanOrEquals(ClientVersion.V_1_8);
        }
    }
}
