package com.pizzapizza.passiveskilltreecustomtreeselect;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

@EventBusSubscriber(modid = PassiveSkillTreeCustomTreeSelect.MODID, value = Dist.CLIENT)
public class TreeMenuCommands {
    private static boolean openEditorNextTick = false;

    @SubscribeEvent
    public static void registerCommands(RegisterClientCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> command =
                Commands.literal("customtreemenu")
                        .then(Commands.literal("editor")
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> {
                                    openEditorNextTick = true;
                                    return 1;
                                }));
        event.getDispatcher().register(command);
    }

    // Same delayed-open trick the base mod uses for its own editor command
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (openEditorNextTick) {
            openEditorNextTick = false;
            Minecraft.getInstance().setScreen(new TreeMenuEditorScreen());
        }
    }
}