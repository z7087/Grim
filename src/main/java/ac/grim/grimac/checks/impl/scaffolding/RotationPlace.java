package ac.grim.grimac.checks.impl.scaffolding;

import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.BlockPlaceCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.BlockPlace;
import ac.grim.grimac.utils.anticheat.update.PostBlockPlace;
import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.grim.grimac.utils.data.Pair;
import ac.grim.grimac.utils.nmsutil.Ray;
import ac.grim.grimac.utils.nmsutil.ReachUtils;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.util.Vector3i;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@CheckData(name = "RotationPlace")
public class RotationPlace extends BlockPlaceCheck {
    double threshold = 0.001;

    public RotationPlace(GrimPlayer player) {
        super(player);
    }

    // Use post flying because it has the correct rotation, and can't false easily.
    @Override
    public void onPostFlyingBlockPlace(PostBlockPlace place) {
        if (place.getMaterial() == StateTypes.SCAFFOLDING) return;

        // This can false with rapidly moving yaw in 1.8+ clients
        // idk where
        if (!didRayTraceHitBlock(place)) {
            flagAndAlert("post-block");
        } else if (!didRayTraceHitCursor(place)) {
            flagAndAlert("post-cursor");
        }
    }

    private boolean didRayTraceHitBlock(PostBlockPlace place) {

        SimpleCollisionBox box = new SimpleCollisionBox(place.getPlacedAgainstBlockLocation());
        //box.expand(player.getClientVersion().isOlderThan(ClientVersion.V_1_9) ? 0.05 : player.getMovementThreshold());

        return isEyeInBox(box) || postCheck(place, box);
    }

    private boolean didRayTraceHitCursor(PostBlockPlace place) {
        // cursor from viarewind may false
        if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_11) && player.getClientVersion().isOlderThan(ClientVersion.V_1_11)) {
            return true;
        }

        Vector3i placeLocation = place.getPlacedAgainstBlockLocation();
        Vector3f cursor = place.getCursor();
        Vector3d clickLocation = new Vector3d(placeLocation.getX() + cursor.getX(), placeLocation.getY() + cursor.getY(), placeLocation.getZ() + cursor.getZ());

        SimpleCollisionBox box = new SimpleCollisionBox(clickLocation, clickLocation).expand(threshold);
        //box.expand(player.getClientVersion().isOlderThan(ClientVersion.V_1_9) ? 0.05 : player.getMovementThreshold());

        return isEyeInBox(box) || postCheck(place, box);
    }

    private boolean isEyeInBox(SimpleCollisionBox box) {
        double minEyeHeight = Collections.min(player.getPossibleEyeHeights());
        double maxEyeHeight = Collections.max(player.getPossibleEyeHeights());

        SimpleCollisionBox eyePositions = new SimpleCollisionBox(player.x, player.y + minEyeHeight, player.z, player.x, player.y + maxEyeHeight, player.z);

        return eyePositions.isIntersected(box);
    }

    private boolean postCheck(PostBlockPlace place, SimpleCollisionBox box) {
        float yaw = place.hasLook() ? place.getYaw() : player.xRot;
        float pitch = place.hasLook() ? place.getPitch() : player.yRot;

        List<Vector3f> possibleLookDirs = new ArrayList<>(Arrays.asList(
                new Vector3f(player.lastXRot, pitch, 0),
                new Vector3f(yaw, pitch, 0)
        ));

        // 1.9+ players could be a tick behind because we don't get skipped ticks
        if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_9)) {
            possibleLookDirs.add(new Vector3f(player.lastXRot, player.lastYRot, 0));
        }

        // 1.7 players do not have any of these issues! They are always on the latest look vector
        if (player.getClientVersion().isOlderThan(ClientVersion.V_1_8)) {
            possibleLookDirs = Collections.singletonList(new Vector3f(yaw, pitch, 0));
        }

        for (double d : player.getPossibleEyeHeights()) {
            for (Vector3f lookDir : possibleLookDirs) {
                // x, y, z are correct for the block placement even after post tick because of code elsewhere
                Vector3d starting = new Vector3d(player.x, player.y + d, player.z);
                // xRot and yRot are a tick behind
                Ray trace = new Ray(player, starting.getX(), starting.getY(), starting.getZ(), lookDir.getX(), lookDir.getY());
                Pair<Vector, BlockFace> intercept = ReachUtils.calculateIntercept(box, trace.getOrigin(), trace.getPointAtDistance(6));

                if (intercept.getFirst() != null) return true;
            }
        }

        return false;
    }
}
