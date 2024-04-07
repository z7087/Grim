package ac.grim.grimac.checks.impl.misc;

import ac.grim.grimac.checks.type.BlockPlaceCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.BlockPlace;
import com.github.retrooper.packetevents.util.Vector3i;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

public class GhostBlockMitigation extends BlockPlaceCheck {

    private boolean allow;
    private int distance;

    public GhostBlockMitigation(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onBlockPlace(final BlockPlace place) {
        if (allow || place.isCancelled() || player.bukkitPlayer == null) return;

        World world = player.bukkitPlayer.getWorld();
        Vector3i pos = place.getPlacedAgainstBlockLocation();

        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        if (Math.pow(x - (int) player.x, 2) + Math.pow(y - (int) player.y, 2) + Math.pow(z - (int) player.z, 2) > 400)
            return;

        int xnd = x - distance; int xpd = x + distance;
        int ynd = y - distance; int ypd = y + distance;
        int znd = z - distance; int zpd = z + distance;

        try {
            for (int ic = xnd >> 4, xec = xpd >> 4; ic <= xec; ++ic) {
                for (int kc = znd >> 4, zec = zpd >> 4; kc <= zec; ++kc) {
                    if (world.isChunkLoaded(ic, kc)) {
                        // need optimize
                        for (int i = Math.max(xnd, ic << 4), xe = Math.min(xpd, (ic << 4) + 15); i <= xe; ++i) {
                            int xd = Math.abs(x - i);
                            for (int j = ynd + id, ye = ypd - id; j <= ye; ++j) {
                                int xyd = xd + Math.abs(y - j);
                                for (int k = Math.max(znd + xyd, kc << 4), ze = Math.min(zpd - xyd, (kc << 4) + 15); k <= ze; ++k) {
                                   Block type = world.getBlockAt(i, j, k);
                                   if (type.getType() != Material.AIR) {
                                       return;
                                   }
                                }
                            }
                        }
                    }
                }
            }
            place.resync();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void reload() {
        super.reload();
        allow = getConfig().getBooleanElse("exploit.allow-building-on-ghostblocks", true);
        distance = getConfig().getIntElse("exploit.distance-to-check-for-ghostblocks", 2);

        if (distance < 2 || distance > 4) distance = 2;
    }
}
