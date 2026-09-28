package com.pizzapizza.passiveskilltreecustomtreeselect.mixin;

import daripher.skilltree.client.widget.skill.SkillButton;
import daripher.skilltree.data.reloader.SkillTreesReloader;
import daripher.skilltree.skill.PassiveSkillTree;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pizzapizza.passiveskilltreecustomtreeselect.TreeLockChecker;

import java.util.List;

@Mixin(SkillButton.class)
public abstract class SkillButtonMixin {

    // Fix #2: stop the "unlockable" flash/glow for nodes in a locked tree
    @Inject(method = "setCanLearn", at = @At("HEAD"), cancellable = true)
    private void customTreeMenu$blockCanLearnFlash(CallbackInfo ci) {
        SkillButton self = (SkillButton) (Object) this;
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        for (var entry : SkillTreesReloader.getSkillTrees().entrySet()) {
            if (entry.getValue().getSkillIds().contains(self.skill.getId())) {
                if (TreeLockChecker.getLockReason(player, entry.getKey()) != null) ci.cancel();
                return;
            }
        }
    }

    @Inject(method = "getSkillTooltip", at = @At("RETURN"))
    private void customTreeMenu$addLockTooltip(
            PassiveSkillTree skillTree, CallbackInfoReturnable<List<MutableComponent>> cir) {
        SkillButton self = (SkillButton) (Object) this;
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        for (var entry : SkillTreesReloader.getSkillTrees().entrySet()) {
            if (entry.getValue().getSkillIds().contains(self.skill.getId())) {
                var reason = TreeLockChecker.getLockReason(player, entry.getKey());
                if (reason != null) {
                    cir.getReturnValue().add(Component.empty());
                    cir.getReturnValue().add(((MutableComponent) reason).withStyle(ChatFormatting.RED));
                }
                return;
            }
        }
    }
}