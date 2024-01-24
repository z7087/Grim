package ac.grim.grimac.events.bukkit;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.events.packets.patch.ForceStopUseItem;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.PlayerItemHeldEvent;

public class ItemHeldEvent implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemHeldEvent(PlayerItemHeldEvent event) {
        if (event.getPreviousSlot() != event.getNewSlot()) {
            ForceStopUseItem.handleSlowStateChangeBukkit(event.getPlayer());
        }
    }
}
