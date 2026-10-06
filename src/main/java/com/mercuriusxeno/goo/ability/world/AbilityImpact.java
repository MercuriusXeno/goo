package com.mercuriusxeno.goo.ability.world;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.program.HostKind;
import com.mercuriusxeno.goo.ability.program.LandingHost;
import com.mercuriusxeno.goo.ability.program.LingerStep;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.ability.program.ProgramLoadException;
import com.mercuriusxeno.goo.network.ChainBurnoutPayload;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.Optional;

/**
 * What an ability goo does when it lands on a block: it lands in the cell
 * {@link LandingSpot} decides, announces its burnout, and runs its program
 * on that landing the tick it splats. The landing places no block of its
 * own; a program ending that tick leaves only its effect, and one that
 * lingers stands its own block through its linger step (decisions
 * splat-runs-the-program-no-fuse, lingering-abilities-place-their-own-thing).
 */
public final class AbilityImpact {

    private static final String LOG_PROGRAM_REFUSED = "Ability {} program refused for the landing host: {}";

    private AbilityImpact() {
    }

    /**
     * Lands an ability goo on a block.
     *
     * @param level   the server level
     * @param pos     the struck block
     * @param type    the goo type thrown
     * @param face    the struck face
     * @param ability the ability the goo names
     */
    public static void land(ServerLevel level, BlockPos pos, ResourceKey<GooTypeDefinition> type,
                            Direction face, AbilityDefinition ability) {
        land(level, pos, type, face, ability, null);
    }

    /**
     * Lands an ability goo on a block, its world actions anchored at the
     * aimed point where it names one, and at the landing cell's center
     * otherwise, so a free ability resolves where it was aimed.
     * aim-point-follows-the-cursor
     *
     * @param level   the server level
     * @param pos     the struck block
     * @param type    the goo type thrown
     * @param face    the struck face
     * @param ability the ability the goo names
     * @param point   the aimed point the ability resolves at, or null for the cell's center
     */
    public static void land(ServerLevel level, BlockPos pos, ResourceKey<GooTypeDefinition> type,
                            Direction face, AbilityDefinition ability, @Nullable Vec3 point) {
        Optional<LandingSpot> spot = LandingSpot.resolve(level, pos, face);
        if (spot.isEmpty()) {
            return;
        }
        BlockPos cell = spot.get().cell();
        LandingHost host = new LandingHost(level, cell, face, spot.get().waterlogged(), type,
                ability.id().toString(), point == null ? Vec3.atCenterOf(cell) : point);
        AbilitySplat.resolve(new Landing(host, ability));
    }

    /**
     * Whether an ability's program stands its own block to run on after the
     * splat, so its burnout plays when that block explodes rather than as the
     * blob lands (decision elemental-explosion-per-type).
     *
     * @param ability the landing ability
     * @return true when a top-level step lingers
     */
    static boolean lingers(AbilityDefinition ability) {
        return ability.behaviors().stream().anyMatch(LingerStep.class::isInstance);
    }

    /**
     * A blob's world actions as it lands.
     *
     * @param host    the landing host
     * @param ability the ability the blob names
     */
    private record Landing(LandingHost host, AbilityDefinition ability) implements AbilitySplat {

        @Override
        public void announceBurnout() {
            new ChainBurnoutPayload(host.cell(), host.face().ordinal(), GooTypes.id(host.gooType()),
                    host.abilityId()).sendToTracking(host.level());
        }

        @Override
        public boolean lingers() {
            return AbilityImpact.lingers(ability);
        }

        @Override
        public void runProgram() {
            try {
                ProgramBehavior.forHost(ability.behaviors(), HostKind.LANDING).tick(host);
            } catch (ProgramLoadException e) {
                Goo.LOGGER.error(LOG_PROGRAM_REFUSED, ability.id(), e.getMessage());
            }
        }
    }
}
