package ru.d2omg.d2farm.mixin;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.ShapelessRecipe;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.d2omg.d2farm.FarmItems;

@Mixin(ShapelessRecipe.class)
public abstract class FarmRecipeMixin {
    @Inject(method = "craft(Lnet/minecraft/recipe/input/CraftingRecipeInput;Lnet/minecraft/registry/RegistryWrapper$WrapperLookup;)Lnet/minecraft/item/ItemStack;",
            at = @At("RETURN"), cancellable = true)
    private void d2farm$preserveTool(CraftingRecipeInput input, RegistryWrapper.WrapperLookup registries, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = cir.getReturnValue();
        FarmItems.Kind kind = FarmItems.kind(result);
        if (kind == null || kind == FarmItems.Kind.COMPOST) return;
        for (ItemStack ingredient : input.getStacks()) {
            if (!ingredient.isOf(result.getItem())) continue;
            ItemStack upgraded = ingredient.copyWithCount(1);
            NbtCompound data = ingredient.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
            data.putString("d2farm", kind.name());
            upgraded.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
            upgraded.set(DataComponentTypes.ITEM_NAME, result.get(DataComponentTypes.ITEM_NAME));
            upgraded.set(DataComponentTypes.LORE, result.get(DataComponentTypes.LORE));
            cir.setReturnValue(upgraded);
            return;
        }
    }
}
