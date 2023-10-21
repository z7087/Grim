// This file was designed and is an original check for GrimAC
// Copyright (C) 2021 DefineOutside
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
package ac.grim.grimac.checks.impl.combat;

import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.grim.grimac.utils.data.packetentity.PacketEntity;
import ac.grim.grimac.utils.math.VectorUtils;
import ac.grim.grimac.utils.nmsutil.ReachUtils;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.util.Vector;

import java.util.*;

// You may not copy the check unless you are licensed under GPL
@CheckData(name = "Reach", configName = "Reach", setback = 10)
public class Reach extends Check implements PacketCheck {
    // Only one flag per reach attack, per entity, per tick.
    // We store position because lastX isn't reliable on teleports.
    private final Map<Integer, SimpleCollisionBox> playerAttackQueue = new LinkedHashMap<>();
    private final Set<Integer> playerAttackPostQueue = new LinkedHashSet<>();
    private final Set<Integer> playerAttackShouldIgnoreQueue = new HashSet<>();
    private Vector3d playerLocation = new Vector3d();
    private static final List<EntityType> blacklisted = Arrays.asList(
            EntityTypes.BOAT,
            EntityTypes.CHEST_BOAT,
            EntityTypes.SHULKER);

    private boolean cancelImpossibleHits;
    private double threshold;
    private double cancelBuffer; // For the next 4 hits after using reach, we aggressively cancel reach

    public Reach(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(final PacketReceiveEvent event) {
        if (!player.disableGrim && event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity action = new WrapperPlayClientInteractEntity(event);

            // Don't let the player teleport to bypass reach
            if (player.getSetbackTeleportUtil().shouldBlockMovement()) {
                event.setCancelled(true);
                player.onPacketCancel();
                return;
            }

            PacketEntity entity = player.compensatedEntities.entityMap.get(action.getEntityId());
            // Stop people from freezing transactions before an entity spawns to bypass reach
            if (entity == null) {
                // Only cancel if and only if we are tracking this entity
                // This is because we don't track paintings.
                if (shouldModifyPackets() && player.compensatedEntities.serverPositionsMap.containsKey(action.getEntityId())) {
                    event.setCancelled(true);
                    player.onPacketCancel();
                }
                return;
            }

            // Dead entities cause false flags (https://github.com/GrimAnticheat/Grim/issues/546)
            if (entity.isDead) return;

            // TODO: Remove when in front of via
            if (entity.type == EntityTypes.ARMOR_STAND && player.getClientVersion().isOlderThan(ClientVersion.V_1_8)) return;

            if (player.gamemode == GameMode.CREATIVE) return;
            if (player.compensatedEntities.getSelf().inVehicle()) return;
            if (entity.riding != null) return;

            SimpleCollisionBox targetBox = entity.getPossibleCollisionBoxes();
            if (entity.type == EntityTypes.END_CRYSTAL) {
                targetBox = new SimpleCollisionBox(entity.desyncClientPos.subtract(1, 0, 1), entity.desyncClientPos.add(1, 2, 1));
            }
            if (player.getClientVersion().isOlderThanOrEquals(ClientVersion.V_1_8)) targetBox.expand(0.1);

            // ignore dup entity, seems if a player doesnt send c03 the box doesnt reset?
            if (!playerAttackShouldIgnoreQueue.contains(action.getEntityId())) {
                playerAttackQueue.put(action.getEntityId(), targetBox); // Queue for next tick for very precise check
                playerAttackPostQueue.add(action.getEntityId());
                playerAttackShouldIgnoreQueue.add(action.getEntityId());
                playerLocation = new Vector3d(player.x, player.y, player.z);
            }

            if (shouldModifyPackets() && cancelImpossibleHits && isKnownInvalid(entity, targetBox.copy())) {
                event.setCancelled(true);
                player.onPacketCancel();
            }
        }

        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            // Teleports don't interpolate, duplicate 1.17 packets don't interpolate
            if (player.packetStateData.lastPacketWasTeleport || player.packetStateData.lastPacketWasOnePointSeventeenDuplicate)
                return;
            tickFlying();
        } else if (event.getPacketType() == PacketType.Play.Client.PONG
                   || event.getPacketType() == PacketType.Play.Client.WINDOW_CONFIRMATION
                   || event.getPacketType() == PacketType.Play.Client.HELD_ITEM_CHANGE) {
            tickPost();
        }
    }

