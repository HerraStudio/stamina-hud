package com.herra.stamina.api;

/**
 * 体力变化的来源标识。传给消耗规则修改器与事件，生态模组可对
 * 不同来源做差异化处理（如重甲加大疾跑消耗、外骨骼减免跳跃消耗）。
 */
public enum StaminaAction {

    /** 疾跑（持续消耗，每 tick 结算）。 */
    SPRINT,

    /** 跳跃（一次性消耗）。 */
    JUMP,

    /** 游泳（持续消耗）。 */
    SWIM,

    /** 疾速游泳（持续消耗）。 */
    SWIM_SPRINT,

    /** 近战攻击命中实体（一次性消耗）。 */
    ATTACK,

    /** 破坏方块（一次性消耗）。 */
    BREAK_BLOCK,

    /** 第三方模组通过 StaminaAPI 发起的一次性消耗。 */
    CUSTOM
}
