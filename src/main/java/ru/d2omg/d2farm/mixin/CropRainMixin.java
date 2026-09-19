package ru.d2omg.d2farm.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.d2omg.d2farm.D2Farm;
import ru.d2omg.d2farm.FarmRules;

@Mixin(CropBlock.class)
public abstract class CropRainMixin {
    @Redirect(method = "randomTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/random/Random;nextInt(I)I"))
    private int d2farm$rainGrowth(Random random, int bound, BlockState state, ServerWorld world, BlockPos pos, Random tickRandom) {
        int roll = random.nextInt(bound);
        if (roll != 0 && D2Farm.rainBonus(world, pos)
                && random.nextDouble() < FarmRules.extraGrowthChance(bound)) return 0;
        return roll;
    }
}
