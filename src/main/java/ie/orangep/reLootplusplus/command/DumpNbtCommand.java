package ie.orangep.reLootplusplus.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class DumpNbtCommand {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private DumpNbtCommand() {
    }

    public static void register(CommandDispatcherWrapper dispatcher) {
        dispatcher.register(CommandManager.literal("dumpnbt")
            .requires(source -> source.hasPermissionLevel(0))
            .then(CommandManager.literal("item")
                .executes(ctx -> run(ctx, Target.ITEM, null, null))
                .then(CommandManager.argument("output", StringArgumentType.word())
                    .executes(ctx -> run(ctx, Target.ITEM, StringArgumentType.getString(ctx, "output"), null))
                    .then(CommandManager.argument("path", StringArgumentType.greedyString())
                        .executes(ctx -> run(ctx, Target.ITEM, StringArgumentType.getString(ctx, "output"),
                            StringArgumentType.getString(ctx, "path"))))))
            .then(CommandManager.literal("block")
                .executes(ctx -> run(ctx, Target.BLOCK, null, null))
                .then(CommandManager.argument("output", StringArgumentType.word())
                    .executes(ctx -> run(ctx, Target.BLOCK, StringArgumentType.getString(ctx, "output"), null))
                    .then(CommandManager.argument("path", StringArgumentType.greedyString())
                        .executes(ctx -> run(ctx, Target.BLOCK, StringArgumentType.getString(ctx, "output"),
                            StringArgumentType.getString(ctx, "path"))))))
        );
    }

    private static int run(CommandContext<ServerCommandSource> ctx, Target target, String output, String pathRaw) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player;
        try {
            player = source.getPlayer();
        } catch (Exception e) {
            source.sendError(new LiteralText("dumpnbt: only players can use this command"));
            return 0;
        }
        NbtCompound nbt = target == Target.ITEM ? dumpItem(player) : dumpBlock(player);
        if (nbt == null) {
            source.sendError(new LiteralText("dumpnbt: nothing to dump"));
            return 0;
        }
        String text = nbt.toString();
        OutputMode mode = OutputMode.from(output);
        if (mode == OutputMode.CLIPBOARD) {
            boolean ok = tryCopyToClipboard(text);
            source.sendFeedback(new LiteralText(ok ? "dumpnbt: copied to clipboard" : "dumpnbt: clipboard not available"), false);
        } else if (mode == OutputMode.FILE) {
            Path file = resolveDumpPath(target, pathRaw);
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, text, StandardCharsets.UTF_8);
                source.sendFeedback(new LiteralText("dumpnbt: wrote to " + file), false);
            } catch (Exception e) {
                source.sendError(new LiteralText("dumpnbt: failed to write file: " + e.getMessage()));
            }
        }

        if (text.length() <= 512) {
            source.sendFeedback(new LiteralText("dumpnbt: " + text), false);
        } else {
            String head = text.substring(0, 512);
            source.sendFeedback(new LiteralText("dumpnbt: " + head + " ... (" + text.length() + " chars)"), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static NbtCompound dumpItem(ServerPlayerEntity player) {
        var stack = player.getMainHandStack();
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.writeNbt(new NbtCompound());
    }

    private static NbtCompound dumpBlock(ServerPlayerEntity player) {
        HitResult hit = player.raycast(6.0, 0.0f, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockHitResult bhr = (BlockHitResult) hit;
        BlockPos pos = bhr.getBlockPos();
        BlockState state = player.getWorld().getBlockState(pos);
        BlockEntity be = player.getWorld().getBlockEntity(pos);
        NbtCompound out = new NbtCompound();
        out.putString("id", Registry.BLOCK.getId(state.getBlock()).toString());
        out.putString("state", state.toString());
        out.putInt("x", pos.getX());
        out.putInt("y", pos.getY());
        out.putInt("z", pos.getZ());
        if (be != null) {
            out.put("blockEntity", be.createNbt());
        }
        return out;
    }

    private static Path resolveDumpPath(Target target, String pathRaw) {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        if (pathRaw != null && !pathRaw.isBlank()) {
            Path path = Path.of(pathRaw);
            if (!path.isAbsolute()) {
                return gameDir.resolve(pathRaw);
            }
            return path;
        }
        String name = "dumpnbt-" + target.name().toLowerCase() + "-" + TS.format(LocalDateTime.now()) + ".snbt";
        return gameDir.resolve("logs").resolve("re_lpp").resolve("dumps").resolve(name);
    }

    private static boolean tryCopyToClipboard(String text) {
        try {
            Class<?> clientCls = Class.forName("net.minecraft.client.MinecraftClient");
            Object client = clientCls.getMethod("getInstance").invoke(null);
            if (client == null) {
                return false;
            }
            Object keyboard = clientCls.getMethod("getKeyboard").invoke(client);
            if (keyboard == null) {
                return false;
            }
            keyboard.getClass().getMethod("setClipboard", String.class).invoke(keyboard, text);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private enum Target {
        ITEM,
        BLOCK
    }

    private enum OutputMode {
        CHAT,
        CLIPBOARD,
        FILE;

        static OutputMode from(String raw) {
            if (raw == null) {
                return CHAT;
            }
            String value = raw.trim().toLowerCase();
            return switch (value) {
                case "clipboard", "clip" -> CLIPBOARD;
                case "file", "f" -> FILE;
                default -> CHAT;
            };
        }
    }

    public interface CommandDispatcherWrapper {
        void register(LiteralArgumentBuilder<ServerCommandSource> builder);
    }
}
