package ac.grim.grimac.events.packets;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import java.util.Optional;

// 1 tick has only 1 cursor
public class PacketPlayerCursor extends PacketListenerAbstract {

    public PacketPlayerCursor() {
        super(PacketListenerPriority.LOW);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {

            GrimPlayer player = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (player == null) return;

            WrapperPlayClientPlayerDigging dig = new WrapperPlayClientPlayerDigging(event);

            if (dig.getAction() == DiggingAction.START_DIGGING || dig.getAction() == DiggingAction.FINISHED_DIGGING) {
                Vector3i targetBlock = dig.getBlockPosition();
                BlockFace face = dig.getBlockFace();
                if (player.hasCursor != 1 || !targetBlock.equals(player.cursorBlock) || face != player.cursorBlockFace) {
                    if (player.hasCursor != -1) {
                        CheckManagerListener.handleQueuedPlaces(player, false, false, 0, 0, System.currentTimeMillis());
                    }
                    player.hasCursor = 1;
                    player.cursorBlock = targetBlock;
                    player.cursorBlockFace = face;
                }
            }
        }

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {

            GrimPlayer player = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (player == null) return;

            WrapperPlayClientPlayerBlockPlacement place = new WrapperPlayClientPlayerBlockPlacement(event);

            if (place.getFace() == BlockFace.OTHER && PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_9)) {
                return;
            }
            Vector3i targetBlock = place.getBlockPosition();
            BlockFace face = place.getFace();
            Vector3f targetCursor = place.getCursorPosition();
            if (player.hasCursor != 1 || !targetBlock.equals(player.cursorBlock) || face != player.cursorBlockFace) {
                if (player.hasCursor != -1) {
                    CheckManagerListener.handleQueuedPlaces(player, false, false, 0, 0, System.currentTimeMillis());
                }
                player.hasCursor = 1;
                player.cursorBlock = targetBlock;
                player.cursorBlockFace = face;
                player.cursor = targetCursor;
            } else if (!targetCursor.equals(player.cursor)) {
                if (player.cursor != null) {
                    CheckManagerListener.handleQueuedPlaces(player, false, false, 0, 0, System.currentTimeMillis());
                    player.hasCursor = 1;
                    player.cursorBlock = targetBlock;
                    player.cursorBlockFace = face;
                }
                player.cursor = targetCursor;
            }
        }

        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {

            GrimPlayer player = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (player == null) return;

            WrapperPlayClientInteractEntity useEntity = new WrapperPlayClientInteractEntity(event);

            Integer entityId = useEntity.getEntityId();
            Optional<Vector3f> target = useEntity.getTarget();
            if (target.isPresent()) {
                Vector3f targetCursor = target.get();
                if (player.hasCursor != 0 || !entityId.equals(player.cursorEntityId)) {
                    if (player.hasCursor != -1) {
                        CheckManagerListener.handleQueuedPlaces(player, false, false, 0, 0, System.currentTimeMillis());
                    }
                    player.hasCursor = 0;
                    player.cursorEntityId = entityId;
                    player.cursor = targetCursor;
                } else if (!targetCursor.equals(player.cursor)) {
                    if (player.cursor != null) {
                        CheckManagerListener.handleQueuedPlaces(player, false, false, 0, 0, System.currentTimeMillis());
                        player.hasCursor = 0;
                        player.cursorEntityId = entityId;
                    }
                    player.cursor = targetCursor;
                }
            } else {
                if (player.hasCursor != 0 || !entityId.equals(player.cursorEntityId)) {
                    if (player.hasCursor != -1) {
                        CheckManagerListener.handleQueuedPlaces(player, false, false, 0, 0, System.currentTimeMillis());
                    }
                    player.hasCursor = 0;
                    player.cursorEntityId = entityId;
                }
            }
        }

    }

}
