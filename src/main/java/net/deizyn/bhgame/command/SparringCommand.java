package net.deizyn.bhgame.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.deizyn.bhgame.compat.epicfight.EpicFightCompat;
import net.deizyn.bhgame.core.config.BhGameConfig;
import net.deizyn.bhgame.game.mob.sparring_dummy.SparringCommands;
import net.deizyn.bhgame.game.mob.sparring_dummy.SparringDummy;
import net.deizyn.bhgame.game.mob.sparring_dummy.SparringMove;
import net.deizyn.bhgame.game.mob.sparring_dummy.SparringPattern;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * {@code /bhgame sparring <dummies> ...}: sets up sparring dummies (D73). Animation ids are the ones
 * {@code /epicfight animator play} takes, with the same suggestions.
 */
final class SparringCommand {
    private static final SimpleCommandExceptionType NO_DUMMY =
            new SimpleCommandExceptionType(Component.literal("ไม่มีหุ่นฝึกใน selector นี้"));
    private static final DynamicCommandExceptionType UNKNOWN_ANIMATION =
            new DynamicCommandExceptionType(id -> Component.literal("Epic Fight ไม่มีท่า " + id));
    private static final SimpleCommandExceptionType NOT_LIVING =
            new SimpleCommandExceptionType(Component.literal("เป้าหมายต้องเป็นสิ่งมีชีวิต"));

    private static final SuggestionProvider<CommandSourceStack> ANIMATIONS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(EpicFightCompat.animationIds(), builder);

    /** Suggests an animation for the last word of a space-separated list. */
    private static final SuggestionProvider<CommandSourceStack> ANIMATION_LIST = (context, builder) -> {
        int lastSpace = builder.getRemaining().lastIndexOf(' ');
        return SharedSuggestionProvider.suggestResource(EpicFightCompat.animationIds(),
                builder.createOffset(builder.getStart() + lastSpace + 1));
    };

