package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.quantum.Portal;
import com.mercuriusxeno.goo.ability.quantum.PortalShots;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Places a Quantum portal of the shot's color on the face the cast struck,
 * for its caster, and finishes. Quantum's Portal is {@code quantum_portal}.
 * player-holds-one-pair-of-portals
 * portals-link-by-looking-through-portal-vision
 */
public record PortalStep() implements TargetGatedStep {

    private static final String NAME = "quantum_portal";

    /**
     * Codec for the step: it takes no params.
     */
    public static final MapCodec<PortalStep> CODEC = MapCodec.unit(PortalStep::new);

    /**
     * The registered type.
     */
    public static final StepType<PortalStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<PortalStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.hostAs(PortalHost.class).placePortal();
        return true;
    }

    /**
     * A cast at a face no frame fits on, or one no button's shot carries, is
     * refused before it lands, costing nothing and showing nothing.
     * portal-takes-over-left-and-right-click-for-blue-and-orange
     *
     * @param thrower the casting player
     * @param struck  the block the cast would strike
     * @param face    the face it would strike
     * @return true where a shot's frame fits on the face
     */
    @Override
    public boolean admitsFace(ServerPlayer thrower, BlockPos struck, Direction face) {
        return PortalShots.colorOf(thrower.getUUID())
                .flatMap(color -> Portal.frameOn(thrower.level(), struck, face, thrower.getDirection(), color))
                .isPresent();
    }

    /**
     * Whether an ability places portals, whose left click and right click
     * shoot the blue and the orange portal.
     *
     * @param behaviors an ability's top-level steps
     * @return true where a portal step stands among them
     */
    public static boolean placesPortals(List<Step> behaviors) {
        return behaviors.stream().anyMatch(PortalStep.class::isInstance);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.PORTAL);
    }
}
