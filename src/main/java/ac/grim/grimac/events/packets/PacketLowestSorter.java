package ac.grim.grimac.events.packets;

import ac.grim.grimac.events.packets.patch.TeleportStupidityHandler;
import com.github.retrooper.packetevents.event.*;

import java.util.ArrayList;
import java.util.List;

// just a way to sort lowest listeners
public class PacketLowestSorter extends PacketListenerAbstract {

    private List<PacketListenerAbstract> listenerList = new ArrayList<PacketListenerAbstract>();

    public PacketLowestSorter() {
        super(PacketListenerPriority.LOWEST);

        listenerList.add(new TeleportStupidityHandler());
        listenerList.add(new PacketPingListener());
    }

    @Override
    public void onUserConnect(UserConnectEvent event) {
        for (PacketListenerAbstract listener : listenerList) {
            try {
                listener.onUserConnect(event);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onUserLogin(UserLoginEvent event) {
        for (PacketListenerAbstract listener : listenerList) {
            try {
                listener.onUserLogin(event);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onUserDisconnect(UserDisconnectEvent event) {
        for (PacketListenerAbstract listener : listenerList) {
            try {
                listener.onUserDisconnect(event);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        for (PacketListenerAbstract listener : listenerList) {
            try {
                listener.onPacketReceive(event);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        for (PacketListenerAbstract listener : listenerList) {
            try {
                listener.onPacketSend(event);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onPacketEventExternal(PacketEvent event) {
        for (PacketListenerAbstract listener : listenerList) {
            try {
                listener.onPacketEventExternal(event);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
