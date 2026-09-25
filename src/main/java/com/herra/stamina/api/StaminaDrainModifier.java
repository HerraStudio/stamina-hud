package com.herra.stamina.api;

import net.minecraft.world.entity.player.Player;

/**
 * Lets other HERRA mods rewrite stamina costs without touching this mod's
 * internals - e.g. heavy armor increases sprint drain, an exoskeleton
 * reduces it, GWO weapon handling adds a jump penalty while overweight.
 *
 * <p>Register instances through
 * {@link StaminaAPI#registerDrainModifier(StaminaDrainModifier)}. All
 * registered modifiers are applied in registration order, each one
 * receiving the previous output:</p>
 *
 * <pre>{@code
 * StaminaAPI.registerDrainModifier((player, action, cost) ->
 *     player.getItemBySlot(EquipmentSlot.CHEST).is(HeavyArmorTags.EXO) ? cost * 0.7f : cost);
 * }</pre>
 */
@FunctionalInterface
public interface StaminaDrainModifier {

    /**
     * @param player the player the cost applies to
     * @param action what is being paid for
     * @param currentCost the cost after all previously registered modifiers
     * @return the new cost (negative values are treated as zero)
     */
    float modify(Player player, StaminaAction action, float currentCost);
}
