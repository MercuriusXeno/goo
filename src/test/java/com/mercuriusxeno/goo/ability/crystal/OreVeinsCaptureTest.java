package com.mercuriusxeno.goo.ability.crystal;

import com.mercuriusxeno.goo.ability.program.HeldSweepPace;
import com.mercuriusxeno.goo.ability.program.HeldSweepWorld;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * One ore-sense pulse out to Glimmer's and Magnet's radius over the fixed
 * synthetic world reveals the same veins on the same pulse ticks as commit
 * b423a17 did, before the ore sense's tick cost was cut: each vein's ore,
 * centroid and blocks, tick by tick.
 * decision glitter-sphere-icons-gem-ore-groups
 * decision diagnose-and-correct-the-lag-on-four-sense-abilities
 */
class OreVeinsCaptureTest {

    private static final double RADIUS = 64;
    private static final HeldSweepPace PACE = new HeldSweepPace(RADIUS);

    @Test
    void aPulseRevealsTheCapturedVeinsOnTheCapturedTicks() {
        BlockPos center = BlockPos.containing(HeldSweepWorld.CASTER);
        List<String> ticks = new ArrayList<>();
        int veins = 0;
        for (int tick = 1; tick <= PACE.ticksToRange(); tick++) {
            List<OreVeins.Vein> reached = OreVeins.firstReachedIn(center, PACE.radiusAt(tick - 1),
                    PACE.radiusAt(tick), RADIUS, HeldSweepWorld.ORES);
            veins += reached.size();
            ticks.add(tick + ":" + String.join("|", reached.stream().map(OreVeinsCaptureTest::written).toList()));
        }

        assertEquals("96 9b4d53f66eb9d5bb3d04e7e95105f861f4238ea12b86ef55f5967a9a83261d69", veins + " " + sha256(String.join("\n", ticks)), "veins revealed, and their SHA-256 by tick");
    }

    private static String written(OreVeins.Vein vein) {
        return String.format(Locale.ROOT, "%s@%s,%s,%s%s", vein.ore(), vein.centroid().x, vein.centroid().y,
                vein.centroid().z, vein.blocks());
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException(missing);
        }
    }
}
