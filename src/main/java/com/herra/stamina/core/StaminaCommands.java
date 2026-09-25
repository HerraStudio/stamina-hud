package com.herra.stamina.core;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.herra.stamina.api.StaminaAPI;
import com.herra.stamina.api.StaminaModifier;
import com.herra.stamina.config.StaminaServerConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;
import java.util.Locale;

/**
 * /stamina —— 游戏内数值调节指令。
 *
 * <pre>
 * /stamina [info] [player]              查看体力状态（查看他人需 OP 2）
 * /stamina set &lt;player&gt; &lt;value&gt;        设置体力（OP 2）
 * /stamina add &lt;player&gt; &lt;value&gt;        增减体力，可为负（OP 2）
 * /stamina exhaust &lt;player&gt;            清空并进入透支锁定，测试惩罚（OP 2）
 * /stamina reset &lt;player&gt;              回满（OP 2）
 * /stamina modifier list [player]       生效中的修改器（OP 2）
 * /stamina modifier clear &lt;player&gt;      清空修改器（OP 2）
 * /stamina modifier give &lt;player&gt; &lt;id&gt; &lt;seconds&gt; [drainMultiplier] [maxBonus] [regenBonus]
 *                                      测试生态接口（与医药模组同路径，OP 2）
 * /stamina config show                  查看服务器数值（OP 2）
 * /stamina config max|sprint-drain|jump-cost|swim-drain|swim-sprint-drain|
 *                    attack-cost|break-cost|regen|regen-exhausted|delay|
 *                    delay-exhausted|sprint-stop|release|block-jump &lt;value&gt;
 *                                      调节并写入配置（OP 2）
 * </pre>
 *
 * <p>config 子指令直接修改 SERVER 配置并保存到
 * {@code world/serverconfig/herra-stamina-server.toml}，重启后仍然生效。</p>
 */
