package com.pizzapizza.passiveskilltreecustomtreeselect;

import daripher.skilltree.client.widget.SkillTreeSelectionButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class ZoomableTreeButton extends SkillTreeSelectionButton {
    private final ResourceLocation treeId;
    private boolean locked = false;

    public ZoomableTreeButton(int x, int y, int size, ResourceLocation treeId) {
        super(x, y, size, size, treeId);
        this.treeId = treeId;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public boolean isLocked() {
        return locked;
    }

    @Override
    protected void renderBackground(@NotNull GuiGraphics graphics) {
        ResourceLocation texture = this.treeId.withPrefix("textures/icons/skill_tree/").withSuffix(".png");
        Minecraft.getInstance().getTextureManager().getTexture(texture).setFilter(false, false);

        int variant;
        if (this.locked) {
            variant = 0; // locked
        } else if (this.isHoveredOrFocused()) {
            variant = 2; // hovered
        } else {
            variant = 1; // idle
        }
        int v = variant * this.getHeight();

        graphics.blit(texture, this.getX(), this.getY(), 0, v, this.getWidth(), this.getHeight(),
                this.getWidth(), this.getHeight() * 3);
    }
}