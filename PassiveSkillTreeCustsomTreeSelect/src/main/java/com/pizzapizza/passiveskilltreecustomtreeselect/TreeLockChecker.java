package com.pizzapizza.passiveskilltreecustomtreeselect;

import daripher.skilltree.capability.skill.PlayerSkillsProvider;
import daripher.skilltree.data.reloader.SkillTreesReloader;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.stats.Stat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TreeLockChecker {

    private static Field CLIENT_ADVANCEMENT_PROGRESS_FIELD;

    /** Returns null if unlocked, or a Component describing why it's locked. */
    public static Component getLockReason(Player player, ResourceLocation treeId) {
        TreeMenuLayoutData layout = TreeMenuLayoutData.getOrCreate();
        String treeIdStr = treeId.toString();

        for (var entry : layout.trees) {
            if (!entry.id.equals(treeIdStr)) continue;
            for (var req : entry.requirements) {
                if (!isRequirementMet(player, req)) {
                    return Component.literal(describeRequirement(player, req));
                }
            }
            break;
        }

        TreeMenuLayoutData.TreeGroup group = getLockingGroup(player, treeId);
        if (group != null) {
            String groupName = group.name.isBlank() ? "this group" : group.name;
            return Component.literal("You can only have " + TreeLockChecker.getMaxUnlockedForTree(treeId) + " unlocked " + groupName);
        }

        return null;
    }

    private static Map<AdvancementHolder, AdvancementProgress> getClientProgressMap(ClientAdvancements advancements) {
        try {
            if (CLIENT_ADVANCEMENT_PROGRESS_FIELD == null) {
                CLIENT_ADVANCEMENT_PROGRESS_FIELD = ClientAdvancements.class.getDeclaredField("progress");
                CLIENT_ADVANCEMENT_PROGRESS_FIELD.setAccessible(true);
            }
            //noinspection unchecked
            return (Map<AdvancementHolder, AdvancementProgress>) CLIENT_ADVANCEMENT_PROGRESS_FIELD.get(advancements);
        } catch (ReflectiveOperationException e) {
            return Map.of();
        }
    }

    public static boolean isTreeLockedForPlayer(Player player, ResourceLocation treeId) {
        return getLockReason(player, treeId) != null;
    }

    public static TreeMenuLayoutData.TreeGroup getLockingGroup(Player player, ResourceLocation treeId) {
        TreeMenuLayoutData layout = TreeMenuLayoutData.getOrCreate();
        String treeIdStr = treeId.toString();

        Set<ResourceLocation> learnedIds = new HashSet<>();
        PlayerSkillsProvider.get(player).getPlayerSkills().forEach(skill -> learnedIds.add(skill.getId()));

        for (TreeMenuLayoutData.TreeGroup group : layout.groups) {
            if (!group.treeIds.contains(treeIdStr)) continue;

            List<String> unlockedInGroup = new ArrayList<>();
            for (String groupTreeId : group.treeIds) {
                var tree = SkillTreesReloader.getSkillTreeById(ResourceLocation.parse(groupTreeId));
                if (tree != null && tree.getSkillIds().stream().anyMatch(learnedIds::contains)) {
                    unlockedInGroup.add(groupTreeId);
                }
            }
            if (!unlockedInGroup.contains(treeIdStr) && unlockedInGroup.size() >= group.maxUnlocked) {
                return group;
            }
        }
        return null;
    }

    public static boolean isRequirementMet(Player player, TreeMenuLayoutData.TreeRequirement req) {
        if ("item".equals(req.type)) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(req.itemId));
            Stat<Item> stat = Stats.ITEM_CRAFTED.get(item);

            if (player instanceof ServerPlayer serverPlayer) {
                return serverPlayer.getStats().getValue(stat) >= req.count;
            }
            if (player instanceof LocalPlayer localPlayer) {
                return localPlayer.getStats().getValue(stat) >= req.count;
            }
            return false;
        }

        if ("advancement".equals(req.type)) {
            ResourceLocation advId = ResourceLocation.parse(req.advancementId);
            AdvancementHolder holder = getAdvancementHolder(player, advId);
            System.out.println("[skilltree] advId=" + advId + " holder=" + (holder != null ? holder.id() : "NULL"));
            if (holder == null) return false;

            if (player instanceof ServerPlayer serverPlayer) {
                boolean done = serverPlayer.getAdvancements().getOrStartProgress(holder).isDone();
                System.out.println("[skilltree] server-side done=" + done);
                return done;
            }

            var connection = Minecraft.getInstance().getConnection();
            if (connection == null) return false;
            AdvancementProgress progress = getClientProgressMap(connection.getAdvancements()).get(holder);
            System.out.println("[skilltree] client-side progress=" + progress
                    + " done=" + (progress != null && progress.isDone()));
            return progress != null && progress.isDone();
        }

        return true;
    }

    private static String describeRequirement(Player player, TreeMenuLayoutData.TreeRequirement req) {
        if ("item".equals(req.type)) {
            return "Requires crafting " + req.count + "x " + getItemDisplayName(req.itemId);
        }
        if ("advancement".equals(req.type)) {
            String name = getAdvancementDisplayName(player, ResourceLocation.parse(req.advancementId));
            return "Requires advancement: " + name;
        }
        return "Locked";
    }

    public static int getMaxUnlockedForTree(ResourceLocation treeId) {
        TreeMenuLayoutData layout = TreeMenuLayoutData.getOrCreate();
        String treeIdStr = treeId.toString();

        for (TreeMenuLayoutData.TreeGroup group : layout.groups) {
            if (group.treeIds.contains(treeIdStr)) {
                return group.maxUnlocked;
            }
        }
        return -1;
    }

    private static AdvancementHolder getAdvancementHolder(Player player, ResourceLocation advId) {
        if (player instanceof ServerPlayer serverPlayer) {
            return serverPlayer.getServer().getAdvancements().get(advId);
        }
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) return null;
        var node = connection.getAdvancements().getTree().get(advId);
        return node != null ? node.holder() : null;
    }

    public static String getAdvancementDisplayName(Player player, ResourceLocation advId) {
        if (player instanceof ServerPlayer serverPlayer) {
            var holder = serverPlayer.getServer().getAdvancements().get(advId);
            return holder != null ? Advancement.name(holder).getString() : advId.toString();
        }
        return AdvancementNameCache.get(advId);
    }

    public static String getItemDisplayName(String itemId) {
        ResourceLocation id = ResourceLocation.parse(itemId);
        if (!BuiltInRegistries.ITEM.containsKey(id)) return itemId;
        return BuiltInRegistries.ITEM.get(id).getDescription().getString();
    }
}