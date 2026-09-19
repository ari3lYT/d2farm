package ru.d2omg.d2farm;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

public final class SoilState extends PersistentState {
    private static final Codec<SoilState> CODEC = Codec.unboundedMap(Codec.STRING,
            Codec.intRange(1, FarmRules.COMPOST_HARVESTS)).xmap(SoilState::new, state -> state.beds);
    public static final PersistentStateType<SoilState> TYPE =
            new PersistentStateType<>("d2farm_soil", SoilState::new, CODEC, null);
    private final Map<String, Integer> beds;

    public SoilState() { this(Map.of()); }
    private SoilState(Map<String, Integer> beds) { this.beds = new HashMap<>(beds); }

    public static SoilState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE);
    }

    public int charges(BlockPos pos) { return beds.getOrDefault(Long.toString(pos.asLong()), 0); }

    public void prepare(BlockPos pos) {
        beds.put(Long.toString(pos.asLong()), FarmRules.COMPOST_HARVESTS);
        markDirty();
    }

    public void remove(BlockPos pos) {
        if (beds.remove(Long.toString(pos.asLong())) != null) markDirty();
    }

    public void harvest(BlockPos pos) {
        int charges = charges(pos);
        if (charges == 0) return;
        if (charges == 1) remove(pos);
        else {
            beds.put(Long.toString(pos.asLong()), FarmRules.afterHarvest(charges));
            markDirty();
        }
    }
}
