package ru.d2omg.d2farm;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Optional Ledger API integration without requiring Ledger on a vanilla client. */
public final class LedgerBridge {
    private static Object factory;
    private static Object api;
    private static Method change;
    private static Method log;
    private static boolean healthy = true;

    private LedgerBridge() { }

    public static void initialize() {
        factory = null;
        api = null;
        healthy = true;
        if (!FabricLoader.getInstance().isModLoaded("ledger")) return;
        try {
            Class<?> factoryClass = Class.forName("com.github.quiltservertools.ledger.actionutils.ActionFactory");
            factory = factoryClass.getField("INSTANCE").get(null);
            change = factoryClass.getMethod("blockChangeAction", World.class, BlockPos.class, BlockState.class,
                    BlockState.class, BlockEntity.class, String.class, PlayerEntity.class);
            api = Class.forName("com.github.quiltservertools.ledger.Ledger").getMethod("getApi").invoke(null);
            log = Class.forName("com.github.quiltservertools.ledger.api.LedgerApi").getMethod("logAction",
                    Class.forName("com.github.quiltservertools.ledger.actions.ActionType"));
            D2Farm.LOGGER.info("Ledger block-change auditing connected");
        } catch (ReflectiveOperationException | LinkageError e) {
            healthy = false;
            D2Farm.LOGGER.error("Ledger integration failed; farming tools disabled to protect world history", e);
        }
    }

    public static boolean healthy() { return healthy; }

    public static boolean record(ServerWorld world, BlockPos pos, BlockState before, BlockState after, PlayerEntity player) {
        if (!healthy) return false;
        if (api == null) return true;
        try {
            Object action = change.invoke(factory, world, pos, before, after, null, player.getName().getString(), player);
            log.invoke(api, action);
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            healthy = false;
            D2Farm.LOGGER.error("Ledger rejected a farming action; further tool use disabled", e);
            return false;
        }
    }
}
