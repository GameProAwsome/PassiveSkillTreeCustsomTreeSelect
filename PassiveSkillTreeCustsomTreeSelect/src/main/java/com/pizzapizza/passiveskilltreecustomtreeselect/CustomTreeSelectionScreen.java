package com.pizzapizza.passiveskilltreecustomtreeselect;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CustomTreeSelectionScreen extends Screen {
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.parse("skilltree:textures/screen/skill_tree_background.png");
    private static final float PAN_SLACK = 20f;

    private TreeMenuLayoutData layout;
    private float zoom = 1.0f;
    private float panX = 0f, panY = 0f;

    private final Map<ZoomableTreeButton, TreeMenuLayoutData.TreeEntry> treeWidgets = new LinkedHashMap<>();

    private boolean panning = false;

    public CustomTreeSelectionScreen() {
        super(Component.literal("Custom Tree Selection"));
    }

    @Override
    protected void init() {
        layout = TreeMenuLayoutData.getOrCreate();
        treeWidgets.clear();
        for (TreeMenuLayoutData.TreeEntry entry : layout.trees) {
            int size = Math.round(TreeMenuLayoutData.BUTTON_SIZE * entry.scale);
            ZoomableTreeButton button = new ZoomableTreeButton((int) Math.round(entry.x), (int) Math.round(entry.y), size, ResourceLocation.parse(entry.id));
            treeWidgets.put(button, entry);
        }

        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(
                    net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.REQUEST_STATS));
        }

        computeLockedTrees();
    }

    private int ticksUntilRecompute = 0;

    @Override
    public void tick() {
        super.tick();
        if (--ticksUntilRecompute <= 0) {
            computeLockedTrees();
            ticksUntilRecompute = 10; // recheck twice a second
        }
    }

    private float screenToWorldX(double screenX) { return TreeMenuCanvasUtil.screenToWorldX(screenX, panX, zoom); }
    private float screenToWorldY(double screenY) { return TreeMenuCanvasUtil.screenToWorldY(screenY, panY, zoom); }

    private void clampPan() {
        if (treeWidgets.isEmpty() && layout.images.isEmpty()) return;
        float[] bounds = TreeMenuCanvasUtil.computeBounds(treeWidgets.values(), layout.images);
        float[] pan = TreeMenuCanvasUtil.clampPan(bounds, panX, panY, zoom, this.width, this.height, PAN_SLACK);
        panX = pan[0];
        panY = pan[1];
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float wx = screenToWorldX(mouseX), wy = screenToWorldY(mouseY);
        for (var e : treeWidgets.entrySet()) {
            ZoomableTreeButton widget = e.getKey();
            if (widget.isMouseOver(wx, wy)) {
                if (!widget.isLocked()) {
                    widget.onPress();
                }
                return true;
            }
        }
        if (button == 2) {
            panning = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (panning) {
            panX += (float) dragX;
            panY += (float) dragY;
            clampPan();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        panning = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        zoom = Math.max(0.5f, Math.min(3.0f, zoom + (float) scrollY * 0.1f));
        clampPan();
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int size = 2048;
        graphics.blit(BACKGROUND, (this.width - size) / 2, (this.height - size) / 2, 0, 0, size, size, size, size);

        graphics.pose().pushPose();
        graphics.pose().translate(panX, panY, 0);
        graphics.pose().scale(zoom, zoom, 1);

        int worldMouseX = Math.round(screenToWorldX(mouseX));
        int worldMouseY = Math.round(screenToWorldY(mouseY));
        var texManager = Minecraft.getInstance().getTextureManager();

        for (TreeMenuLayoutData.ImageEntry img : layout.images) {
            ResourceLocation tex = ResourceLocation.parse(img.texture);
            texManager.getTexture(tex).setFilter(false, false);
            int sw = Math.round(img.width * img.scale);
            int sh = Math.round(img.height * img.scale);
            graphics.blit(tex, (int) Math.round(img.x), (int) Math.round(img.y), 0, 0, sw, sh, sw, sh);
        }
        for (ZoomableTreeButton widget : treeWidgets.keySet()) {
            widget.render(graphics, worldMouseX, worldMouseY, partialTick);
        }

        graphics.pose().popPose();

        var player = Minecraft.getInstance().player;
        if (player != null) {
            for (var e : treeWidgets.entrySet()) {
                if (e.getKey().isMouseOver(worldMouseX, worldMouseY)) {
                    graphics.renderComponentTooltip(this.font, buildTreeTooltip(e.getValue(), player), mouseX, mouseY);
                    break;
                }
            }
        }
    }

    private void computeLockedTrees() {
    var player = Minecraft.getInstance().player;
    if (player == null) return;

    for (var e : treeWidgets.entrySet()) {
        boolean locked = TreeMenuCanvasUtil.hasUnmetRequirements(player, e.getValue());
        e.getKey().setLocked(locked);
    }
}

    private List<Component> buildTreeTooltip(TreeMenuLayoutData.TreeEntry entry, Player player) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(entry.id));

        ResourceLocation treeId = ResourceLocation.parse(entry.id);
        var lockingGroup = TreeLockChecker.getLockingGroup(player, treeId);
        boolean hasReqLine = !entry.requirements.isEmpty() || lockingGroup != null;

        if (hasReqLine) {
            lines.add(Component.literal("Requirements:").withStyle(ChatFormatting.GOLD));
        }
        for (var req : entry.requirements) {
            boolean met = TreeLockChecker.isRequirementMet(player, req);
            String text = TreeMenuCanvasUtil.describeRequirementShort(player, req);
            lines.add(Component.literal("  " + text).withStyle(met ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        if (lockingGroup != null) {
            String groupName = lockingGroup.name.isBlank() ? "this group" : lockingGroup.name;
            lines.add(Component.literal("  You can only have " + TreeLockChecker.getMaxUnlockedForTree(treeId)
                    + " unlocked " + groupName).withStyle(ChatFormatting.RED));
        }
        return lines;
    }
}