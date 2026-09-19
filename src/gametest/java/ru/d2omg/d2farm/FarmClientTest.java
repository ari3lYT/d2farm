package ru.d2omg.d2farm;

import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameMode;

public final class FarmClientTest implements FabricClientGameTest {
    private static final BlockPos SOIL = new BlockPos(0, 64, 0);
    private static boolean deny;
    private int checks;

    @Override
    public void runTest(ClientGameTestContext context) {
        if (Boolean.getBoolean("d2farm.external")) return;
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> deny ? ActionResult.FAIL : ActionResult.PASS);
        TestWorldSave save;
        try (var game = context.worldBuilder().create()) {
            save = game.getWorldSave();
            var server = game.getServer();
            server.runCommand("gamerule minecraft:random_tick_speed 0");
            server.runCommand("time set noon");
            server.runOnServer(s -> {
                var world = s.getOverworld();
                var p = s.getPlayerManager().getPlayerList().getFirst();
                check(p.getName().getString().equals("LoerVoid"), "offline client account LoerVoid");
                p.changeGameMode(GameMode.SURVIVAL);
                for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
                    world.setBlockState(new BlockPos(x, 63, z), Blocks.STONE.getDefaultState());
                    world.setBlockState(new BlockPos(x, 64, z), Blocks.DIRT.getDefaultState());
                    for (int y = 65; y <= 69; y++) world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState());
                }
                p.requestTeleport(.5, 65, -2.5);
            });
            context.waitTicks(5);
            game.getClientWorld().waitForChunksDownload();
            equip(context, server, FarmItems.create(FarmItems.Kind.SOWER), new ItemStack(Items.WHEAT_SEEDS, 20));
            click(context, SOIL);
            server.runOnServer(s -> {
                var w = s.getOverworld();
                var p = s.getPlayerManager().getPlayerList().getFirst();
                check(count(w, Blocks.FARMLAND, 0) == 9, "sower tills nine blocks");
                check(count(w, Blocks.WHEAT, 1) == 9, "sower plants nine crops");
                check(p.getOffHandStack().getCount() == 11, "planting consumes nine seeds");
                check(p.getMainHandStack().getDamage() == 9, "nine blocks cost nine durability");
            });
            click(context, SOIL);
            server.runOnServer(s -> check(s.getPlayerManager().getPlayerList().getFirst().getMainHandStack().getDamage() == 9,
                    "repeated sow on occupied beds consumes nothing"));

            server.runOnServer(s -> {
                var w = s.getOverworld();
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
                    w.setBlockState(SOIL.add(x, 1, z), ((CropBlock)Blocks.WHEAT).withAge(7));
                w.setBlockState(SOIL.add(-1, 1, -1), ((CropBlock)Blocks.WHEAT).withAge(2));
                w.setBlockState(SOIL.add(1, 1, 1), ((CropBlock)Blocks.CARROTS).withAge(7));
            });
            equip(context, server, FarmItems.create(FarmItems.Kind.REAPER), ItemStack.EMPTY);
            click(context, SOIL.up());
            server.runOnServer(s -> {
                var w = s.getOverworld();
                var p = s.getPlayerManager().getPlayerList().getFirst();
                check(p.getMainHandStack().getDamage() == 7, "reaper processes seven eligible plants");
                check(((CropBlock)Blocks.WHEAT).getAge(w.getBlockState(SOIL.up())) == 0, "mature crop replanted");
                check(((CropBlock)Blocks.WHEAT).getAge(w.getBlockState(SOIL.add(-1, 1, -1))) == 2, "immature crop untouched");
                check(((CropBlock)Blocks.CARROTS).isMature(w.getBlockState(SOIL.add(1, 1, 1))), "other crop type untouched");
                int wheat = w.getEntitiesByClass(ItemEntity.class, new Box(SOIL).expand(5), e -> e.getStack().isOf(Items.WHEAT))
                        .stream().mapToInt(e -> e.getStack().getCount()).sum();
                check(wheat == 7, "normal loot: seven wheat from seven mature plants");
            });
            click(context, SOIL.up());
            server.runOnServer(s -> check(s.getPlayerManager().getPlayerList().getFirst().getMainHandStack().getDamage() == 7,
                    "double click cannot harvest seedlings"));

            equip(context, server, FarmItems.create(FarmItems.Kind.COMPOST), ItemStack.EMPTY);
            click(context, SOIL.up());
            server.runOnServer(s -> {
                check(SoilState.get(s.getOverworld()).charges(SOIL) == 5, "compost starts with five harvests");
                check(FarmItems.kind(s.getPlayerManager().getPlayerList().getFirst().getMainHandStack()) != FarmItems.Kind.COMPOST,
                        "compost item consumed (ordinary crop drops may fill the empty hand)");
            });
            equip(context, server, FarmItems.create(FarmItems.Kind.COMPOST), ItemStack.EMPTY);
            click(context, SOIL.up());
            server.runOnServer(s -> check(s.getPlayerManager().getPlayerList().getFirst().getMainHandStack().getCount() == 1,
                    "repeat compost does not waste item or stack bonus"));

            equip(context, server, FarmItems.create(FarmItems.Kind.GARDEN), ItemStack.EMPTY);
            context.getInput().holdKey(o -> o.sneakKey);
            context.waitTicks(2);
            click(context, SOIL.up());
            context.takeScreenshot("d2farm-soil-inspection");
            context.getInput().releaseKey(o -> o.sneakKey);
            context.waitTicks(2);
            server.runOnServer(s -> {
                var w = s.getOverworld();
                check(SoilState.get(w).charges(SOIL) == 5, "inspection consumes no charge");
                w.setBlockState(SOIL.up(), ((CropBlock)Blocks.WHEAT).withAge(7));
                w.setBlockState(SOIL.add(0, 1, 1), ((CropBlock)Blocks.WHEAT).withAge(7));
            });
            click(context, SOIL.up());
            server.runOnServer(s -> {
                var w = s.getOverworld();
                check(SoilState.get(w).charges(SOIL) == 4, "manual harvest consumes one compost charge");
                check(((CropBlock)Blocks.WHEAT).isMature(w.getBlockState(SOIL.add(0, 1, 1))), "garden hoe does not harvest neighbors");
                var p = s.getPlayerManager().getPlayerList().getFirst();
                p.setHealth(20);
                Blocks.FARMLAND.onLandedUpon(w, w.getBlockState(SOIL), SOIL, p, 5);
                check(w.getBlockState(SOIL).isOf(Blocks.FARMLAND), "garden hoe prevents own trampling");
                check(p.getHealth() < 20, "garden hoe does not cancel fall damage");
            });

            server.runOnServer(s -> {
                s.getOverworld().setBlockState(SOIL.up(), ((CropBlock)Blocks.WHEAT).withAge(7));
                deny = true;
            });
            click(context, SOIL.up());
            server.runOnServer(s -> {
                check(((CropBlock)Blocks.WHEAT).isMature(s.getOverworld().getBlockState(SOIL.up())), "third-party interaction denial respected");
                deny = false;
                s.getPlayerManager().getPlayerList().getFirst().changeGameMode(GameMode.ADVENTURE);
            });
            click(context, SOIL.up());
            server.runOnServer(s -> {
                check(((CropBlock)Blocks.WHEAT).isMature(s.getOverworld().getBlockState(SOIL.up())), "adventure mode cannot harvest");
                s.getPlayerManager().getPlayerList().getFirst().changeGameMode(GameMode.SURVIVAL);
                checkRecipes(s.getOverworld());
            });

            // Exercise every supported crop through actual client interaction packets.
            for (Block crop : List.of(Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS)) {
                server.runOnServer(s -> s.getOverworld().setBlockState(SOIL.up(), ((CropBlock)crop).withAge(((CropBlock)crop).getMaxAge())));
                equip(context, server, FarmItems.create(FarmItems.Kind.GARDEN), ItemStack.EMPTY);
                click(context, SOIL.up());
                server.runOnServer(s -> {
                    var state = s.getOverworld().getBlockState(SOIL.up());
                    check(state.isOf(crop) && ((CropBlock)crop).getAge(state) == 0, "harvest and replant " + crop);
                    check(s.getPlayerManager().getPlayerList().getFirst().getMainHandStack().getDamage() == 1,
                            "single-crop durability " + crop);
                });
            }
            server.runOnServer(s -> {
                var w = s.getOverworld();
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                    w.setBlockState(SOIL.add(x, 1, z), Blocks.AIR.getDefaultState());
                    w.setBlockState(SOIL.add(x, 0, z), Blocks.DIRT.getDefaultState());
                }
            });
            equip(context, server, FarmItems.create(FarmItems.Kind.SOWER), ItemStack.EMPTY);
            context.getInput().holdKey(o -> o.sneakKey);
            context.waitTicks(2);
            click(context, SOIL);
            context.getInput().releaseKey(o -> o.sneakKey);
            context.waitTicks(2);
            server.runOnServer(s -> {
                check(count(s.getOverworld(), Blocks.FARMLAND, 0) == 1, "sneaking sower works on one block");
                check(count(s.getOverworld(), Blocks.WHEAT, 1) == 0, "no free seeds when offhand empty");
            });
            ItemStack fragile = FarmItems.create(FarmItems.Kind.SOWER);
            fragile.setDamage(fragile.getMaxDamage() - 1);
            equip(context, server, fragile, new ItemStack(Items.WHEAT_SEEDS, 20));
            click(context, SOIL);
            server.runOnServer(s -> {
                check(count(s.getOverworld(), Blocks.WHEAT, 1) == 1, "area action stops when tool breaks");
                check(count(s.getOverworld(), Blocks.FARMLAND, 0) == 1, "broken tool cannot till remaining eight blocks");
            });
            // A renamed vanilla hoe must not gain custom powers.
            ItemStack ordinaryHoe = new ItemStack(Items.DIAMOND_HOE);
            ordinaryHoe.set(DataComponentTypes.CUSTOM_NAME, net.minecraft.text.Text.literal("Жатвенная мотыга"));
            check(FarmItems.kind(ordinaryHoe) == null, "anvil or /name cannot forge a farming tool");
            server.runOnServer(s -> {
                var w = s.getOverworld();
                w.setBlockState(SOIL.up(), ((CropBlock)Blocks.WHEAT).withAge(7));
                SoilState.get(w).prepare(SOIL);
            });
            equip(context, server, ordinaryHoe, ItemStack.EMPTY);
            click(context, SOIL.up());
            server.runOnServer(s -> check(((CropBlock)Blocks.WHEAT).isMature(s.getOverworld().getBlockState(SOIL.up())),
                    "ordinary renamed hoe keeps vanilla right-click behavior"));

            server.runOnServer(s -> {
                var w = s.getOverworld();
                w.setWeather(0, 6000, true, false);
                w.setRainGradient(1);
                w.setBlockState(SOIL.up(), Blocks.WHEAT.getDefaultState());
                SoilState.get(w).prepare(SOIL);
                check(D2Farm.rainBonus(w, SOIL.up()), "rain bonus on open prepared bed");
                w.setBlockState(SOIL.east(), Blocks.FARMLAND.getDefaultState());
                w.setBlockState(SOIL.east().up(), Blocks.WHEAT.getDefaultState());
                check(!D2Farm.rainBonus(w, SOIL.east().up()), "ordinary planted bed has no rain bonus");
                w.setBlockState(SOIL.up(3), Blocks.GLASS.getDefaultState());
                check(!D2Farm.rainBonus(w, SOIL.up()), "glass roof blocks rain bonus");
                w.setBlockState(SOIL.up(3), Blocks.AIR.getDefaultState());
                checkRainGrowth(w);
                w.setWeather(6000, 0, false, false);
                w.setRainGradient(0);
                check(!D2Farm.rainBonus(w, SOIL.up()), "dry weather has no bonus");
                w.setBlockState(SOIL.up(), ((CropBlock)Blocks.WHEAT).withAge(7));
                w.setBlockState(SOIL.up(), Blocks.WATER.getDefaultState());
                check(SoilState.get(w).charges(SOIL) == 4, "water removal consumes one harvest charge without auto-replant");
                w.setBlockState(SOIL.up(), Blocks.AIR.getDefaultState());
                SoilState.get(w).prepare(SOIL);
                w.setBlockState(SOIL, Blocks.DIRT.getDefaultState());
                w.setBlockState(SOIL, Blocks.FARMLAND.getDefaultState());
                check(SoilState.get(w).charges(SOIL) == 0, "removed farmland loses its bonus permanently");
                w.setBlockState(SOIL.up(), Blocks.WHEAT.getDefaultState());
                SoilState.get(w).prepare(SOIL);
                SoilState.get(w).harvest(SOIL);
            });
            context.runOnClient(client -> { client.player.setYaw(0); client.player.setPitch(28); });
            context.waitTicks(3);
            context.takeScreenshot("d2farm-headless-field");
        }
        try (var reopened = save.open()) {
            reopened.getServer().runOnServer(s -> check(SoilState.get(s.getOverworld()).charges(SOIL) == 4,
                    "four charges persist across full world close and reopen"));
        }
        D2Farm.LOGGER.info("D2FARM_HEADLESS_PASS checks={}", checks);
    }

    private void checkRecipes(ServerWorld world) {
        List<List<ItemStack>> ingredients = List.of(
                List.of(new ItemStack(Items.IRON_HOE), new ItemStack(Items.WHEAT_SEEDS), new ItemStack(Items.BONE_MEAL)),
                List.of(new ItemStack(Items.DIAMOND_HOE), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.HAY_BLOCK)),
                List.of(new ItemStack(Items.GOLDEN_HOE), new ItemStack(Items.FEATHER), new ItemStack(Items.MOSS_BLOCK)),
                List.of(new ItemStack(Items.WHEAT_SEEDS), new ItemStack(Items.ROTTEN_FLESH), new ItemStack(Items.BONE_MEAL), new ItemStack(Items.DIRT)));
        FarmItems.Kind[] kinds = FarmItems.Kind.values();
        for (int i = 0; i < ingredients.size(); i++) {
            var stacks = ingredients.get(i);
            if (i < 3) stacks.getFirst().setDamage(17);
            var input = CraftingRecipeInput.create(stacks.size(), 1, stacks);
            var recipe = world.getServer().getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, world).orElseThrow();
            ItemStack output = recipe.value().craft(input, world.getRegistryManager());
            check(FarmItems.kind(output) == kinds[i], "recipe crafts " + kinds[i]);
            if (i < 3) check(output.getDamage() == 17, "upgrading preserves tool damage " + kinds[i]);
            check(output.get(DataComponentTypes.ITEM_NAME) != null, "recipe has translated name " + kinds[i]);
        }
    }

    private void checkRainGrowth(ServerWorld world) {
        BlockPos control = SOIL.east(3);
        world.setBlockState(SOIL, Blocks.FARMLAND.getDefaultState().with(FarmlandBlock.MOISTURE, 7));
        world.setBlockState(control, Blocks.FARMLAND.getDefaultState().with(FarmlandBlock.MOISTURE, 7));
        // Isolate both crops from neighbor-crop/moisture effects for equal vanilla probability.
        BlockPos test = SOIL.up();
        for (int x = -1; x <= 4; x++) for (int z = -1; z <= 1; z++) {
            BlockPos p = SOIL.add(x, 0, z);
            world.setBlockState(p, Blocks.FARMLAND.getDefaultState().with(FarmlandBlock.MOISTURE, 7));
            world.setBlockState(p.up(), Blocks.AIR.getDefaultState());
        }
        BlockState initial = Blocks.WHEAT.getDefaultState();
        Random randomA = Random.create(81397);
        Random randomB = Random.create(81397);
        int boosted = 0, ordinary = 0;
        for (int i = 0; i < 30000; i++) {
            world.setBlockState(test, initial, Block.NOTIFY_LISTENERS);
            world.setBlockState(control.up(), initial, Block.NOTIFY_LISTENERS);
            initial.randomTick(world, test, randomA);
            initial.randomTick(world, control.up(), randomB);
            if (((CropBlock)Blocks.WHEAT).getAge(world.getBlockState(test)) > 0) boosted++;
            if (((CropBlock)Blocks.WHEAT).getAge(world.getBlockState(control.up())) > 0) ordinary++;
        }
        double ratio = (double)boosted / ordinary;
        D2Farm.LOGGER.info("RAIN_SAMPLES boosted={} ordinary={} ratio={}", boosted, ordinary, ratio);
        check(ratio > 1.40 && ratio < 1.60, "real crop ticks show approximately 50 percent rain bonus");
        check(SoilState.get(world).charges(SOIL) == 5, "growth rolls alone do not consume harvest charges");
    }

    private static int count(ServerWorld world, Block type, int yOffset) {
        int count = 0;
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            if (world.getBlockState(SOIL.add(x, yOffset, z)).isOf(type)) count++;
        return count;
    }

    private static void equip(ClientGameTestContext context, TestServerContext server, ItemStack main, ItemStack off) {
        server.runOnServer(s -> {
            ServerPlayerEntity player = s.getPlayerManager().getPlayerList().getFirst();
            player.getInventory().setSelectedSlot(0);
            player.setStackInHand(Hand.MAIN_HAND, main);
            player.setStackInHand(Hand.OFF_HAND, off);
            player.currentScreenHandler.sendContentUpdates();
        });
        context.waitTicks(3);
    }

    private static void click(ClientGameTestContext context, BlockPos pos) {
        context.runOnClient(client -> client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND,
                new BlockHitResult(Vec3d.ofCenter(pos).add(0, .4, 0), Direction.UP, pos, false)));
        context.waitTicks(3);
    }

    private void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks++;
        D2Farm.LOGGER.info("PASS {}", description);
    }
}
