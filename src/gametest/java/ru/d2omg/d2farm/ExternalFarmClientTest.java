package ru.d2omg.d2farm;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class ExternalFarmClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        if (!Boolean.getBoolean("d2farm.external")) return;
        context.runOnClient(client -> ConnectScreen.connect(new TitleScreen(), client,
                ServerAddress.parse("127.0.0.1:25585"), new ServerInfo("D2Farm local QA", "127.0.0.1:25585", ServerInfo.ServerType.OTHER),
                false, null));
        context.waitFor(client -> client.player != null && client.world != null
                && client.player.getMainHandStack().isOf(Items.IRON_HOE), 1200);
        context.waitTicks(25);
        click(context, new BlockPos(0, 64, 0));
        context.waitFor(client -> client.player.getMainHandStack().getDamage() == 9, 300);
        context.runOnClient(client -> client.getNetworkHandler().sendChatCommand("d2farmqa reaper"));
        context.waitFor(client -> client.player.getMainHandStack().isOf(Items.DIAMOND_HOE), 300);
        context.waitTicks(3);
        click(context, new BlockPos(0, 65, 0));
        context.waitFor(client -> client.player.getMainHandStack().getDamage() == 9, 300);
        context.runOnClient(client -> client.getNetworkHandler().sendChatCommand("d2farmqa verify"));
        context.waitFor(client -> client.player.getMainHandStack().getName().getString().equals("QA PASSED"), 300);
        context.runOnClient(client -> { client.player.setYaw(0); client.player.setPitch(25); });
        context.waitTicks(5);
        context.takeScreenshot("d2farm-dedicated-ledger-pass");
        D2Farm.LOGGER.info("D2FARM_EXTERNAL_CLIENT_PASS");
        context.runOnClient(client -> client.disconnect(new TitleScreen(), false));
        context.waitFor(client -> client.world == null && client.player == null);
    }
    private static void click(ClientGameTestContext context, BlockPos pos) {
        context.runOnClient(client -> client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND,
                new BlockHitResult(Vec3d.ofCenter(pos).add(0, .4, 0), Direction.UP, pos, false)));
        context.waitTicks(3);
    }
}
