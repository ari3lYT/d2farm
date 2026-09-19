package ru.d2omg.d2farm.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.d2omg.d2farm.FarmItems;

@Mixin(FarmlandBlock.class)
public abstract class FarmlandMixin {
    @Inject(method = "onLandedUpon", at = @At("HEAD"), cancellable = true)
    private void d2farm$gentleStep(World world, BlockState state, BlockPos pos, Entity entity, double distance, CallbackInfo ci) {
        if (!world.isClient() && world.getRegistryKey().equals(World.OVERWORLD) && entity instanceof PlayerEntity player
                && (FarmItems.kind(player.getMainHandStack()) == FarmItems.Kind.GARDEN
                || FarmItems.kind(player.getOffHandStack()) == FarmItems.Kind.GARDEN)) {
            // Preserve fall damage; only trampling is suppressed.
            entity.handleFallDamage(distance, 1f, world.getDamageSources().fall());
            ci.cancel();
        }
    }
}
