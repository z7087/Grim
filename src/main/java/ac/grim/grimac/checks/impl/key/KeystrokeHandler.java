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

public class PNSException extends RuntimeException {
    public static final PNSException INSTANCE = new PNSException();
}

public enum KeystrokeEvents {
    OUTSIDE_TICK(true) {
        @Override
        public boolean isType(GrimPlayer player, PacketTypeCommon packetType) {
            return isTransaction(packetType) || player.packetStateData.lastPacketWasTeleport;
        }
    },

    UPDATE_CONTROLLER() {
        @Override
        public boolean isType(GrimPlayer player, PacketTypeCommon packetType) {
            return packetType == PacketType.Play.Client.HELD_ITEM_CHANGE;
        }
    },

    HANDLE_SCREEN_INPUT(true) {
        // idk how the screen handle input...
        @Override
        public boolean isType(GrimPlayer player, PacketTypeCommon packetType) {
            return packetType == PacketType.Play.Client.CLICK_WINDOW || packetType == PacketType.Play.Client.CREATIVE_INVENTORY_ACTION;
        }
    },

    UPDATE_VEHIDLE_LOCATION() {
        @Override
        public boolean isType(GrimPlayer player, PacketTypeCommon packetType) {
            return packetType == PacketType.Play.Client.VEHICLE_MOVE;
        }

        @Override
        public KeystrokeEvents getNext() {
            return UPDATE_PLAYER_LOCATION.getNext();
        }
    },

    UPDATE_SPRINT_STATE() {
        @Override
        public boolean isType(GrimPlayer player, PacketReceiveEvent event) {
            if (event.getPacketType() == PacketType.Play.Client.ENTITY_ACTION) {
                WrapperPlayClientEntityAction.Action action = (new WrapperPlayClientEntityAction(event)).getAction();
                return action == WrapperPlayClientEntityAction.Action.START_SPRINTING || action == WrapperPlayClientEntityAction.Action.STOP_SPRINTING;
            }
            return false;
        }

        @Override
        public boolean isType(GrimPlayer player, PacketTypeCommon packetType) {
            throwException();
        }
    },

    UPDATE_SNEAK_STATE() {
        @Override
        public boolean isType(GrimPlayer player, PacketReceiveEvent event) {
            if (event.getPacketType() == PacketType.Play.Client.ENTITY_ACTION) {
                WrapperPlayClientEntityAction.Action action = (new WrapperPlayClientEntityAction(event)).getAction();
                return action == WrapperPlayClientEntityAction.Action.START_SNEAKING || action == WrapperPlayClientEntityAction.Action.STOP_SNEAKING;
            }
            return false;
        }

        @Override
        public boolean isType(GrimPlayer player, PacketTypeCommon packetType) {
            throwException();
        }
    },

    UPDATE_PLAYER_LOCATION() {
        @Override
        public boolean isType(GrimPlayer player, PacketTypeCommon packetType) {
            return WrapperPlayClientPlayerFlying.isFlying(packetType);
        }
    };

    protected static final KeystrokeEvents[] VALUES = values();

    protected final boolean repeatable;

    public KeystrokeEvents() {
        this(false);
    }

    public KeystrokeEvents(boolean repeatable) {
        this.repeatable = repeatable;
    }

    public boolean isRepeatable() {
        return repeatable;
    }

    public boolean isType(GrimPlayer player, PacketReceiveEvent event) {
        return isType(player, event.getPacketType());
    }

    public boolean isType(GrimPlayer player, PacketTypeCommon packetType) {
        return false;
    }

    public KeystrokeEvents getNext() {
        return VALUES[(ordinal()+1) % VALUES.length];
    }

    protected void throwException() {
        throw PNSException.INSTANCE;
    }
}

@CheckData(name = "KeystrokeHandler")
public class KeystrokeHandler extends Check implements PacketCheck {

    public KeystrokeHandler(GrimPlayer playerData) {
        super(playerData);
    }

    @Override
    public void onPacketReceive(final PacketReceiveEvent event) {
    }
}