    // This method finds the most optimal point at which the user should be aiming at
    // and then measures the distance between the player's eyes and this target point
    //
    // It will not cancel every invalid attack but should cancel 3.05+ or so in real-time
    // Let the post look check measure the distance, as it will always return equal or higher
    // than this method.  If this method flags, the other method WILL flag.
    //
    // Meaning that the other check should be the only one that flags.
    private boolean isKnownInvalid(PacketEntity reachEntity, SimpleCollisionBox targetBox) {
        // If the entity doesn't exist, or if it is exempt, or if it is dead
        if ((blacklisted.contains(reachEntity.type) || !reachEntity.isLivingEntity()) && reachEntity.type != EntityTypes.END_CRYSTAL)
            return false; // exempt

        if (player.gamemode == GameMode.CREATIVE) return false;
        if (player.compensatedEntities.getSelf().inVehicle()) return false;

        double lowest = 6;
        // Filter out what we assume to be cheats
        if (cancelBuffer != 0) {
            return checkReach(reachEntity, targetBox, true) != null; // If they flagged
        } else {
            if (!player.packetStateData.didLastMovementIncludePosition || player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_9))
                targetBox.expand(player.getMovementThreshold());
            // Don't allow blatant cheats to get first hit
            for (double eyes : player.getPossibleEyeHeights()) {
                Vector from = new Vector(player.x, player.y + eyes, player.z);
                Vector closestPoint = VectorUtils.cutBoxToVector(from, targetBox);
                lowest = Math.min(lowest, closestPoint.distance(from));
            }
        }

        return lowest > 3;
    }

    private void tickFlying() {
        for (Map.Entry<Integer, SimpleCollisionBox> attack : playerAttackQueue.entrySet()) {
            PacketEntity reachEntity = player.compensatedEntities.entityMap.get(attack.getKey());

            if (reachEntity != null) {
                String result = checkReach(reachEntity, attack.getValue(), false);
                if (result != null) {
                    flagAndAlert(result);
                }
            }
        }
        playerAttackQueue.clear();
        playerAttackPostQueue.clear();
        playerAttackShouldIgnoreQueue.clear();
    }

    private void tickPost() {
        for (int attack : playerAttackPostQueue) {
            PacketEntity reachEntity = player.compensatedEntities.entityMap.get(attack);

            if (reachEntity != null) {
                SimpleCollisionBox targetBox = playerAttackQueue.get(attack);
                if (targetBox == null) continue;
                String result = checkReach(reachEntity, targetBox.copy(), true);
                if (result != null) {
                    flagAndAlert(result);
                    playerAttackQueue.remove(attack);
                }
            }
        }
        playerAttackPostQueue.clear();
    }

    private String checkReach(PacketEntity reachEntity, SimpleCollisionBox targetBox, boolean isPrediction) {
        if (!player.packetStateData.didLastLastMovementIncludePosition || player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_9))
            targetBox.expand(player.getMovementThreshold());

        targetBox.expand(threshold);

        double minDistance = Double.MAX_VALUE;

        // https://bugs.mojang.com/browse/MC-67665
        List<Vector> possibleLookDirs = new ArrayList<>(Arrays.asList(ReachUtils.getLook(player, player.xRot, player.yRot)));

        // If we are a tick behind, we don't know their next look so don't bother doing this
        if (!isPrediction) {
            possibleLookDirs.add(ReachUtils.getLook(player, player.lastXRot, player.yRot));

            // 1.9+ players could be a tick behind because we don't get skipped ticks
            if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_9)) {
                possibleLookDirs.add(ReachUtils.getLook(player, player.lastXRot, player.lastYRot));
            }

            // 1.7 players do not have any of these issues! They are always on the latest look vector
            if (player.getClientVersion().isOlderThan(ClientVersion.V_1_8)) {
                possibleLookDirs = Collections.singletonList(ReachUtils.getLook(player, player.xRot, player.yRot));
            }
        }

        for (Vector lookVec : possibleLookDirs) {
            for (double eye : player.getPossibleEyeHeights()) {
                Vector eyePos = new Vector(playerLocation.getX(), playerLocation.getY() + eye, playerLocation.getZ());
                Vector endReachPos = eyePos.clone().add(new Vector(lookVec.getX() * 6, lookVec.getY() * 6, lookVec.getZ() * 6));

                Vector intercept = ReachUtils.calculateIntercept(targetBox, eyePos, endReachPos).getFirst();

                if (ReachUtils.isVecInside(targetBox, eyePos)) {
                    minDistance = 0;
                    break;
                }

                if (intercept != null) {
                    minDistance = Math.min(eyePos.distance(intercept), minDistance);
                }
            }
        }

        // if the entity is not exempt and the entity is alive
        if ((!blacklisted.contains(reachEntity.type) && reachEntity.isLivingEntity()) || reachEntity.type == EntityTypes.END_CRYSTAL) {
            if (minDistance == Double.MAX_VALUE) {
                cancelBuffer = 1;
                if (isPrediction)
                    return "Missed hitbox (post)";
                return "Missed hitbox";
            } else if (minDistance > 3) {
                cancelBuffer = 1;
                if (isPrediction)
                    return String.format("%.5f", minDistance) + " blocks (post)";
                return String.format("%.5f", minDistance) + " blocks";
            } else {
                cancelBuffer = Math.max(0, cancelBuffer - 0.25);
            }
        }

        return null;
    }

    @Override
    public void reload() {
        super.reload();
        this.cancelImpossibleHits = getConfig().getBooleanElse("Reach.block-impossible-hits", true);
        this.threshold = getConfig().getDoubleElse("Reach.threshold", 0.0005);
    }
}
