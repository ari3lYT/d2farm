package ru.d2omg.d2farm;

import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class D2Farm implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("D2Farm");
    private static final ThreadLocal<Boolean> CHECKING = ThreadLocal.withInitial(() -> false);

    @Override
    public void onInitialize() {
        UseBlockCallback.EVENT.register(D2Farm::use);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> LedgerBridge.initialize());
        LOGGER.info("D2Farm enabled: manual tools; rain bonus only on composted farmland");
    }

    public static boolean supported(BlockState state) {
        return state.isOf(Blocks.WHEAT) || state.isOf(Blocks.CARROTS)
                || state.isOf(Blocks.POTATOES) || state.isOf(Blocks.BEETROOTS);
    }

    public static boolean rainBonus(ServerWorld world, BlockPos cropPos) {
        return supported(world.getBlockState(cropPos)) && world.hasRain(cropPos)
                && world.getBlockState(cropPos.down()).isOf(Blocks.FARMLAND)
                && SoilState.get(world).charges(cropPos.down()) > 0;
    }

    private static ActionResult use(PlayerEntity player, World world, Hand hand, BlockHitResult hit) {
        if (CHECKING.get() || !(world instanceof ServerWorld serverWorld)
                || !(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
        ItemStack tool = player.getStackInHand(hand);
        FarmItems.Kind kind = FarmItems.kind(tool);
        if (kind == null) return ActionResult.PASS;
        if (!LedgerBridge.healthy()) {
            player.sendMessage(Text.literal("Инструменты земледелия временно недоступны: ошибка журнала изменений."), true);
            return ActionResult.FAIL;
        }
        if (!player.canModifyBlocks() || player.isSpectator()) return ActionResult.FAIL;
        // Lobby and third-party dimensions remain untouched by this survival expansion.
        if (!world.getRegistryKey().equals(World.OVERWORLD)) return ActionResult.PASS;
        if (hand != Hand.MAIN_HAND && kind != FarmItems.Kind.COMPOST) return ActionResult.PASS;
        BlockPos origin = hit.getBlockPos();
        BlockState clicked = world.getBlockState(origin);
        if (kind == FarmItems.Kind.COMPOST || (kind == FarmItems.Kind.GARDEN && player.isSneaking())) {
            BlockPos soil = supported(clicked) ? origin.down() : origin;
            if (!world.getBlockState(soil).isOf(Blocks.FARMLAND)) return ActionResult.PASS;
            if (!allowed(serverWorld, serverPlayer, hand, origin)) return ActionResult.FAIL;
            SoilState state = SoilState.get(serverWorld);
            if (kind == FarmItems.Kind.GARDEN) {
                String status = state.charges(soil) == 0 ? "Обычная грядка" :
                        "Подкормка: " + state.charges(soil) + " урожаев · " +
                        (rainBonus(serverWorld, soil.up()) ? "Дождевой бонус активен" : "Дождевой бонус не активен");
                player.sendMessage(Text.literal(status), true);
                return ActionResult.SUCCESS;
            }
            if (state.charges(soil) > 0) {
                player.sendMessage(Text.literal("Грядка уже удобрена: осталось урожаев — " + state.charges(soil)), true);
                return ActionResult.SUCCESS;
            }
            state.prepare(soil);
            if (!player.isCreative()) tool.decrement(1);
            serverWorld.spawnParticles(ParticleTypes.COMPOSTER, soil.getX() + .5, soil.getY() + 1,
                    soil.getZ() + .5, 5, .2, .1, .2, .01);
            serverWorld.playSound(null, soil, SoundEvents.BLOCK_COMPOSTER_FILL_SUCCESS, SoundCategory.BLOCKS, .6f, 1f);
            player.sendMessage(Text.literal("Подкормка внесена: 5 урожаев. Под дождём рост быстрее на 50%."), true);
            LOGGER.info("COMPOST player={} pos={}", player.getName().getString(), soil.toShortString());
            return ActionResult.SUCCESS;
        }
        if (kind == FarmItems.Kind.SOWER) {
            if (hit.getSide() == Direction.DOWN || !(clicked.isOf(Blocks.DIRT)
                    || clicked.isOf(Blocks.GRASS_BLOCK) || clicked.isOf(Blocks.FARMLAND))) return ActionResult.PASS;
        } else if (!supported(clicked)) return ActionResult.PASS;

        int radius = player.isSneaking() || kind == FarmItems.Kind.GARDEN ? 0 : 1;
        int changed = 0;
        // Process the clicked block first, then the bounded square around it.
        for (int pass = 0; pass < 2; pass++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if ((x == 0 && z == 0) != (pass == 0)) continue;
                    if (tool.isEmpty()) break;
                    BlockPos pos = origin.add(x, 0, z);
                    if (!serverWorld.isChunkLoaded(pos) || !allowed(serverWorld, serverPlayer, hand, pos)) continue;
                    boolean success = kind == FarmItems.Kind.SOWER ? sow(serverWorld, serverPlayer, hand, pos) :
                            harvest(serverWorld, serverPlayer, hand, pos, clicked.getBlock());
                    if (success) {
                        changed++;
                        tool.damage(1, player, hand);
                    }
                }
            }
        }
        if (changed > 0) {
            serverWorld.playSound(null, origin, kind == FarmItems.Kind.SOWER ? SoundEvents.ITEM_HOE_TILL :
                    SoundEvents.BLOCK_CROP_BREAK, SoundCategory.BLOCKS, .7f, 1f);
        }
        return ActionResult.SUCCESS;
    }

    private static boolean allowed(ServerWorld world, ServerPlayerEntity player, Hand hand, BlockPos pos) {
        if (!player.canModifyBlocks() || !player.canInteractWithBlockAt(pos, 0)
                || !world.canEntityModifyAt(player, pos)) return false;
        Vec3d target = Vec3d.ofCenter(pos).add(0, .4, 0);
        BlockHitResult ray = world.raycast(new RaycastContext(player.getEyePos(), target,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        if (ray.getType() != net.minecraft.util.hit.HitResult.Type.MISS && !ray.getBlockPos().equals(pos)) return false;
        CHECKING.set(true);
        try {
            ActionResult result = UseBlockCallback.EVENT.invoker().interact(player, world, hand,
                    new BlockHitResult(target, Direction.UP, pos, false));
            // Honor both protection denials and another mod claiming the interaction.
            if (result != ActionResult.PASS) return false;
            return PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(world, player, pos,
                    world.getBlockState(pos), world.getBlockEntity(pos));
        } finally {
            CHECKING.remove();
        }
    }

    private static boolean sow(ServerWorld world, ServerPlayerEntity player, Hand hand, BlockPos pos) {
        BlockState before = world.getBlockState(pos);
        if (!world.getBlockState(pos.up()).isAir()) return false;
        boolean tilled = before.isOf(Blocks.DIRT) || before.isOf(Blocks.GRASS_BLOCK);
        if (!tilled && !before.isOf(Blocks.FARMLAND)) return false;
        if (tilled) {
            BlockState after = Blocks.FARMLAND.getDefaultState();
            if (!LedgerBridge.record(world, pos, before, after, player)) return false;
            world.setBlockState(pos, after, Block.NOTIFY_ALL);
        }
        ItemStack seeds = player.getOffHandStack();
        Block crop = cropFor(seeds.getItem());
        boolean planted = !seeds.isEmpty() && crop != null && allowed(world, player, hand, pos.up())
                && LedgerBridge.record(world, pos.up(), world.getBlockState(pos.up()), crop.getDefaultState(), player);
        if (planted) {
            world.setBlockState(pos.up(), crop.getDefaultState(), Block.NOTIFY_ALL);
            if (!player.isCreative()) seeds.decrement(1);
            world.emitGameEvent(player, GameEvent.BLOCK_PLACE, pos.up());
        }
        if (tilled || planted) {
            world.emitGameEvent(player, GameEvent.BLOCK_CHANGE, pos);
            LOGGER.info("SOW player={} pos={} before={} planted={}", player.getName().getString(), pos.toShortString(), before, planted);
        }
        return tilled || planted;
    }

    private static boolean harvest(ServerWorld world, ServerPlayerEntity player, Hand hand, BlockPos pos, Block type) {
        BlockState before = world.getBlockState(pos);
        if (!before.isOf(type) || !supported(before) || !((CropBlock) type).isMature(before)) return false;
        ItemStack tool = player.getStackInHand(hand);
        if (player.isCreative()) {
            if (!LedgerBridge.record(world, pos, before, type.getDefaultState(), player)) return false;
            world.setBlockState(pos, type.getDefaultState(), Block.NOTIFY_ALL);
        } else {
            List<ItemStack> drops = Block.getDroppedStacks(before, world, pos, null, player, tool);
            Item seed = seedFor(type);
            boolean replanted = false;
            for (ItemStack drop : drops) {
                if (!replanted && drop.isOf(seed) && !drop.isEmpty()) {
                    drop.decrement(1);
                    replanted = true;
                }
            }
            BlockState after = replanted ? type.getDefaultState() : Blocks.AIR.getDefaultState();
            if (!LedgerBridge.record(world, pos, before, after, player)) return false;
            world.setBlockState(pos, after, Block.NOTIFY_ALL);
            for (ItemStack drop : drops) if (!drop.isEmpty()) Block.dropStack(world, pos, drop);
            before.onStacksDropped(world, pos, tool, true);
        }
        world.emitGameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        // This is a state change/replant, not vanilla block destruction. Emitting AFTER
        // would make Ledger append a second, incorrect crop-to-air action.
        LOGGER.info("HARVEST player={} pos={} before={} after={}", player.getName().getString(), pos.toShortString(), before, world.getBlockState(pos));
        return true;
    }

    public static Block cropFor(Item item) {
        if (item == Items.WHEAT_SEEDS) return Blocks.WHEAT;
        if (item == Items.CARROT) return Blocks.CARROTS;
        if (item == Items.POTATO) return Blocks.POTATOES;
        if (item == Items.BEETROOT_SEEDS) return Blocks.BEETROOTS;
        return null;
    }

    public static Item seedFor(Block block) {
        if (block == Blocks.WHEAT) return Items.WHEAT_SEEDS;
        if (block == Blocks.CARROTS) return Items.CARROT;
        if (block == Blocks.POTATOES) return Items.POTATO;
        return Items.BEETROOT_SEEDS;
    }
}
