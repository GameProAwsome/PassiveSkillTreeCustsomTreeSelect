package com.pizzapizza.passiveskilltreecustomtreeselect;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class TreeMenuLayoutData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final int BUTTON_SIZE = 19; // matches vanilla BUTTONS_SIZE exactly

    public List<TreeEntry> trees = new ArrayList<>();
    public List<ImageEntry> images = new ArrayList<>();

    public static class TreeEntry {
        public String id;
        public float x, y; // world-space coordinates, unaffected by zoom
        public float scale = 1f;
        public List<TreeRequirement> requirements = new ArrayList<>();
    }

    public static class ImageEntry {
        public String texture;
        public float x, y, width, height;
        public float scale = 1f;
    }

    public static class TreeGroup {
        public String id = java.util.UUID.randomUUID().toString();
        public String name = ""; 
        public List<String> treeIds = new ArrayList<>();
        public int maxUnlocked = 1;
    }

    public static class TreeRequirement {
        public String type; // "item" or "advancement"
        public String itemId;        // used when type = "item", e.g. "minecraft:iron_ingot"
        public int count = 1;        // used when type = "item"
        public String advancementId; // used when type = "advancement", e.g. "minecraft:story/mine_diamond"
    }

    public List<TreeGroup> groups = new ArrayList<>();

    private static File getEditorFolder() {
        return new File(FMLPaths.GAMEDIR.get().toFile(), "skilltree/editor/data/skilltree/custom_tree_menu");
    }

    private static File getLayoutFile() {
        return new File(getEditorFolder(), "menu_page.json");
    }
    public static TreeMenuLayoutData getOrCreate() {
        File file = getLayoutFile();
        if (!file.exists()) {
            TreeMenuLayoutData fresh = new TreeMenuLayoutData(); // empty by default now
            save(fresh);
            return fresh;
        }
        try (FileReader reader = new FileReader(file)) {
            TreeMenuLayoutData loaded = GSON.fromJson(reader, TreeMenuLayoutData.class);
            return loaded != null ? loaded : new TreeMenuLayoutData();
        } catch (IOException e) {
            e.printStackTrace();
            return new TreeMenuLayoutData();
        }
    }

    public static void save(TreeMenuLayoutData data) {
        try {
            Files.createDirectories(getEditorFolder().toPath());
            try (FileWriter writer = new FileWriter(getLayoutFile())) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}