package ru.d2omg.d2farm;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

/** Test-only fixture. Not included in the distributable mod JAR. */
public final class QaServer implements ModInitializer {
    @Override public void onInitialize() {
        if (!Boolean.getBoolean("d2farm.qa")) return;
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var player = handler.player;
            if (!player.getName().getString().equals("LoerVoid")) {
                handler.disconnect(Text.literal("Local QA only"));
                return;
            }
            var w = server.getOverworld();
            for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
                w.setBlockState(new BlockPos(x, 63, z), Blocks.STONE.getDefaultState());
                w.setBlockState(new BlockPos(x, 64, z), Blocks.DIRT.getDefaultState());
                for (int y = 65; y < 70; y++) w.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState());
            }
            player.changeGameMode(GameMode.SURVIVAL);
            player.requestTeleport(.5, 65, -2.5);
            player.setStackInHand(Hand.MAIN_HAND, FarmItems.create(FarmItems.Kind.SOWER));
            player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.WHEAT_SEEDS, 20));
            player.currentScreenHandler.sendContentUpdates();
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
                CommandManager.literal("d2farmqa").requires(source -> source.getEntity() != null
                        && source.getName().equals("LoerVoid"))
                .then(CommandManager.argument("stage", StringArgumentType.word()).executes(ctx -> {
                    var player = ctx.getSource().getPlayerOrThrow();
                    var world = ctx.getSource().getWorld();
                    String stage = StringArgumentType.getString(ctx, "stage");
                    if (!LedgerBridge.healthy()) throw new AssertionError("Ledger adapter unhealthy");
                    if (stage.equals("reaper")) {
                        if (player.getMainHandStack().getDamage() != 9) throw new AssertionError("sowing cost");
                        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                            BlockPos pos = new BlockPos(x, 65, z);
                            if (!world.getBlockState(pos).isOf(Blocks.WHEAT)) throw new AssertionError("missing planted crop");
                            world.setBlockState(pos, ((CropBlock)Blocks.WHEAT).withAge(7));
                        }
                        player.setStackInHand(Hand.MAIN_HAND, FarmItems.create(FarmItems.Kind.REAPER));
                        player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
                    } else if (stage.equals("verify")) {
                        if (player.getMainHandStack().getDamage() != 9) throw new AssertionError("harvesting cost");
                        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                            BlockPos pos = new BlockPos(x, 65, z);
                            if (!world.getBlockState(pos).isOf(Blocks.WHEAT)
                                    || ((CropBlock)Blocks.WHEAT).getAge(world.getBlockState(pos)) != 0)
                                throw new AssertionError("missing replanted crop");
                        }
                        // Exercise the actual Ledger action's rollback and restore on a separate block.
                        try {
                            Class<?> factoryClass = Class.forName("com.github.quiltservertools.ledger.actionutils.ActionFactory");
                            var factory = factoryClass.getField("INSTANCE").get(null);
                            BlockPos pos = new BlockPos(4, 64, 4);
                            var old = Blocks.DIRT.getDefaultState();
                            var next = Blocks.FARMLAND.getDefaultState();
                            Object action = factoryClass.getMethod("blockChangeAction", net.minecraft.world.World.class,
                                    BlockPos.class, net.minecraft.block.BlockState.class, net.minecraft.block.BlockState.class,
                                    net.minecraft.block.entity.BlockEntity.class, String.class, net.minecraft.entity.player.PlayerEntity.class)
                                    .invoke(factory, world, pos, old, next, null, "LoerVoid", player);
                            world.setBlockState(pos, next);
                            Class<?> type = Class.forName("com.github.quiltservertools.ledger.actions.ActionType");
                            type.getMethod("rollback", net.minecraft.server.MinecraftServer.class).invoke(action, world.getServer());
                            if (!world.getBlockState(pos).isOf(Blocks.DIRT)) throw new AssertionError("Ledger rollback");
                            type.getMethod("restore", net.minecraft.server.MinecraftServer.class).invoke(action, world.getServer());
                            if (!world.getBlockState(pos).isOf(Blocks.FARMLAND)) throw new AssertionError("Ledger restore");
                        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
                        ItemStack marker = new ItemStack(Items.COMPASS);
                        marker.set(DataComponentTypes.CUSTOM_NAME, Text.literal("QA PASSED"));
                        player.setStackInHand(Hand.MAIN_HAND, marker);
                        D2Farm.LOGGER.info("D2FARM_DEDICATED_LEDGER_PASS: player packets, sow, harvest, rollback, restore");
                    } else throw new IllegalArgumentException("Unknown QA stage");
                    player.currentScreenHandler.sendContentUpdates();
                    return 1;
                }))));
    }
}
