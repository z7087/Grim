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

        int dxn = x - distance; int dxp = x + distance;
        int dyn = y - distance; int dyp = y + distance;
        int dzn = z - distance; int dzp = z + distance;

        try {
            for (int ic = dxn >> 4, ice = dxp >> 4; ic <= ice; ++ic) {
                for (int kc = dzn >> 4, kce = dzp >> 4; kc <= kce; ++kc) {
                    if (world.isChunkLoaded(ic, kc)) {
                        // idk if it can optimize but seems good enough
                        // maybe change to bfs?
                        int xs = ic << 4;
                        int zs = kc << 4;
                        int ze = zs + 15;
                        for (int i = Math.max(dxn, xs), ie = Math.min(dxp, xs + 15); i <= ie; ++i) {
                            int dx = Math.abs(x - i);
                            for (int j = dyn + dx, je = dyp - dx; j <= je; ++j) {
                                int dxy = dx + Math.abs(y - j);
                                for (int k = Math.max(dzn + dxy, zs), ke = Math.min(dzp - dxy, ze); k <= ke; ++k) {
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
