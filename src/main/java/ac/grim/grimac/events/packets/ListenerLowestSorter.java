package ac.grim.grimac.events.packets;

import ac.grim.grimac.events.packets.patch.TeleportStupidityHandler;
import com.github.retrooper.packetevents.event.PacketListenerPriority;

// just a way to sort lowest listeners
public class ListenerLowestSorter extends ListenerSorterAbstract {

    public ListenerLowestSorter() {
        super(PacketListenerPriority.LOWEST);

        listenerList.add(new TeleportStupidityHandler());
        listenerList.add(new PacketPingListener());
    }

}
