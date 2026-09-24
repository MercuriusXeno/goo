package com.mercuriusxeno.goo.lab;

/**
 * The lab ground one machine's bay stands on.
 *
 * @param machine       the machine the plot is for
 * @param bounds        the plot's footprint and headroom relative to the lab origin
 * @param signOffset    where the sign naming the machine stands
 * @param machineOffset where the bay's machine block stands; the bay's other blocks sit around it
 */
public record LabPlot(LabMachine machine, LabBox bounds, LabOffset signOffset, LabOffset machineOffset) {
}
