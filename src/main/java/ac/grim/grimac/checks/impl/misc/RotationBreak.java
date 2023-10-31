package ac.grim.grimac.checks.impl.misc;

import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.grim.grimac.utils.data.Pair;
import ac.grim.grimac.utils.nmsutil.Ray;
import ac.grim.grimac.utils.nmsutil.ReachUtils;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@CheckData(name = "RotationBreak")
public class RotationBreak extends Check implements PacketCheck {

    // The block the player is currently breaking
    Vector3i targetBlock = null;
    boolean needPostCheck = false;

    Vector3d playerLocation = null;

    public RotationBreak(GrimPlayer player) {
        super(player);
    }


    // player must facing a block when start break and finished break that
    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            WrapperPlayClientPlayerDigging dig = new WrapperPlayClientPlayerDigging(event);
            if (dig.getAction() == DiggingAction.START_DIGGING || dig.getAction() == DiggingAction.FINISHED_DIGGING) {
                // new tick, check last digging
                if (targetBlock != null && needPostCheck) {
                    if (!didRayTraceHit(targetBlock)) {
                        flagAndAlert("quick break");
                    }
                    targetBlock = null;
                }

                if (player.gamemode == GameMode.CREATIVE) return;
                if (player.compensatedEntities.getSelf().inVehicle()) return;

                playerLocation = new Vector3d(player.x, player.y, player.z);
                targetBlock = dig.getBlockPosition();
                needPostCheck = true;
            }
        } else if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            if (player.packetStateData.lastPacketWasOnePointSeventeenDuplicate)
                return;
            if (targetBlock != null) {
                if (!didRayTraceHit(targetBlock)) {
                    flagAndAlert("flying");
                }
                targetBlock = null;
            }
        } else if (event.getPacketType() == PacketType.Play.Client.PONG
                   || event.getPacketType() == PacketType.Play.Client.WINDOW_CONFIRMATION
                   || event.getPacketType() == PacketType.Play.Client.HELD_ITEM_CHANGE) {
            if (targetBlock != null && needPostCheck) {
                if (!didRayTraceHit(targetBlock)) {
                    flagAndAlert("post");
                    targetBlock = null;
                }
                needPostCheck = false;
            }
        }
    }

// handlerespawnlalalalalla

    private boolean didRayTraceHit(Vector3i pos) {
        SimpleCollisionBox box = new SimpleCollisionBox(pos);

        List<Vector3f> possibleLookDirs = new ArrayList<>(Arrays.asList(
                new Vector3f(player.lastXRot, player.yRot, 0),
                new Vector3f(player.xRot, player.yRot, 0)
        ));

        // Start checking if player is in the block
        double minEyeHeight = Collections.min(player.getPossibleEyeHeights());
        double maxEyeHeight = Collections.max(player.getPossibleEyeHeights());

        SimpleCollisionBox eyePositions = new SimpleCollisionBox(playerLocation.getX(), playerLocation.getY() + minEyeHeight, playerLocation.getZ(), playerLocation.getX(), playerLocation.getY() + maxEyeHeight, playerLocation.getZ());
        box.expand(player.getMovementThreshold());

        // If the player is inside a block, then they can ray trace through the block and hit the other side of the block
        if (eyePositions.isIntersected(box)) {
            return true;
        }
        // End checking if the player is in the block

        // 1.9+ players could be a tick behind because we don't get skipped ticks
        if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_9)) {
            possibleLookDirs.add(new Vector3f(player.lastXRot, player.lastYRot, 0));
        }

        // 1.7 players do not have any of these issues! They are always on the latest look vector
        if (player.getClientVersion().isOlderThan(ClientVersion.V_1_8)) {
            possibleLookDirs = Collections.singletonList(new Vector3f(player.xRot, player.yRot, 0));
        }

        for (double d : player.getPossibleEyeHeights()) {
            for (Vector3f lookDir : possibleLookDirs) {
                // x, y, z are correct for the block placement even after post tick because of code elsewhere
                Vector3d starting = new Vector3d(playerLocation.getX(), playerLocation.getY() + d, playerLocation.getZ());
                // xRot and yRot are a tick behind
                Ray trace = new Ray(player, starting.getX(), starting.getY(), starting.getZ(), lookDir.getX(), lookDir.getY());
                Pair<Vector, BlockFace> intercept = ReachUtils.calculateIntercept(box, trace.getOrigin(), trace.getPointAtDistance(4.5));

                if (intercept.getFirst() != null) return true;
            }
        }

        return false;
    }
}
