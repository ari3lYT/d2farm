package ru.d2omg.d2farm;

import java.util.List;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class FarmItems {
    public enum Kind { SOWER, REAPER, GARDEN, COMPOST }
    private FarmItems() { }

    public static Kind kind(ItemStack stack) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null) return null;
        String key = data.copyNbt().getString("d2farm", "");
        for (Kind kind : Kind.values()) {
            if (kind.name().equals(key) && stack.isOf(base(kind))) return kind;
        }
        return null;
    }

    private static Item base(Kind kind) {
        return switch (kind) {
            case SOWER -> Items.IRON_HOE;
            case REAPER -> Items.DIAMOND_HOE;
            case GARDEN -> Items.GOLDEN_HOE;
            case COMPOST -> Items.BROWN_DYE;
        };
    }

    public static ItemStack create(Kind kind) {
        ItemStack stack = new ItemStack(base(kind));
        NbtCompound data = new NbtCompound();
        data.putString("d2farm", kind.name());
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
        String name = switch (kind) {
            case SOWER -> "Посевная мотыга";
            case REAPER -> "Жатвенная мотыга";
            case GARDEN -> "Садовая мотыга";
            case COMPOST -> "Компост";
        };
        String detail = switch (kind) {
            case SOWER -> "ПКМ: вспашка 3×3. Семена — во второй руке.";
            case REAPER -> "ПКМ: собрать и пересадить зрелые растения 3×3.";
            case GARDEN -> "ПКМ: сбор и пересадка. В руке защищает от вытаптывания.";
            case COMPOST -> "ПКМ по грядке: дождевой бонус на 5 урожаев.";
        };
        stack.set(DataComponentTypes.ITEM_NAME, Text.literal(name).formatted(Formatting.GREEN));
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(Text.literal(detail).formatted(Formatting.GRAY),
                Text.literal(kind == Kind.GARDEN ? "Shift + ПКМ: состояние почвы." :
                        kind == Kind.COMPOST ? "Без дождя рост остаётся обычным." : "Shift + ПКМ: один блок.").formatted(Formatting.DARK_GRAY))));
        return stack;
    }
}
