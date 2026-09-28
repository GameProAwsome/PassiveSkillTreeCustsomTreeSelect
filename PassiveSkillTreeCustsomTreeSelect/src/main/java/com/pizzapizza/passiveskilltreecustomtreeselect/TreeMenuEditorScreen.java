package com.pizzapizza.passiveskilltreecustomtreeselect;

import daripher.skilltree.data.client.SkillTexturesData;
import daripher.skilltree.data.reloader.SkillTreesReloader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class TreeMenuEditorScreen extends Screen {
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.parse("skilltree:textures/screen/skill_tree_background.png");
    private static final float PAN_SLACK = 20f;

    private TreeMenuLayoutData layout;
    private float zoom = 1.0f;
    private int panX = 0, panY = 0;

    private final Map<ZoomableTreeButton, TreeMenuLayoutData.TreeEntry> treeWidgets = new LinkedHashMap<>();
    private final List<TreeMenuLayoutData.ImageEntry> images = new ArrayList<>();

    private Object dragging;
    private static final Object PANNING = new Object();

    private EditBox treeIdBox;
    private List<String> suggestions = List.of();
    private int suggestionIndex = 0;

    private EditBox folderBox;
    private List<ResourceLocation> iconResults = List.of();
    private int iconScrollOffset = 0;

    private EditBox scaleBox;
    private EditBox maxUnlockedBox;
    private EditBox groupNameBox;

    private Button groupButton, ungroupButton, addAdvancementButton, addItemButton;

    private EditBox advancementBox, itemBox, itemCountBox;
    private List<String> advancementSuggestions = List.of();
    private int advancementSuggestionIndex = 0;
    private List<String> itemSuggestions = List.of();
    private int itemSuggestionIndex = 0;
    private final List<Button> requirementRemoveButtons = new ArrayList<>();

    private long saveMessageUntil = 0L;

    private final Set<TreeMenuLayoutData.TreeEntry> selectedTrees = new LinkedHashSet<>();
    private final Set<TreeMenuLayoutData.ImageEntry> selectedImages = new LinkedHashSet<>();

    private final Map<Object, float[]> dragWorldOffsets = new HashMap<>();

    private static final Object MOVING_SELECTION = new Object();

    private static final int ICON_GRID_ROW_HEIGHT = 20;
    private static final int ICON_GRID_VISIBLE_ROWS = 3;
    private static final int REQUIREMENTS_LIST_Y = 88;

    // Position of the icon grid, computed in init() (moved right + below the icon folder field).
    private int iconGridX, iconGridY;

    public TreeMenuEditorScreen() {
        super(Component.literal("Tree Menu Editor"));
    }

    @Override
    protected void init() {
        layout = TreeMenuLayoutData.getOrCreate();
        computeLockedTrees();

        // ---- Requirements panel (top-left) ----
        advancementBox = new EditBox(this.font, 10, 36, 200, 16, Component.literal("Advancement"));
        advancementBox.setMaxLength(256);
        advancementBox.setHint(Component.literal("minecraft:story/mine_diamond"));
        advancementBox.setResponder(this::updateAdvancementSuggestions);
        this.addRenderableWidget(advancementBox);
        addAdvancementButton = this.addRenderableWidget(Button.builder(Component.literal("Add"), b -> addAdvancementRequirement())
                .bounds(215, 36, 50, 16).build());

        itemBox = new EditBox(this.font, 10, 66, 160, 16, Component.literal("Item"));
        itemBox.setMaxLength(256);
        itemBox.setHint(Component.literal("minecraft:iron_ingot"));
        itemBox.setResponder(this::updateItemSuggestions);
        this.addRenderableWidget(itemBox);

        itemCountBox = new EditBox(this.font, 175, 66, 40, 16, Component.literal("x1"));
        itemCountBox.setMaxLength(6);
        itemCountBox.setValue("1");
        this.addRenderableWidget(itemCountBox);

        addItemButton = this.addRenderableWidget(Button.builder(Component.literal("Add"), b -> addItemRequirement())
            .bounds(220, 66, 50, 16).build());

        // ---- Canvas panel (bottom-left) ----
        treeIdBox = new EditBox(this.font, 10, this.height - 134, 220, 16, Component.literal("Tree ID"));
        treeIdBox.setMaxLength(256);
        treeIdBox.setHint(Component.literal("custom_skill_tree:test"));
        treeIdBox.setResponder(this::updateTreeSuggestions);
        this.addRenderableWidget(treeIdBox);

        folderBox = new EditBox(this.font, 10, this.height - 96, 220, 16, Component.literal("Icon folder"));
        folderBox.setMaxLength(256);
        folderBox.setResponder(this::updateIconResults);
        this.addRenderableWidget(folderBox);
        folderBox.setValue("textures/icons");
        updateIconResults("textures/icons");

        // Icon grid: shifted left by 2 icon columns (40px), still below the folder field
        iconGridX = 10;
        iconGridY = this.height - 68;

        // Scale / Add / Remove: same x positions as before, moved down to sit beside the icon grid
        scaleBox = new EditBox(this.font, 240, iconGridY + 16, 50, 16, Component.literal("Scale"));
        scaleBox.setMaxLength(6);
        scaleBox.setValue("1.00");
        scaleBox.setResponder(this::onScaleBoxChanged);
        this.addRenderableWidget(scaleBox);

        this.addRenderableWidget(Button.builder(Component.literal("-"), b -> adjustScale(-0.5f))
                .bounds(240, iconGridY + 36, 24, 16).build());
        this.addRenderableWidget(Button.builder(Component.literal("+"), b -> adjustScale(0.5f))
                .bounds(268, iconGridY + 36, 24, 16).build());

        this.addRenderableWidget(Button.builder(Component.literal("Add Tree"), b -> addTreeFromBox())
                .bounds(300, iconGridY + 16, 90, 16).build());
        this.addRenderableWidget(Button.builder(Component.literal("Remove Selected"), b -> removeSelected())
                .bounds(300, iconGridY + 36, 110, 16).build());

        // ---- Save (bottom-right) ----
        this.addRenderableWidget(Button.builder(Component.literal("Save"), b -> {
                    TreeMenuLayoutData.save(layout);
                    saveMessageUntil = System.currentTimeMillis() + 2000;
                })
                .bounds(this.width - 90, this.height - 40, 80, 20).build());

        // ---- Group panel (right side) ----
        groupButton = this.addRenderableWidget(Button.builder(Component.literal("Group Selected"), b -> groupSelected())
                .bounds(this.width - 150, 30, 140, 16).build());
        ungroupButton = this.addRenderableWidget(Button.builder(Component.literal("Ungroup Selected"), b -> ungroupSelected())
                .bounds(this.width - 150, 50, 140, 16).build());

        maxUnlockedBox = new EditBox(this.font, this.width - 150, 84, 60, 16, Component.literal("Max Unlocked"));
        maxUnlockedBox.setMaxLength(4);
        maxUnlockedBox.setResponder(this::onMaxUnlockedChanged);
        this.addRenderableWidget(maxUnlockedBox);

        groupNameBox = new EditBox(this.font, this.width - 150, 116, 140, 16, Component.literal("Group Name"));
        groupNameBox.setMaxLength(64);
        groupNameBox.setResponder(this::onGroupNameChanged);
        this.addRenderableWidget(groupNameBox);

        rebuildCanvasEntities();
        updateConditionalPanels();
    }

    private void rebuildCanvasEntities() {
        treeWidgets.clear();
        for (TreeMenuLayoutData.TreeEntry entry : layout.trees) {
            int sx = Math.round(entry.x * zoom) + panX;
            int sy = Math.round(entry.y * zoom) + panY;
            int size = Math.max(1, Math.round(TreeMenuLayoutData.BUTTON_SIZE * entry.scale * zoom));
            ZoomableTreeButton button = new ZoomableTreeButton(sx, sy, size, ResourceLocation.parse(entry.id));
            treeWidgets.put(button, entry);
        }
        images.clear();
        images.addAll(layout.images);
    }

    private int imageScreenX(TreeMenuLayoutData.ImageEntry e) { return Math.round(e.x * zoom) + panX; }
    private int imageScreenY(TreeMenuLayoutData.ImageEntry e) { return Math.round(e.y * zoom) + panY; }
    private int imageScreenW(TreeMenuLayoutData.ImageEntry e) { return Math.max(1, Math.round(e.width * e.scale * zoom)); }
    private int imageScreenH(TreeMenuLayoutData.ImageEntry e) { return Math.max(1, Math.round(e.height * e.scale * zoom)); }

    private void updateTreeSuggestions(String text) {
        suggestionIndex = 0;
        if (text.isBlank()) { suggestions = List.of(); return; }
        String lower = text.toLowerCase(Locale.ROOT);
        suggestions = SkillTreesReloader.getSkillTrees().keySet().stream()
                .map(ResourceLocation::toString)
                .filter(id -> id.toLowerCase(Locale.ROOT).contains(lower))
                .sorted()
                .limit(6)
                .toList();
    }

    private void addTreeFromBox() {
        String text = treeIdBox.getValue().trim();
        if (text.isEmpty()) return;
        ResourceLocation id;
        try {
            id = ResourceLocation.parse(text);
        } catch (Exception e) {
            return;
        }
        TreeMenuLayoutData.TreeEntry entry = new TreeMenuLayoutData.TreeEntry();
        entry.id = id.toString();
        entry.x = Math.round(TreeMenuCanvasUtil.screenToWorldX(this.width / 2.0, panX, zoom));
        entry.y = Math.round(TreeMenuCanvasUtil.screenToWorldY(this.height / 2.0, panY, zoom));
        layout.trees.add(entry);
        treeIdBox.setValue("");
        suggestions = List.of();
        rebuildCanvasEntities();
    }

    private void updateIconResults(String folder) {
        iconScrollOffset = 0;
        if (folder.isBlank()) {
            iconResults = List.of();
            return;
        }
        if (SkillTexturesData.isTextureFolder(folder)) {
            iconResults = SkillTexturesData.getTexturesInFolder(folder).stream()
                    .sorted(Comparator.comparing(ResourceLocation::toString))
                    .toList();
        } else {
            iconResults = List.of();
        }
    }

    private void addImage(ResourceLocation texture) {
        TreeMenuLayoutData.ImageEntry entry = new TreeMenuLayoutData.ImageEntry();
        entry.texture = texture.toString();
        entry.x = Math.round(TreeMenuCanvasUtil.screenToWorldX(this.width / 2.0, panX, zoom));
        entry.y = Math.round(TreeMenuCanvasUtil.screenToWorldY(this.height / 2.0, panY, zoom));
        entry.width = 32;
        entry.height = 32;
        layout.images.add(entry);
        rebuildCanvasEntities();
    }

    private void removeSelected() {
        layout.trees.removeAll(selectedTrees);
        layout.images.removeAll(selectedImages);
        selectedTrees.clear();
        selectedImages.clear();
        rebuildCanvasEntities();
        syncScaleBox();
        syncAndUpdate();
    }

    private void clampPan() {
        if (layout.trees.isEmpty() && layout.images.isEmpty()) return;
        float[] bounds = TreeMenuCanvasUtil.computeBounds(layout.trees, layout.images);
        float[] pan = TreeMenuCanvasUtil.clampPan(bounds, panX, panY, zoom, this.width, this.height, PAN_SLACK);
        panX = Math.round(pan[0]);
        panY = Math.round(pan[1]);
    }

    private boolean isOverIconGrid(double mouseX, double mouseY) {
        int gridLeft = iconGridX;
        int gridRight = gridLeft + 8 * 20;
        int gridTop = iconGridY;
        int gridBottom = gridTop + ICON_GRID_VISIBLE_ROWS * ICON_GRID_ROW_HEIGHT;
        return mouseX >= gridLeft && mouseX < gridRight && mouseY >= gridTop && mouseY < gridBottom;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (treeIdBox.isFocused() && !suggestions.isEmpty()) {
            if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_TAB) {
                suggestionIndex = (suggestionIndex + 1) % suggestions.size();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP) {
                suggestionIndex = (suggestionIndex - 1 + suggestions.size()) % suggestions.size();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                treeIdBox.setValue(suggestions.get(suggestionIndex));
                suggestions = List.of();
                return true;
            }
        }

        if (advancementBox.isFocused() && !advancementSuggestions.isEmpty()) {
            if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_TAB) {
                advancementSuggestionIndex = (advancementSuggestionIndex + 1) % advancementSuggestions.size();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP) {
                advancementSuggestionIndex = (advancementSuggestionIndex - 1 + advancementSuggestions.size()) % advancementSuggestions.size();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                advancementBox.setValue(advancementSuggestions.get(advancementSuggestionIndex));
                advancementSuggestions = List.of();
                return true;
            }
        }

        if (itemBox.isFocused() && !itemSuggestions.isEmpty()) {
            if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_TAB) {
                itemSuggestionIndex = (itemSuggestionIndex + 1) % itemSuggestions.size();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP) {
                itemSuggestionIndex = (itemSuggestionIndex - 1 + itemSuggestions.size()) % itemSuggestions.size();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                itemBox.setValue(itemSuggestions.get(itemSuggestionIndex));
                itemSuggestions = List.of();
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!suggestions.isEmpty()) {
            int listY = treeIdBox.getY() - suggestions.size() * 12;
            for (int i = 0; i < suggestions.size(); i++) {
                int rowY = listY + i * 12;
                if (mouseX >= treeIdBox.getX() && mouseX < treeIdBox.getX() + 220
                        && mouseY >= rowY && mouseY < rowY + 12) {
                    treeIdBox.setValue(suggestions.get(i));
                    suggestions = List.of();
                    return true;
                }
            }
        }

        if (!advancementSuggestions.isEmpty()) {
            int listY = advancementBox.getY() + advancementBox.getHeight() + 2;
            for (int i = 0; i < advancementSuggestions.size(); i++) {
                int rowY = listY + i * 12;
                if (mouseX >= advancementBox.getX() && mouseX < advancementBox.getX() + 200
                        && mouseY >= rowY && mouseY < rowY + 12) {
                    advancementBox.setValue(advancementSuggestions.get(i));
                    advancementSuggestions = List.of();
                    return true;
                }
            }
        }

        if (!itemSuggestions.isEmpty()) {
            int listY = itemBox.getY() + itemBox.getHeight() + 2;
            for (int i = 0; i < itemSuggestions.size(); i++) {
                int rowY = listY + i * 12;
                if (mouseX >= itemBox.getX() && mouseX < itemBox.getX() + 160
                        && mouseY >= rowY && mouseY < rowY + 12) {
                    itemBox.setValue(itemSuggestions.get(i));
                    itemSuggestions = List.of();
                    return true;
                }
            }
        }

        if (!iconResults.isEmpty()) {
            int startIndex = iconScrollOffset * 8;
            int col = 0, row = 0;
            for (int i = startIndex; i < iconResults.size() && (i - startIndex) < 24; i++) {
                int ix = iconGridX + col * 20;
                int iy = iconGridY + row * 20;
                if (mouseX >= ix && mouseX < ix + 18 && mouseY >= iy && mouseY < iy + 18) {
                    addImage(iconResults.get(i));
                    return true;
                }
                col++;
                if (col >= 8) { col = 0; row++; }
            }
        }

        for (var widget : treeWidgets.keySet()) {
            if (widget.isMouseOver(mouseX, mouseY)) {
                TreeMenuLayoutData.TreeEntry entry = treeWidgets.get(widget);
                if (Screen.hasShiftDown()) {
                    if (!selectedTrees.remove(entry)) selectedTrees.add(entry);
                } else if (!selectedTrees.contains(entry)) {
                    selectedTrees.clear();
                    selectedImages.clear();
                    selectedTrees.add(entry);
                }
                dragging = MOVING_SELECTION;
                beginGroupDrag(mouseX, mouseY);
                syncScaleBox();
                syncAndUpdate();
                return true;
            }
        }
        for (var entry : images) {
            int sx = imageScreenX(entry), sy = imageScreenY(entry);
            int sw = imageScreenW(entry), sh = imageScreenH(entry);
            if (mouseX >= sx && mouseX < sx + sw && mouseY >= sy && mouseY < sy + sh) {
                if (Screen.hasShiftDown()) {
                    if (!selectedImages.remove(entry)) selectedImages.add(entry);
                } else if (!selectedImages.contains(entry)) {
                    selectedImages.clear();
                    selectedTrees.clear();
                    selectedImages.add(entry);
                }
                dragging = MOVING_SELECTION;
                beginGroupDrag(mouseX, mouseY);
                syncScaleBox();
                syncAndUpdate();
                return true;
            }
        }

        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (!handled && button == 0 && !Screen.hasShiftDown()) {
            selectedTrees.clear();
            selectedImages.clear();
            syncScaleBox();
            syncAndUpdate();
        }
        if (!handled && button == 2) {
            dragging = PANNING;
            return true;
        }
        return handled;
    }

    private void beginGroupDrag(double mouseX, double mouseY) {
        float worldMouseX = TreeMenuCanvasUtil.screenToWorldX(mouseX, panX, zoom);
        float worldMouseY = TreeMenuCanvasUtil.screenToWorldY(mouseY, panY, zoom);
        dragWorldOffsets.clear();
        for (var t : selectedTrees) dragWorldOffsets.put(t, new float[]{t.x - worldMouseX, t.y - worldMouseY});
        for (var i : selectedImages) dragWorldOffsets.put(i, new float[]{i.x - worldMouseX, i.y - worldMouseY});
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging == PANNING) {
            panX += (int) dragX;
            panY += (int) dragY;
            clampPan();
            rebuildCanvasEntities();
            return true;
        }

        if (dragging == MOVING_SELECTION) {
            float worldMouseX = TreeMenuCanvasUtil.screenToWorldX(mouseX, panX, zoom);
            float worldMouseY = TreeMenuCanvasUtil.screenToWorldY(mouseY, panY, zoom);
            for (var e : treeWidgets.entrySet()) {
                TreeMenuLayoutData.TreeEntry entry = e.getValue();
                float[] offset = dragWorldOffsets.get(entry);
                if (offset != null) {
                    entry.x = worldMouseX + offset[0];
                    entry.y = worldMouseY + offset[1];
                    e.getKey().setX(Math.round(entry.x * zoom) + panX);
                    e.getKey().setY(Math.round(entry.y * zoom) + panY);
                }
            }
            for (var entry : selectedImages) {
                float[] offset = dragWorldOffsets.get(entry);
                if (offset != null) {
                    entry.x = worldMouseX + offset[0];
                    entry.y = worldMouseY + offset[1];
                }
            }
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!iconResults.isEmpty() && isOverIconGrid(mouseX, mouseY)) {
            int totalRows = (iconResults.size() + 7) / 8;
            int maxOffset = Math.max(0, totalRows - 3);
            iconScrollOffset = Math.max(0, Math.min(maxOffset, iconScrollOffset - (int) Math.signum(scrollY)));
            return true;
        }

        boolean overSelectedTree = false;
        for (var e : treeWidgets.entrySet()) {
            if (selectedTrees.contains(e.getValue()) && e.getKey().isMouseOver(mouseX, mouseY)) {
                overSelectedTree = true;
                break;
            }
        }
        boolean overSelectedImage = false;
        for (var entry : selectedImages) {
            int sx = imageScreenX(entry), sy = imageScreenY(entry);
            int sw = imageScreenW(entry), sh = imageScreenH(entry);
            if (mouseX >= sx && mouseX < sx + sw && mouseY >= sy && mouseY < sy + sh) {
                overSelectedImage = true;
                break;
            }
        }
        if (overSelectedTree || overSelectedImage) {
            float delta = (float) scrollY * 0.1f;
            for (var e : selectedTrees) e.scale = Math.max(0.25f, e.scale + delta);
            for (var e : selectedImages) e.scale = Math.max(0.25f, e.scale + delta);
            rebuildCanvasEntities();
            syncScaleBox();
            syncAndUpdate();
            return true;
        }

        zoom = Math.max(0.5f, Math.min(3.0f, zoom + (float) scrollY * 0.1f));
        clampPan();
        rebuildCanvasEntities();
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {

        var texManager = Minecraft.getInstance().getTextureManager();

        texManager.getTexture(BACKGROUND).setFilter(false, false);

        int size = 2048;
        graphics.blit(BACKGROUND, (this.width - size) / 2, (this.height - size) / 2, 0, 0, size, size, size, size);

        for (var entry : images) {
            ResourceLocation tex = ResourceLocation.parse(entry.texture);
            texManager.getTexture(tex).setFilter(false, false);
            graphics.blit(tex, imageScreenX(entry), imageScreenY(entry), 0, 0,
                    imageScreenW(entry), imageScreenH(entry), imageScreenW(entry), imageScreenH(entry));

            if (selectedImages.contains(entry)) {
                drawOutline(graphics, imageScreenX(entry), imageScreenY(entry), imageScreenW(entry), imageScreenH(entry), 0xFFFFFF00);
            }
        }

        Set<String> groupHighlight = new HashSet<>();
        for (var t : selectedTrees) {
            layout.groups.stream().filter(g -> g.treeIds.contains(t.id)).findFirst()
                    .ifPresent(g -> groupHighlight.addAll(g.treeIds));
        }

        for (var e : treeWidgets.entrySet()) {
            var widget = e.getKey();
            widget.render(graphics, mouseX, mouseY, partialTick);
            TreeMenuLayoutData.TreeEntry entry = treeWidgets.get(widget);

            if (lockedTreeIds.contains(entry.id)) {
                graphics.fill(widget.getX(), widget.getY(),
                        widget.getX() + widget.getWidth(), widget.getY() + widget.getHeight(), 0x99000000);
            }

            if (selectedTrees.contains(e.getValue())) {
                drawOutline(graphics, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(), 0xFFFFFF00);
            } else if (groupHighlight.contains(e.getValue().id)) {
                drawOutline(graphics, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(), 0xFF3399FF);
            }
        }

        // ---- Requirements panel labels ----
        if (singleSelectedTree() != null) {
            graphics.drawString(this.font, "Advancement:", advancementBox.getX(), advancementBox.getY() - 11, 0xFFFFFF);
            graphics.drawString(this.font, "Item:", itemBox.getX(), itemBox.getY() - 11, 0xFFFFFF);
            graphics.drawString(this.font, "Count:", itemCountBox.getX(), itemCountBox.getY() - 11, 0xFFFFFF);
        }

        // ---- Canvas panel labels ----
        graphics.drawString(this.font, "Tree ID:", treeIdBox.getX(), treeIdBox.getY() - 11, 0xFFFFFF);
        graphics.drawString(this.font, "Icon Folder:", folderBox.getX(), folderBox.getY() - 11, 0xFFFFFF);
        graphics.drawString(this.font, "Scale:", scaleBox.getX(), scaleBox.getY() - 11, 0xFFFFFF);

        // ---- Icon grid (moved right, below the icon folder field) ----
        if (iconResults.isEmpty()) {
            graphics.drawString(this.font, "No icons found for '" + folderBox.getValue() + "'",
                    iconGridX, iconGridY, 0xFF5555);
        } else {
            int startIndex = iconScrollOffset * 8;
            int col = 0, row = 0;
            for (int i = startIndex; i < iconResults.size() && (i - startIndex) < 24; i++) {
                ResourceLocation icon = iconResults.get(i);
                int ix = iconGridX + col * 20;
                int iy = iconGridY + row * 20;
                graphics.fill(ix - 1, iy - 1, ix + 19, iy + 19, 0x66000000);
                texManager.getTexture(icon).setFilter(false, false);
                graphics.blit(icon, ix, iy, 0, 0, 16, 16, 16, 16);
                col++;
                if (col >= 8) { col = 0; row++; }
            }
        }

        if (!suggestions.isEmpty()) {
            int listY = treeIdBox.getY() - suggestions.size() * 12;
            for (int i = 0; i < suggestions.size(); i++) {
                int rowY = listY + i * 12;
                int bg = (i == suggestionIndex) ? 0xAA557733 : 0xAA000000;
                graphics.fill(treeIdBox.getX(), rowY, treeIdBox.getX() + 220, rowY + 12, bg);
                graphics.drawString(this.font, suggestions.get(i), treeIdBox.getX() + 2, rowY + 2, 0xFFFFFF);
            }
        }

        // ---- Group panel labels (hidden when no group is selected) ----
        if (maxUnlockedBox.visible) {
            graphics.drawString(this.font, "Max Unlocked (group):", this.width - 150, 72, 0xFFFFFF);
        }
        if (groupNameBox.visible) {
            graphics.drawString(this.font, "Group Name:", this.width - 150, 104, 0xFFFFFF);
        }

        var tree = singleSelectedTree();
        if (tree != null) {
            var player = Minecraft.getInstance().player;
            int y = REQUIREMENTS_LIST_Y;
            for (var req : tree.requirements) {
                String text = TreeMenuCanvasUtil.describeRequirementShort(player, req);
                graphics.drawString(this.font, text, 30, y + 4, 0xFFFFFF);
                y += 18;
            }
        }

        if (!advancementSuggestions.isEmpty()) {
            int listY = advancementBox.getY() + advancementBox.getHeight() + 2;
            for (int i = 0; i < advancementSuggestions.size(); i++) {
                int rowY = listY + i * 12;
                int bg = (i == advancementSuggestionIndex) ? 0xAA557733 : 0xAA000000;
                graphics.fill(advancementBox.getX(), rowY, advancementBox.getX() + 200, rowY + 12, bg);
                graphics.drawString(this.font, advancementSuggestions.get(i), advancementBox.getX() + 2, rowY + 2, 0xFFFFFF);
            }
        }

        if (!itemSuggestions.isEmpty()) {
            int listY = itemBox.getY() + itemBox.getHeight() + 2;
            for (int i = 0; i < itemSuggestions.size(); i++) {
                int rowY = listY + i * 12;
                int bg = (i == itemSuggestionIndex) ? 0xAA557733 : 0xAA000000;
                graphics.fill(itemBox.getX(), rowY, itemBox.getX() + 160, rowY + 12, bg);
                graphics.drawString(this.font, itemSuggestions.get(i), itemBox.getX() + 2, rowY + 2, 0xFFFFFF);
            }
        }

        if (System.currentTimeMillis() < saveMessageUntil) {
            graphics.drawString(this.font, "Saved!", this.width - 90, this.height - 24, 0x55FF55);
        }

        graphics.drawString(this.font,
                "Zoom: " + String.format("%.1f", zoom) + "x (scroll) | Middle-drag to pan", 10, 10, 0xFFFFFF);

        for (Renderable renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void drawOutline(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x - 1, y - 1, x + w + 1, y, color);
        graphics.fill(x - 1, y + h, x + w + 1, y + h + 1, color);
        graphics.fill(x - 1, y - 1, x, y + h + 1, color);
        graphics.fill(x + w, y - 1, x + w + 1, y + h + 1, color);
    }

    private void adjustScale(float delta) {
        if (selectedTrees.isEmpty() && selectedImages.isEmpty()) return;
        for (var e : selectedTrees) e.scale = Math.max(0.25f, e.scale + delta);
        for (var e : selectedImages) e.scale = Math.max(0.25f, e.scale + delta);
        rebuildCanvasEntities();
        syncScaleBox();
    }

    private void onScaleBoxChanged(String text) {
        if (selectedTrees.isEmpty() && selectedImages.isEmpty()) return;
        try {
            float value = Math.max(0.25f, Float.parseFloat(text));
            for (var e : selectedTrees) e.scale = value;
            for (var e : selectedImages) e.scale = value;
            rebuildCanvasEntities();
        } catch (NumberFormatException ignored) {
            // mid-typing
        }
    }

    private void syncScaleBox() {
        if (scaleBox == null) return;
        if (selectedTrees.isEmpty() && selectedImages.isEmpty()) {
            scaleBox.setValue("1.00");
            return;
        }
        Float common = null;
        boolean mixed = false;
        for (var e : selectedTrees) {
            if (common == null) common = e.scale;
            else if (common != e.scale) mixed = true;
        }
        for (var e : selectedImages) {
            if (common == null) common = e.scale;
            else if (common != e.scale) mixed = true;
        }
        scaleBox.setValue(mixed ? "" : String.format(Locale.ROOT, "%.2f", common));
    }

    private void groupSelected() {
        if (selectedTrees.size() < 2) return;
        for (var t : selectedTrees) layout.groups.forEach(g -> g.treeIds.remove(t.id));
        layout.groups.removeIf(g -> g.treeIds.isEmpty());

        TreeMenuLayoutData.TreeGroup group = new TreeMenuLayoutData.TreeGroup();
        for (var t : selectedTrees) group.treeIds.add(t.id);
        layout.groups.add(group);
        syncAndUpdate();
    }

    private void ungroupSelected() {
        for (var t : selectedTrees) layout.groups.forEach(g -> g.treeIds.remove(t.id));
        layout.groups.removeIf(g -> g.treeIds.isEmpty());
        syncAndUpdate();
    }

    private TreeMenuLayoutData.TreeGroup groupOfSelection() {
        if (selectedTrees.isEmpty()) return null;
        String firstId = selectedTrees.iterator().next().id;
        return layout.groups.stream().filter(g -> g.treeIds.contains(firstId)).findFirst().orElse(null);
    }

    private void onMaxUnlockedChanged(String text) {
        TreeMenuLayoutData.TreeGroup group = groupOfSelection();
        if (group == null) return;
        try {
            group.maxUnlocked = Math.max(1, Integer.parseInt(text.trim()));
        } catch (NumberFormatException ignored) {
            // mid-typing
        }
    }

    private void onGroupNameChanged(String text) {
        TreeMenuLayoutData.TreeGroup group = groupOfSelection();
        if (group == null) return;
        group.name = text;
    }

    private void syncAndUpdate(){
        syncMaxUnlockedBox();
        syncGroupNameBox();
        updateConditionalPanels();
    }

    private void syncGroupNameBox() {
        if (groupNameBox == null) return;
        TreeMenuLayoutData.TreeGroup group = groupOfSelection();
        groupNameBox.setValue(group != null ? group.name : "");
    }

    private void syncMaxUnlockedBox() {
        if (maxUnlockedBox == null) return;
        TreeMenuLayoutData.TreeGroup group = groupOfSelection();
        maxUnlockedBox.setValue(group != null ? String.valueOf(group.maxUnlocked) : "");
    }

    private final Set<String> lockedTreeIds = new HashSet<>();

    private void computeLockedTrees() {
        lockedTreeIds.clear();
        var player = Minecraft.getInstance().player;
        if (player == null) return;

        for (TreeMenuLayoutData.TreeEntry entry : layout.trees) {
            if (TreeMenuCanvasUtil.hasUnmetRequirements(player, entry)) {
                lockedTreeIds.add(entry.id);
            }
        }

        Set<ResourceLocation> learnedIds = new HashSet<>();
        for (var skill : daripher.skilltree.capability.skill.PlayerSkillsProvider.get(player).getPlayerSkills()) {
            learnedIds.add(skill.getId());
        }

        for (TreeMenuLayoutData.TreeGroup group : layout.groups) {
            List<String> unlockedInGroup = new ArrayList<>();
            for (String treeId : group.treeIds) {
                var tree = SkillTreesReloader.getSkillTreeById(ResourceLocation.parse(treeId));
                if (tree.getSkillIds().stream().anyMatch(learnedIds::contains)) {
                    unlockedInGroup.add(treeId);
                }
            }
            if (unlockedInGroup.size() >= group.maxUnlocked) {
                for (String treeId : group.treeIds) {
                    if (!unlockedInGroup.contains(treeId)) lockedTreeIds.add(treeId);
                }
            }
        }
    }

    private void updateConditionalPanels() {
        boolean showGroup = selectedTrees.size() > 1;
        boolean showReq = selectedTrees.size() == 1;

        groupButton.visible = groupButton.active = showGroup;
        ungroupButton.visible = ungroupButton.active = showGroup;
        maxUnlockedBox.visible = maxUnlockedBox.active = showGroup;
        groupNameBox.visible = groupNameBox.active = showGroup;

        advancementBox.visible = advancementBox.active = showReq;
        addAdvancementButton.visible = addAdvancementButton.active = showReq;
        itemBox.visible = itemBox.active = showReq;
        itemCountBox.visible = itemCountBox.active = showReq;
        addItemButton.visible = addItemButton.active = showReq;

        rebuildRequirementList();
    }

    private TreeMenuLayoutData.TreeEntry singleSelectedTree() {
        return selectedTrees.size() == 1 ? selectedTrees.iterator().next() : null;
    }

    private void updateAdvancementSuggestions(String text) {
        advancementSuggestionIndex = 0;
        if (text.isBlank()) { advancementSuggestions = List.of(); return; }
        String lower = text.toLowerCase(Locale.ROOT);
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) { advancementSuggestions = List.of(); return; }
        advancementSuggestions = connection.getAdvancements().getTree().nodes().stream()
                .map(node -> node.holder().id().toString())
                .filter(id -> id.toLowerCase(Locale.ROOT).contains(lower))
                .sorted()
                .limit(8)
                .toList();
    }

    private void updateItemSuggestions(String text) {
        itemSuggestionIndex = 0;
        if (text.isBlank()) { itemSuggestions = List.of(); return; }
        String lower = text.toLowerCase(Locale.ROOT);
        itemSuggestions = net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet().stream()
                .map(ResourceLocation::toString)
                .filter(id -> id.toLowerCase(Locale.ROOT).contains(lower))
                .sorted()
                .limit(8)
                .toList();
    }

    private void addAdvancementRequirement() {
        var tree = singleSelectedTree();
        String text = advancementBox.getValue().trim();
        if (tree == null || text.isEmpty()) return;
        var req = new TreeMenuLayoutData.TreeRequirement();
        req.type = "advancement";
        req.advancementId = text;
        tree.requirements.add(req);
        advancementBox.setValue("");
        advancementSuggestions = List.of();
        rebuildRequirementList();
    }

    private void addItemRequirement() {
        var tree = singleSelectedTree();
        String text = itemBox.getValue().trim();
        if (tree == null || text.isEmpty()) return;
        int count;
        try { count = Math.max(1, Integer.parseInt(itemCountBox.getValue().trim())); }
        catch (NumberFormatException e) { count = 1; }
        var req = new TreeMenuLayoutData.TreeRequirement();
        req.type = "item";
        req.itemId = text;
        req.count = count;
        tree.requirements.add(req);
        itemBox.setValue("");
        itemSuggestions = List.of();
        rebuildRequirementList();
    }

    private void rebuildRequirementList() {
        for (Button b : requirementRemoveButtons) this.removeWidget(b);
        requirementRemoveButtons.clear();

        var tree = singleSelectedTree();
        if (tree == null) return;

        int y = REQUIREMENTS_LIST_Y;
        for (TreeMenuLayoutData.TreeRequirement req : new ArrayList<>(tree.requirements)) {
            Button remove = this.addRenderableWidget(Button.builder(Component.literal("X"), b -> {
                    tree.requirements.remove(req);
                    rebuildRequirementList();
                })
                .bounds(10, y, 16, 16).build());
            requirementRemoveButtons.add(remove);
            y += 18;
        }
    }
}