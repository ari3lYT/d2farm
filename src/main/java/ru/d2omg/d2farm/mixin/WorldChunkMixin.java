package ru.d2omg.d2farm.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.d2omg.d2farm.D2Farm;
import ru.d2omg.d2farm.SoilState;

@Mixin(WorldChunk.class)
public abstract class WorldChunkMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void d2farm$soilLifecycle(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> cir) {
        BlockState old = cir.getReturnValue();
        if (old == null || !(((WorldChunk)(Object)this).getWorld() instanceof ServerWorld world)) return;
        if (old.isOf(Blocks.FARMLAND) && !state.isOf(Blocks.FARMLAND)) SoilState.get(world).remove(pos);
        if (D2Farm.supported(old) && ((CropBlock)old.getBlock()).isMature(old)
                && (!state.isOf(old.getBlock()) || !((CropBlock)old.getBlock()).isMature(state))) {
            SoilState.get(world).harvest(pos.down());
        }
    }
}