    static ArgumentBuilder<CommandSourceStack, ?> build() {
        return Commands.literal("sparring").then(Commands.argument("dummies", EntityArgument.entities())
                .then(Commands.literal("info").executes(SparringCommand::info))
                .then(Commands.literal("moves")
                        .executes(SparringCommand::info)
                        .then(Commands.literal("add")
                                .then(Commands.argument("animation", ResourceLocationArgument.id()).suggests(ANIMATIONS)
                                        .executes(context -> add(context, 0F))
                                        .then(Commands.argument("transitionTimeModifier", FloatArgumentType.floatArg())
                                                .executes(context -> add(context,
                                                        FloatArgumentType.getFloat(context, "transitionTimeModifier"))))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("animations", StringArgumentType.greedyString())
                                        .suggests(ANIMATION_LIST)
                                        .executes(SparringCommand::set)))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("index", IntegerArgumentType.integer(1, SparringDummy.MAX_MOVES))
                                        .executes(SparringCommand::remove)))
                        .then(Commands.literal("clear").executes(context -> apply(context,
                                dummy -> dummy.setMoves(List.of()), "ล้างท่าแล้ว (กลับไปใช้หมัดพื้นฐาน)"))))
                .then(Commands.literal("order")
                        .then(Commands.literal("sequence").executes(context -> apply(context,
                                dummy -> dummy.setOrder(SparringDummy.Order.SEQUENCE), "ออกท่าตามลำดับ")))
                        .then(Commands.literal("random").executes(context -> apply(context,
                                dummy -> dummy.setOrder(SparringDummy.Order.RANDOM), "ออกท่าแบบสุ่ม"))))
                .then(Commands.literal("pause")
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(0, SparringDummy.MAX_PAUSE))
                                .executes(context -> {
                                    int ticks = IntegerArgumentType.getInteger(context, "ticks");
                                    return apply(context, dummy -> dummy.setPauseTicks(ticks),
                                            "พักระหว่างท่า " + ticks + " tick");
                                })))
                .then(Commands.literal("range")
                        .then(Commands.argument("blocks", FloatArgumentType.floatArg(0.5F, SparringDummy.MAX_RANGE))
                                .executes(context -> {
                                    float blocks = FloatArgumentType.getFloat(context, "blocks");
                                    return apply(context, dummy -> dummy.setAttackRange(blocks),
                                            "ออกท่าเมื่อเป้าหมายอยู่ใกล้ " + blocks + " บล็อก");
                                })))
                .then(chase())
                .then(Commands.literal("speed")
                        .then(Commands.argument("multiplier", FloatArgumentType.floatArg(SparringMove.MIN_SPEED,
                                        SparringMove.MAX_SPEED))
                                .executes(context -> {
                                    float speed = FloatArgumentType.getFloat(context, "multiplier");
                                    return apply(context, dummy -> dummy.setAnimationSpeed(speed),
                                            "ความเร็วท่า x" + speed);
                                })))
                .then(Commands.literal("name")
                        .then(Commands.literal("clear").executes(context -> apply(context,
                                dummy -> dummy.setEditableName("", false), "ลบชื่อแล้ว")))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> {
                                    String name = SparringDummy.cleanName(StringArgumentType.getString(context, "name"));
                                    return apply(context, dummy -> dummy.setEditableName(name, true),
                                            "ตั้งชื่อ " + name + " (ใช้ & เป็นโค้ดสีได้)");
                                })))
                .then(Commands.literal("stopbelow")
                        .then(Commands.argument("percent", FloatArgumentType.floatArg(0F, SparringDummy.MAX_STOP_BELOW))
                                .executes(context -> {
                                    float percent = FloatArgumentType.getFloat(context, "percent");
                                    return apply(context, dummy -> dummy.setStopBelow(percent), percent <= 0F
                                            ? "ตีไปจนสุด (ไม่หยุดตอนเป้าหมายเลือดน้อย)"
                                            : "หยุดตีและยืนนิ่งเมื่อเป้าหมายเลือดต่ำกว่า " + percent
                                            + "% (กลับมาตีเมื่อเลือดเต็ม)");
                                })))
                .then(commands())
                .then(Commands.literal("target")
                        .then(Commands.literal("nearest").executes(context -> apply(context,
                                SparringDummy::targetNearestPlayer, "ตีผู้เล่นที่ใกล้ที่สุด")))
                        .then(Commands.literal("none").executes(context -> apply(context,
                                SparringDummy::targetNobody, "ไม่หาเป้าหมายเอง (ตีกลับเฉพาะคนที่ตีมัน)")))
                        .then(Commands.argument("entity", EntityArgument.entity()).executes(SparringCommand::target))));
    }

    /** {@code command <event> <command...>} / {@code command <event> clear} (D79). */
    private static ArgumentBuilder<CommandSourceStack, ?> commands() {
        var root = Commands.literal("command")
                .requires(source -> source.hasPermission(Math.max(SparringDummy.COMMAND_PERMISSION,
                        BhGameConfig.sparringCommandPermission)));
        for (SparringCommands.Event event : SparringCommands.Event.values()) {
            root.then(Commands.literal(event.key())
                    .then(Commands.literal("clear").executes(context -> apply(context,
                            dummy -> dummy.setCommands(dummy.commands().with(event, "")),
                            "ล้างคำสั่ง " + event.key())))
                    .then(Commands.argument("command", StringArgumentType.greedyString())
                            .executes(context -> {
                                String line = SparringCommands.clean(StringArgumentType.getString(context, "command"));
                                return apply(context, dummy -> dummy.setCommands(dummy.commands().with(event, line)),
                                        "ตั้งคำสั่ง " + event.key() + ": /" + line);
                            })));
        }
        return root;
    }

    private static ArgumentBuilder<CommandSourceStack, ?> chase() {
        var chase = Commands.literal("chase")
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(context -> {
                            boolean enabled = BoolArgumentType.getBool(context, "enabled");
                            return apply(context, dummy -> dummy.setChase(enabled),
                                    enabled ? "เดินไล่เป้าหมาย" : "ยืนรออยู่กับที่");
                        }));
        for (SparringDummy.ChaseMode mode : SparringDummy.ChaseMode.values()) {
            chase.then(Commands.literal(mode.name().toLowerCase(Locale.ROOT)).executes(context -> apply(context,
                    dummy -> dummy.setChaseMode(mode), "ไล่เป้าหมายแบบ " + mode.name().toLowerCase(Locale.ROOT))));
        }
        return chase;
    }

    private static int add(CommandContext<CommandSourceStack> context, float transition) throws CommandSyntaxException {
        ResourceLocation animation = checked(ResourceLocationArgument.getId(context, "animation"));
        SparringMove move = new SparringMove(animation, transition);
        List<SparringDummy> dummies = dummies(context);
        int added = 0;
        for (SparringDummy dummy : dummies) {
            if (dummy.addMove(move)) {
                added++;
            }
        }
        int count = added;
        context.getSource().sendSuccess(() -> Component.literal("[bhgame] เพิ่มท่า " + move + " ให้หุ่นฝึก "
                + count + "/" + dummies.size() + " ตัว"
                + (count < dummies.size() ? " (ตัวที่เหลือมีครบ " + SparringDummy.MAX_MOVES + " ท่าแล้ว)" : "")), true);
        return count;
    }

    private static int set(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        List<SparringMove> moves = new ArrayList<>();
        for (String word : StringArgumentType.getString(context, "animations").trim().split("\\s+")) {
            ResourceLocation id = ResourceLocation.tryParse(word);
            if (id == null) {
                throw UNKNOWN_ANIMATION.create(word);
            }
            moves.add(new SparringMove(checked(id), 0F));
        }
        if (moves.size() > SparringDummy.MAX_MOVES) {
            throw new SimpleCommandExceptionType(Component.literal("ใส่ได้ไม่เกิน " + SparringDummy.MAX_MOVES + " ท่า"))
                    .create();
        }
        return apply(context, dummy -> dummy.setMoves(moves), "ตั้งท่าใหม่ " + moves.size() + " ท่า");
    }

    private static int remove(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int index = IntegerArgumentType.getInteger(context, "index");
        int removed = 0;
        List<SparringDummy> dummies = dummies(context);
        for (SparringDummy dummy : dummies) {
            if (dummy.removeMove(index - 1) != null) {
                removed++;
            }
        }
        int count = removed;
        context.getSource().sendSuccess(() -> Component.literal("[bhgame] ลบท่าที่ " + index + " จากหุ่นฝึก "
                + count + "/" + dummies.size() + " ตัว"), true);
        return count;
    }

    private static int target(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Entity entity = EntityArgument.getEntity(context, "entity");
        if (!(entity instanceof LivingEntity living)) {
            throw NOT_LIVING.create();
        }
        return apply(context, dummy -> {
            if (dummy != living) {
                dummy.targetFixed(living);
            }
        }, "ตีเป้าหมาย " + entity.getDisplayName().getString() + " ตลอด");
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        for (SparringDummy dummy : dummies(context)) {
            String target = switch (dummy.targetMode()) {
                case NEAREST_PLAYER -> "ผู้เล่นที่ใกล้ที่สุด";
                case NONE -> "ไม่หาเอง";
                case FIXED -> "ตายตัว " + dummy.fixedTarget();
            };
            source.sendSuccess(() -> Component.literal("[bhgame] หุ่นฝึก #" + dummy.getId() + " @ "
                    + dummy.blockPosition().toShortString() + " | " + dummy.order().name().toLowerCase(Locale.ROOT)
                    + " | พัก " + dummy.pauseTicks() + "t | ระยะ " + dummy.attackRange()
                    + " | ไล่: " + dummy.chaseMode().name().toLowerCase(Locale.ROOT)
                    + " | ความเร็วท่า x" + dummy.animationSpeed()
                    + (dummy.stopBelow() > 0F ? " | หยุดเมื่อเป้าหมาย < " + dummy.stopBelow() + "%" : "")
                    + " | เป้าหมาย: " + target), false);
            for (SparringCommands.Event event : SparringCommands.Event.values()) {
                String command = dummy.commands().get(event);
                if (!command.isEmpty()) {
                    source.sendSuccess(() -> Component.literal("  " + event.key() + ": /" + command), false);
                }
            }
            List<SparringMove> moves = dummy.moves();
            if (moves.isEmpty()) {
                source.sendSuccess(() -> Component.literal("  (ยังไม่ตั้งท่า ใช้หมัดพื้นฐานของ Epic Fight)"), false);
            }
            for (int i = 0; i < moves.size(); i++) {
                String line = "  " + (i + 1) + ". " + moves.get(i);
                source.sendSuccess(() -> Component.literal(line), false);
            }
            List<SparringPattern> patterns = dummy.patterns();
            for (int i = 0; i < patterns.size(); i++) {
                SparringPattern pattern = patterns.get(i);
                String line = "  pattern " + (i + 1) + ": " + pattern.describe() + ", range " + pattern.range()
                        + ", " + pattern.moves().size() + " ท่า (แก้ด้วยไม้สั่งหุ่น)";
                source.sendSuccess(() -> Component.literal(line), false);
            }
        }
        return 1;
    }

    private static int apply(CommandContext<CommandSourceStack> context, Consumer<SparringDummy> change, String done)
            throws CommandSyntaxException {
        List<SparringDummy> dummies = dummies(context);
        dummies.forEach(change);
        context.getSource().sendSuccess(() -> Component.literal("[bhgame] " + done + " (หุ่นฝึก "
                + dummies.size() + " ตัว)"), true);
        return dummies.size();
    }

    private static List<SparringDummy> dummies(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        List<SparringDummy> dummies = new ArrayList<>();
        for (Entity entity : EntityArgument.getEntities(context, "dummies")) {
            if (entity instanceof SparringDummy dummy) {
                dummies.add(dummy);
            }
        }
        if (dummies.isEmpty()) {
            throw NO_DUMMY.create();
        }
        return dummies;
    }

    private static ResourceLocation checked(ResourceLocation animation) throws CommandSyntaxException {
        if (!EpicFightCompat.animationExists(animation)) {
            throw UNKNOWN_ANIMATION.create(animation);
        }
        return animation;
    }

    private SparringCommand() {
    }
}
