package ac.grim.grimac.checks.impl.badpackets;

import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;

@CheckData(name = "BadPacketsO")
public class BadPacketsO extends Check implements PacketCheck {

    public BadPacketsO(GrimPlayer player) {
        super(player);
    }

}