public final class StaminaCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(build());
    }

    /** 指令树根。每个分支拆成独立方法，避免深层括号嵌套。 */
    private static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("stamina")
                .executes(ctx -> info(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                .then(infoNode())
                .then(setNode())
                .then(addNode())
                .then(exhaustNode())
                .then(resetNode())
                .then(modifierNode())
                .then(configNode());
    }

    // ------------------------------------------------------------------ info

    private static LiteralArgumentBuilder<CommandSourceStack> infoNode() {
        return Commands.literal("info")
                .executes(ctx -> info(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> info(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))));
    }

    // ------------------------------------------------------------------ set / add / exhaust / reset

    private static LiteralArgumentBuilder<CommandSourceStack> setNode() {
        return Commands.literal("set")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("value", FloatArgumentType.floatArg(0.0F, 1_000_000.0F))
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    float value = FloatArgumentType.getFloat(ctx, "value");
                                    StaminaAPI.setStamina(target, value);
                                    ctx.getSource().sendSuccess(() -> Component.translatable(
                                            "herra_stamina.command.set.success",
                                            target.getGameProfile().getName(), fmt(value)), true);
                                    return 1;
                                })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> addNode() {
        return Commands.literal("add")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("value", FloatArgumentType.floatArg(-1_000_000.0F, 1_000_000.0F))
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    float value = FloatArgumentType.getFloat(ctx, "value");
                                    float before = StaminaAPI.getStamina(target);
                                    StaminaAPI.addStamina(target, value);
                                    float after = StaminaAPI.getStamina(target);
                                    ctx.getSource().sendSuccess(() -> Component.translatable(
                                            "herra_stamina.command.add.success",
                                            target.getGameProfile().getName(), fmt(before), fmt(after),
                                            value >= 0 ? "+" : "", fmt(value)), true);
                                    return 1;
                                })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> exhaustNode() {
        return Commands.literal("exhaust")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            StaminaAPI.setStamina(target, 0.0F);
                            ctx.getSource().sendSuccess(() -> Component.translatable(
                                    "herra_stamina.command.exhaust.success",
                                    target.getGameProfile().getName()), true);
                            return 1;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> resetNode() {
        return Commands.literal("reset")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            StaminaAPI.reset(target);
                            ctx.getSource().sendSuccess(() -> Component.translatable(
                                    "herra_stamina.command.reset.success",
                                    target.getGameProfile().getName(),
                                    fmt(StaminaAPI.getStamina(target))), true);
                            return 1;
                        }));
    }

    // ------------------------------------------------------------------ modifier

    private static LiteralArgumentBuilder<CommandSourceStack> modifierNode() {
        return Commands.literal("modifier")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("list")
                        .executes(ctx -> listModifiers(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> listModifiers(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    int count = StaminaAPI.getActiveModifiers(target).size();
                                    StaminaAPI.clearModifiers(target);
                                    ctx.getSource().sendSuccess(() -> Component.translatable(
                                            "herra_stamina.command.modifier.clear.success",
                                            target.getGameProfile().getName(), String.valueOf(count)), true);
                                    return 1;
                                })))
                // 测试生态接口：与医药模组走同一条 StaminaAPI.applyModifier 路径
                .then(Commands.literal("give")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 86_400))
                                                .executes(StaminaCommands::giveModifier)
                                                .then(Commands.argument("drainMultiplier", FloatArgumentType.floatArg(0.0F, 100.0F))
                                                        .executes(StaminaCommands::giveModifier)
                                                        .then(Commands.argument("maxBonus", FloatArgumentType.floatArg(-1_000_000.0F, 1_000_000.0F))
                                                                .executes(StaminaCommands::giveModifier)
                                                                .then(Commands.argument("regenBonus", FloatArgumentType.floatArg(-100_000.0F, 100_000.0F))
                                                                        .executes(StaminaCommands::giveModifier))))))));
    }

    // ------------------------------------------------------------------ config

    private static LiteralArgumentBuilder<CommandSourceStack> configNode() {
        return Commands.literal("config")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("show").executes(ctx -> showConfig(ctx.getSource())))
                .then(cfgFloat("max", "max_stamina", StaminaServerConfig.MAX_STAMINA, 1.0F, 1_000_000.0F))
                .then(cfgFloat("sprint-drain", "sprint_per_second", StaminaServerConfig.SPRINT_DRAIN_PER_SECOND, 0.0F, 100_000.0F))
                .then(cfgFloat("jump-cost", "jump_cost", StaminaServerConfig.JUMP_COST, 0.0F, 100_000.0F))
                .then(cfgFloat("swim-drain", "swim_per_second", StaminaServerConfig.SWIM_DRAIN_PER_SECOND, 0.0F, 100_000.0F))
                .then(cfgFloat("swim-sprint-drain", "swim_sprint_per_second", StaminaServerConfig.SWIM_SPRINT_DRAIN_PER_SECOND, 0.0F, 100_000.0F))
                .then(cfgFloat("attack-cost", "attack_cost", StaminaServerConfig.ATTACK_COST, 0.0F, 100_000.0F))
                .then(cfgFloat("break-cost", "break_block_cost", StaminaServerConfig.BREAK_BLOCK_COST, 0.0F, 100_000.0F))
                .then(cfgFloat("regen", "per_second", StaminaServerConfig.RECOVERY_PER_SECOND, 0.0F, 100_000.0F))
                .then(cfgFloat("regen-exhausted", "exhausted_per_second", StaminaServerConfig.EXHAUSTED_RECOVERY_PER_SECOND, 0.0F, 100_000.0F))
                .then(cfgFloat("sprint-stop", "sprint_stop_threshold", StaminaServerConfig.SPRINT_STOP_THRESHOLD, 0.0F, 1_000_000.0F))
                .then(cfgFloat("release", "exhausted_release_threshold", StaminaServerConfig.EXHAUSTED_RELEASE_THRESHOLD, 0.0F, 1_000_000.0F))
                .then(cfgBool("block-jump", "exhausted_block_jump", StaminaServerConfig.EXHAUSTED_BLOCK_JUMP))
                .then(cfgInt("delay", "delay_ticks", StaminaServerConfig.RECOVERY_DELAY_TICKS, 0, 1_200))
                .then(cfgInt("delay-exhausted", "exhausted_delay_ticks", StaminaServerConfig.EXHAUSTED_RECOVERY_DELAY_TICKS, 0, 1_200));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> cfgFloat(
            String literal, String key, ModConfigSpec.DoubleValue value, float min, float max) {
        return Commands.literal(literal)
                .then(Commands.argument("value", FloatArgumentType.floatArg(min, max))
                        .executes(ctx -> setConfigDouble(ctx.getSource(), key, value,
                                FloatArgumentType.getFloat(ctx, "value"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> cfgInt(
            String literal, String key, ModConfigSpec.IntValue value, int min, int max) {
        return Commands.literal(literal)
                .then(Commands.argument("value", IntegerArgumentType.integer(min, max))
                        .executes(ctx -> setConfigInt(ctx.getSource(), key, value,
                                IntegerArgumentType.getInteger(ctx, "value"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> cfgBool(
            String literal, String key, ModConfigSpec.BooleanValue value) {
        return Commands.literal(literal)
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> setConfigBool(ctx.getSource(), key, value,
                                BoolArgumentType.getBool(ctx, "value"))));
    }

    // ------------------------------------------------------------------ 处理器

    private static int info(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
        // 查看他人需要 OP 2（查看自己永远允许；控制台始终允许）
        ServerPlayer self = source.getPlayer();
        if (self != null && self != target && !source.hasPermission(2)) {
            source.sendFailure(Component.translatable("herra_stamina.command.no_permission"));
            return 0;
        }
        float stamina = StaminaAPI.getStamina(target);
        float max = StaminaAPI.getMaxStamina(target);
        int percent = Math.round(stamina / max * 100.0F);
        source.sendSuccess(() -> Component.translatable("herra_stamina.command.info.line",
                target.getGameProfile().getName(), fmt(stamina), fmt(max), String.valueOf(percent)), false);
        if (StaminaAPI.isExhausted(target)) {
            source.sendSuccess(() -> Component.translatable("herra_stamina.command.state.exhausted"), false);
        } else if (!StaminaAPI.canSprint(target)) {
            source.sendSuccess(() -> Component.translatable("herra_stamina.command.state.winded"), false);
        }
        for (StaminaAPI.ModifierEntry entry : StaminaAPI.getActiveModifierEntries(target)) {
            source.sendSuccess(() -> Component.translatable("herra_stamina.command.modifier.entry",
                    entry.modifier().id(), remainingText(entry)), false);
        }
        return 1;
    }

    private static int listModifiers(CommandSourceStack source, ServerPlayer target) {
        List<StaminaAPI.ModifierEntry> modifiers = StaminaAPI.getActiveModifierEntries(target);
        if (modifiers.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("herra_stamina.command.modifier.none"), false);
            return 0;
        }
        for (StaminaAPI.ModifierEntry entry : modifiers) {
            source.sendSuccess(() -> Component.translatable("herra_stamina.command.modifier.entry",
                    entry.modifier().id(), remainingText(entry)), false);
        }
        return modifiers.size();
    }

    /**
     * /stamina modifier give &lt;player&gt; &lt;id&gt; &lt;seconds&gt; [drainMultiplier] [maxBonus] [regenBonus]
     * 例：60 秒内消耗减半、上限+30、恢复+5/s —— 与医药模组的 API 调用完全同路径。
     */
    private static int giveModifier(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String id = StringArgumentType.getString(ctx, "id");
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
        float drain = optFloat(ctx, "drainMultiplier", 1.0F);
        float maxBonus = optFloat(ctx, "maxBonus", 0.0F);
        float regen = optFloat(ctx, "regenBonus", 0.0F);

        StaminaModifier modifier = StaminaModifier.builder(id)
                .drainMultiplier(drain)
                .maxStaminaBonus(maxBonus)
                .regenBonus(regen)
                .durationSeconds(seconds)
                .build();
        StaminaAPI.applyModifier(target, modifier);

        ctx.getSource().sendSuccess(() -> Component.translatable("herra_stamina.command.modifier.give.success",
                id, target.getGameProfile().getName(),
                "x" + fmt(drain), signed(maxBonus), signed(regen) + "/s",
                String.valueOf(seconds)), true);
        return 1;
    }

    private static String remainingText(StaminaAPI.ModifierEntry entry) {
        return entry.remainingTicks() < 0
                ? Component.translatable("herra_stamina.command.modifier.permanent").getString()
                : String.format(Locale.ROOT, "%.1fs", entry.remainingTicks() / 20.0F);
    }

    private static float optFloat(CommandContext<CommandSourceStack> ctx, String name, float fallback) {
        try {
            return FloatArgumentType.getFloat(ctx, name);
        } catch (IllegalArgumentException notPresent) {
            return fallback;
        }
    }

    private static String signed(float value) {
        return (value >= 0 ? "+" : "") + fmt(value);
    }

    // ------------------------------------------------------------------ 配置写入

    private static int setConfigDouble(CommandSourceStack source, String key,
                                        ModConfigSpec.DoubleValue value, float newValue) {
        value.set((double) newValue);
        StaminaServerConfig.SPEC.save();
        source.sendSuccess(() -> Component.translatable("herra_stamina.command.config.set",
                key, fmt(newValue)), true);
        return 1;
    }

    private static int setConfigInt(CommandSourceStack source, String key,
                                     ModConfigSpec.IntValue value, int newValue) {
        value.set(newValue);
        StaminaServerConfig.SPEC.save();
        source.sendSuccess(() -> Component.translatable("herra_stamina.command.config.set",
                key, String.valueOf(newValue)), true);
        return 1;
    }

    private static int setConfigBool(CommandSourceStack source, String key,
                                      ModConfigSpec.BooleanValue value, boolean newValue) {
        value.set(newValue);
        StaminaServerConfig.SPEC.save();
        source.sendSuccess(() -> Component.translatable("herra_stamina.command.config.set",
                key, String.valueOf(newValue)), true);
        return 1;
    }

    private static int showConfig(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("herra_stamina.command.config.show.header"), false);
        sendConfigLine(source, "max_stamina", fmt(StaminaServerConfig.f(StaminaServerConfig.MAX_STAMINA)));
        sendConfigLine(source, "sprint_per_second", fmt(StaminaServerConfig.f(StaminaServerConfig.SPRINT_DRAIN_PER_SECOND)));
        sendConfigLine(source, "jump_cost", fmt(StaminaServerConfig.f(StaminaServerConfig.JUMP_COST)));
        sendConfigLine(source, "swim_per_second", fmt(StaminaServerConfig.f(StaminaServerConfig.SWIM_DRAIN_PER_SECOND)));
        sendConfigLine(source, "swim_sprint_per_second", fmt(StaminaServerConfig.f(StaminaServerConfig.SWIM_SPRINT_DRAIN_PER_SECOND)));
        sendConfigLine(source, "attack_cost", fmt(StaminaServerConfig.f(StaminaServerConfig.ATTACK_COST)));
        sendConfigLine(source, "break_block_cost", fmt(StaminaServerConfig.f(StaminaServerConfig.BREAK_BLOCK_COST)));
        sendConfigLine(source, "recovery.per_second", fmt(StaminaServerConfig.f(StaminaServerConfig.RECOVERY_PER_SECOND)));
        sendConfigLine(source, "recovery.exhausted_per_second", fmt(StaminaServerConfig.f(StaminaServerConfig.EXHAUSTED_RECOVERY_PER_SECOND)));
        sendConfigLine(source, "recovery.delay_ticks", String.valueOf(StaminaServerConfig.RECOVERY_DELAY_TICKS.get()));
        sendConfigLine(source, "recovery.exhausted_delay_ticks", String.valueOf(StaminaServerConfig.EXHAUSTED_RECOVERY_DELAY_TICKS.get()));
        sendConfigLine(source, "penalty.sprint_stop_threshold", fmt(StaminaServerConfig.f(StaminaServerConfig.SPRINT_STOP_THRESHOLD)));
        sendConfigLine(source, "penalty.exhausted_release_threshold", fmt(StaminaServerConfig.f(StaminaServerConfig.EXHAUSTED_RELEASE_THRESHOLD)));
        sendConfigLine(source, "penalty.exhausted_block_jump", String.valueOf(StaminaServerConfig.EXHAUSTED_BLOCK_JUMP.get()));
        return 1;
    }

    private static void sendConfigLine(CommandSourceStack source, String key, String value) {
        source.sendSuccess(() -> Component.translatable("herra_stamina.command.config.show.entry",
                key, value), false);
    }

    private static String fmt(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private StaminaCommands() {
    }
}
