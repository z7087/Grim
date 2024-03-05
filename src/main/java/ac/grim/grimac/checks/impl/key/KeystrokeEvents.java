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

public enum KeystrokeEvents {
    OUTSIDE_TICK(true) {
        @Override
        public boolean isType(GrimPlayer player, PacketTypeCommon packetType, PacketReceiveEvent event) {
            return isTransaction(packetType) ||
                    packetType == PacketType.Play.Client.TELEPORT_CONFIRM ||
                    (player.packetStateData.lastPacketWasTeleport && (packetType == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION || packetType == PacketType.Play.Client.VEHICLE_MOVE)) ||
                    (packetType == PacketType.Play.Client.CLICK_WINDOW && player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_13));
        }
    },

    UPDATE_CONTROLLER() {
        @Override
        public boolean isType(GrimPlayer player, PacketTypeCommon packetType, PacketReceiveEvent event) {
            return packetType == PacketType.Play.Client.HELD_ITEM_CHANGE;
        }
    },

    HANDLE_SCREEN_INPUT(true) {
        // idk how the screen handle input...
        @Override
        public boolean isType(GrimPlayer player, PacketTypeCommon packetType, PacketReceiveEvent event) {
            return packetType == PacketType.Play.Client.CLICK_WINDOW || packetType == PacketType.Play.Client.CREATIVE_INVENTORY_ACTION;
        }
    },

    UPDATE_VEHIDLE_LOOK() {
        @Override
        public boolean isType(GrimPlayer player, PacketTypeCommon packetType, PacketReceiveEvent event) {
            return packetType == PacketType.Play.Client.PLAYER_ROTATION;
        }
    },

    UPDATE_VEHIDLE_INPUT() {
        @Override
        public boolean isType(GrimPlayer player, PacketTypeCommon packetType, PacketReceiveEvent event) {
            return packetType == PacketType.Play.Client.STEER_VEHICLE;
        }

        @Override
        public KeystrokeEvents getNext(GrimPlayer player, boolean type) {
            return type ? UPDATE_VEHIDLE_POSITION : UPDATE_SPRINT_STATE;
        }
    },

    UPDATE_VEHIDLE_POSITION() {
        @Override
        public boolean isType(GrimPlayer player, PacketTypeCommon packetType, PacketReceiveEvent event) {
            return packetType == PacketType.Play.Client.VEHICLE_MOVE;
        }

        @Override
        public KeystrokeEvents getNext(GrimPlayer player, boolean type) {
            return UPDATE_PLAYER_LOCATION.getNext();
        }
    },

    UPDATE_SPRINT_STATE() {
        @Override
        public boolean isType(GrimPlayer player, PacketTypeCommon packetType, PacketReceiveEvent event) {
            if (packetType == PacketType.Play.Client.ENTITY_ACTION) {
                WrapperPlayClientEntityAction.Action action = (new WrapperPlayClientEntityAction(event)).getAction();
                return action == WrapperPlayClientEntityAction.Action.START_SPRINTING || action == WrapperPlayClientEntityAction.Action.STOP_SPRINTING;
            }
            return false;
        }
    },

    UPDATE_SNEAK_STATE() {
        @Override
        public boolean isType(GrimPlayer player, PacketTypeCommon packetType, PacketReceiveEvent event) {
            if (packetType == PacketType.Play.Client.ENTITY_ACTION) {
                WrapperPlayClientEntityAction.Action action = (new WrapperPlayClientEntityAction(event)).getAction();
                return action == WrapperPlayClientEntityAction.Action.START_SNEAKING || action == WrapperPlayClientEntityAction.Action.STOP_SNEAKING;
            }
            return false;
        }
    },

    UPDATE_PLAYER_LOCATION() {
        @Override
        public boolean isType(GrimPlayer player, PacketTypeCommon packetType, PacketReceiveEvent event) {
            return WrapperPlayClientPlayerFlying.isFlying(packetType);
        }
    };

    protected static final KeystrokeEvents[] VALUES = values();

    protected final boolean repeatable;

    KeystrokeEvents() {
        this(false);
    }

    KeystrokeEvents(boolean repeatable) {
        this.repeatable = repeatable;
    }

    public boolean isRepeatable() {
        return repeatable;
    }

    public boolean isType(GrimPlayer player, PacketReceiveEvent event) {
        return isType(player, event.getPacketType(), event);
    }

    public boolean isType(GrimPlayer player, PacketTypeCommon packetType, PacketReceiveEvent event) {
        return false;
    }

    public KeystrokeEvents getNext(GrimPlayer player, PacketTypeCommon packetType, PacketReceiveEvent event) {
        return getNext(player, false);
    }

    public KeystrokeEvents getNext(GrimPlayer player, boolean type) {
        return VALUES[(ordinal()+1) % VALUES.length];
    }

    protected void throwException() {
        throw PNSException.INSTANCE;
    }

    public static class PNSException extends RuntimeException {
        public static final PNSException INSTANCE = new PNSException();
    }
}