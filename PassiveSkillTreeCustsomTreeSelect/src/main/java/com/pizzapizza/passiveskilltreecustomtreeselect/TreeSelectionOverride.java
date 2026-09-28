package com.pizzapizza.passiveskilltreecustomtreeselect;

import daripher.skilltree.client.screen.SkillTreeSelectionScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = PassiveSkillTreeCustomTreeSelect.MODID, value = Dist.CLIENT)
public class TreeSelectionOverride {

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getScreen() instanceof SkillTreeSelectionScreen) {
            event.setNewScreen(new CustomTreeSelectionScreen());
        }
    }
}