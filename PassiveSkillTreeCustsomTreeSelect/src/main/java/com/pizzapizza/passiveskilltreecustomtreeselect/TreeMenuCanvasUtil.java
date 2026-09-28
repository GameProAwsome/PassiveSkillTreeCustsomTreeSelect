package com.pizzapizza.passiveskilltreecustomtreeselect;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.Collection;

/** Shared math/formatting used by both the read-only tree-selection screen and the layout editor. */
public final class TreeMenuCanvasUtil {
    private TreeMenuCanvasUtil() {}

    public static float screenToWorldX(double screenX, float panX, float zoom) {
        return (float) ((screenX - panX) / zoom);
    }

    public static float screenToWorldY(double screenY, float panY, float zoom) {
        return (float) ((screenY - panY) / zoom);
    }

    /** World-space bounding box of every tree + image's center, in {minCX, maxCX, minCY, maxCY} order. */
    public static float[] computeBounds(Collection<TreeMenuLayoutData.TreeEntry> trees,
                                         Collection<TreeMenuLayoutData.ImageEntry> images) {
        float minCX = Float.MAX_VALUE, maxCX = -Float.MAX_VALUE;
        float minCY = Float.MAX_VALUE, maxCY = -Float.MAX_VALUE;

        for (var entry : trees) {
            float half = TreeMenuLayoutData.BUTTON_SIZE * entry.scale / 2f;
            float cx = entry.x + half;
            float cy = entry.y + half;
            minCX = Math.min(minCX, cx); maxCX = Math.max(maxCX, cx);
            minCY = Math.min(minCY, cy); maxCY = Math.max(maxCY, cy);
        }
        for (var img : images) {
            float cx = img.x + img.width * img.scale / 2f;
            float cy = img.y + img.height * img.scale / 2f;
            minCX = Math.min(minCX, cx); maxCX = Math.max(maxCX, cx);
            minCY = Math.min(minCY, cy); maxCY = Math.max(maxCY, cy);
        }
        return new float[]{minCX, maxCX, minCY, maxCY};
    }

    /** Returns an adjusted {panX, panY}, clamped so the bounded content stays roughly on-screen. */
    public static float[] clampPan(float[] bounds, float panX, float panY, float zoom,
                                    int screenWidth, int screenHeight, float slack) {
        float minCX = bounds[0], maxCX = bounds[1], minCY = bounds[2], maxCY = bounds[3];
        float screenCX = screenWidth / 2f;
        float screenCY = screenHeight / 2f;

        float leftmostScreenX = minCX * zoom + panX;
        if (leftmostScreenX > screenCX + slack) panX = screenCX + slack - minCX * zoom;
        float rightmostScreenX = maxCX * zoom + panX;
        if (rightmostScreenX < screenCX - slack) panX = screenCX - slack - maxCX * zoom;

        float topmostScreenY = minCY * zoom + panY;
        if (topmostScreenY > screenCY + slack) panY = screenCY + slack - minCY * zoom;
        float bottommostScreenY = maxCY * zoom + panY;
        if (bottommostScreenY < screenCY - slack) panY = screenCY - slack - maxCY * zoom;

        return new float[]{panX, panY};
    }

    /** "Craft Nx Item" or "Advancement: Name" — no color/indentation, just the raw description. */
    public static String describeRequirementShort(Player player, TreeMenuLayoutData.TreeRequirement req) {
        return "item".equals(req.type)
                ? "Craft " + req.count + "x " + TreeLockChecker.getItemDisplayName(req.itemId)
                : "Advancement: " + TreeLockChecker.getAdvancementDisplayName(player, ResourceLocation.parse(req.advancementId));
    }

    /** Whether this tree's own item/advancement requirements are unmet (ignores group max-unlocked limits). */
    public static boolean hasUnmetRequirements(Player player, TreeMenuLayoutData.TreeEntry entry) {
        for (var req : entry.requirements) {
            if (!TreeLockChecker.isRequirementMet(player, req)) return true;
        }
        return false;
    }
}