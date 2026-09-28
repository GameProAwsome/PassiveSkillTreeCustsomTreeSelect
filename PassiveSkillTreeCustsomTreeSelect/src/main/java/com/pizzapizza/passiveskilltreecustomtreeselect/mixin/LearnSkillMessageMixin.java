package com.pizzapizza.passiveskilltreecustomtreeselect.mixin;

import daripher.skilltree.data.reloader.SkillTreesReloader;
import daripher.skilltree.network.message.LearnSkillMessage;
import daripher.skilltree.skill.PassiveSkillTree;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pizzapizza.passiveskilltreecustomtreeselect.TreeLockChecker;

@Mixin(LearnSkillMessage.class)
public abstract class LearnSkillMessageMixin {

    @Shadow private ResourceLocation skillId;

    @Inject(method = "receive", at = @At("HEAD"), cancellable = true)
    private static void customTreeMenu$blockLockedTreeLearning(
            LearnSkillMessage message, IPayloadContext context, CallbackInfo ci) {

        ServerPlayer player = (ServerPlayer) context.player();
        if (player == null) return;

        ResourceLocation targetSkillId = ((LearnSkillMessageMixin) (Object) message).skillId;
        if (targetSkillId == null) return;

        for (var entry : SkillTreesReloader.getSkillTrees().entrySet()) {
            PassiveSkillTree tree = entry.getValue();
            if (tree.getSkillIds().contains(targetSkillId)) {
                if (TreeLockChecker.getLockReason(player, entry.getKey()) != null) {
                    player.displayClientMessage(
                            Component.literal("This tree is locked."),
                            true);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                            player, new daripher.skilltree.network.message.SyncPlayerSkillsMessage(player));
                    ci.cancel();
                }
                return;
            }
        }
    }
}