package com.b1n_ry.yigd.client.gui;

import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.api.YigdApi;
import com.b1n_ry.yigd.core.DeadPlayerData;
import com.b1n_ry.yigd.core.PacketIdentifiers;
import com.b1n_ry.yigd.core.YigdCommand;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public class GraveViewScreen extends Screen {
    private final Identifier GRAVE_VIEW_TEXTURE = new Identifier("yigd", "textures/gui/grave_view.png");
    private final Identifier SELECT_ELEMENT_TEXTURE = new Identifier("yigd", "textures/gui/select_elements.png");

    private final DeadPlayerData data;

    private ItemStack hoveredStack = null;
    private boolean mouseIsClicked = false;
    private String hoveredButton = null;
    private final int modItemSize;
    private final int xpLevels;
    private final Screen previousScreen;

    public static boolean getKeysFromGui = false;
    public static boolean unlockableGraves = false;
    public static List<UUID> unlockedGraves = new ArrayList<>();

    public GraveViewScreen(DeadPlayerData data, @Nullable Screen previousScreen) {
        super(data.graveOwner != null ? new TranslatableText("text.yigd.gui.grave_view.title", data.graveOwner.getName()) : new TranslatableText("text.yigd.gui.grave_view.title.missing"));
        this.data = data;
        this.previousScreen = previousScreen;

        int size = 0;
        for (int i = 41; i < data.inventory.size(); i++) {
            if (data.inventory.get(i).isEmpty()) continue;
            size++;
        }
        for (int i = 0; i < data.modInventories.size(); i++) {
            YigdApi yigdApi = Yigd.apiMods.get(i);
            size += yigdApi.getInventorySize(data.modInventories.get(i));
        }
        this.modItemSize = size;

        int points = data.xp;
        int i;
        for (i = 0; points >= 0; i++) {
            if (i < 16) points -= (2 * i) + 7;
            else if (i < 31) points -= (5 * i) - 38;
            else points -= (9 * i) - 158;
        }
        this.xpLevels = i - 1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredButton != null) {
            mouseIsClicked = true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        mouseIsClicked = false;
        if (button == 0 && hoveredButton != null && client != null) {
            switch (hoveredButton) {
                case "restore" -> {
                    if (client.player != null && YigdCommand.hasPermission(client.player, "yigd.command.restore")) {
                        PacketByteBuf buf = PacketByteBufs.create()
                                .writeUuid(this.data.graveOwner.getId())
                                .writeUuid(this.data.id);

                        ClientPlayNetworking.send(PacketIdentifiers.RESTORE_INVENTORY, buf);

                        this.close();
                    }
                }
                case "delete" -> {
                    if (client.player != null && YigdCommand.hasPermission(client.player, "yigd.command.delete")) {
                        PacketByteBuf buf = PacketByteBufs.create()
                                .writeUuid(this.data.graveOwner.getId())
                                .writeUuid(this.data.id);

                        ClientPlayNetworking.send(PacketIdentifiers.DELETE_GRAVE, buf);

                        this.close();
                    }
                }
                case "rob" -> {
                    if (client.player != null && YigdCommand.hasPermission(client.player, "yigd.command.rob")) {
                        PacketByteBuf buf = PacketByteBufs.create()
                                .writeString(this.data.graveOwner.getName())
                                .writeUuid(this.data.graveOwner.getId())
                                .writeUuid(this.data.id);

                        ClientPlayNetworking.send(PacketIdentifiers.ROB_GRAVE, buf);

                        this.close();
                    }
                }
                case "give_key" -> {
                    PacketByteBuf buf = PacketByteBufs.create()
                            .writeUuid(this.data.graveOwner.getId())
                            .writeUuid(this.data.id);

                    ClientPlayNetworking.send(PacketIdentifiers.GIVE_KEY_ITEM, buf);

                    this.close();
                }
                case "toggleLocked" -> {
                    boolean graveIsLocked;
                    if (unlockedGraves.contains(this.data.id)) {
                        unlockedGraves.remove(this.data.id);
                        graveIsLocked = true;
                    } else {
                        unlockedGraves.add(this.data.id);
                        graveIsLocked = false;
                    }

                    PacketByteBuf buf = PacketByteBufs.create()
                            .writeUuid(this.data.id);
                    buf.writeBoolean(graveIsLocked);

                    ClientPlayNetworking.send(PacketIdentifiers.SET_GRAVE_LOCK, buf);
                }
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        final int screenWidth = 176;
        final int screenHeight = 174;
        final int originX = this.width / 2;
        final int originY = this.height / 2;

        RenderSystem.setShaderTexture(0, GRAVE_VIEW_TEXTURE);
        drawTexture(matrices, originX - screenWidth / 2, originY - screenHeight / 2, 0, 0, screenWidth, screenHeight);

        int yOffset = 0;

        this.hoveredStack = null;
        this.hoveredButton = null;

        if (unlockableGraves) {
            RenderSystem.setShaderTexture(0, SELECT_ELEMENT_TEXTURE);

            if (mouseX > originX + screenWidth / 2 + 1 && mouseX < originX + screenWidth / 2 + 52 && mouseY > originY - screenHeight / 2 + yOffset && mouseY < originY - screenHeight / 2 + 15 + yOffset) {
                hoveredButton = "toggleLocked";
            }
            if (hoveredButton != null && hoveredButton.equals("toggleLocked") && mouseIsClicked) {
                drawTexture(matrices, originX + screenWidth / 2 + 1, originY - screenHeight / 2 + yOffset, 182, 15, 51, 30);
            } else {
                drawTexture(matrices, originX + screenWidth / 2 + 1, originY - screenHeight / 2 + yOffset, 182, 0, 51, 15);
            }

            if (unlockedGraves.contains(this.data.id)) {
                textRenderer.draw(matrices, new TranslatableText("text.yigd.word.lock"), originX + screenWidth / 2f + 5, originY - screenHeight / 2f + 4 + yOffset, 0x000000);
            } else {
                textRenderer.draw(matrices, new TranslatableText("text.yigd.word.unlock"), originX + screenWidth / 2f + 5, originY - screenHeight / 2f + 4 + yOffset, 0x000000);
            }
        }

        if (client != null && client.player != null && YigdCommand.hasPermission(client.player, "yigd.command.restore")) {
            RenderSystem.setShaderTexture(0, SELECT_ELEMENT_TEXTURE);
            if (mouseX > originX + screenWidth / 2 + 1 && mouseX < originX + screenWidth / 2 + 52 && mouseY > originY - screenHeight / 2 + yOffset && mouseY < originY - screenHeight / 2 + 15 + yOffset) {
                hoveredButton = "restore";
            }
            if (hoveredButton != null && hoveredButton.equals("restore") && mouseIsClicked) {
                drawTexture(matrices, originX + screenWidth / 2 + 1, originY - screenHeight / 2 + yOffset, 182, 15, 51, 30);
            } else {
                drawTexture(matrices, originX + screenWidth / 2 + 1, originY - screenHeight / 2 + yOffset, 182, 0, 51, 15);
            }
            textRenderer.draw(matrices, new TranslatableText("text.yigd.word.restore"), originX + screenWidth / 2f + 5, originY - screenHeight / 2f + 4 + yOffset, 0x000000);
            yOffset += 16;
        }
        if (client != null && client.player != null && YigdCommand.hasPermission(client.player, "yigd.command.delete")) {
            RenderSystem.setShaderTexture(0, SELECT_ELEMENT_TEXTURE);
            if (mouseX > originX + screenWidth / 2 + 1 && mouseX < originX + screenWidth / 2 + 52 && mouseY > originY - screenHeight / 2 + yOffset && mouseY < originY - screenHeight / 2 + 15 + yOffset) {
                hoveredButton = "delete";
            }
            if (hoveredButton != null && hoveredButton.equals("delete") && mouseIsClicked) {
                drawTexture(matrices, originX + screenWidth / 2 + 1, originY - screenHeight / 2 + yOffset, 182, 15, 51, 30);
            } else {
                drawTexture(matrices, originX + screenWidth / 2 + 1, originY - screenHeight / 2 + yOffset, 182, 0, 51, 15);
            }
            textRenderer.draw(matrices, new TranslatableText("text.yigd.word.delete"), originX + screenWidth / 2f + 5, originY - screenHeight / 2f + 4 + yOffset, 0x000000);
            yOffset += 16;
        }
        if (client != null && client.player != null && YigdCommand.hasPermission(client.player, "yigd.command.rob")) {
            RenderSystem.setShaderTexture(0, SELECT_ELEMENT_TEXTURE);
            if (mouseX > originX + screenWidth / 2 + 1 && mouseX < originX + screenWidth / 2 + 52 && mouseY > originY - screenHeight / 2 + yOffset && mouseY < originY - screenHeight / 2 + 15 + yOffset) {
                hoveredButton = "rob";
            }
            if (hoveredButton != null && hoveredButton.equals("rob") && mouseIsClicked) {
                drawTexture(matrices, originX + screenWidth / 2 + 1, originY - screenHeight / 2 + yOffset, 182, 15, 51, 30);
            } else {
                drawTexture(matrices, originX + screenWidth / 2 + 1, originY - screenHeight / 2 + yOffset, 182, 0, 51, 15);
            }
            textRenderer.draw(matrices, new TranslatableText("text.yigd.word.rob"), originX + screenWidth / 2f + 5, originY - screenHeight / 2f + 4 + yOffset, 0x000000);
            yOffset += 16;
        }

        if (getKeysFromGui) {
            RenderSystem.setShaderTexture(0, SELECT_ELEMENT_TEXTURE);
            if (mouseX > originX + screenWidth / 2 + 1 && mouseX < originX + screenWidth / 2 + 52 && mouseY > originY - screenHeight / 2 + yOffset && mouseY < originY - screenHeight / 2 + 15 + yOffset) {
                hoveredButton = "give_key";
            }
            if (hoveredButton != null && hoveredButton.equals("give_key") && mouseIsClicked) {
                drawTexture(matrices, originX + screenWidth / 2 + 1, originY - screenHeight / 2 + yOffset, 182, 15, 51, 30);
            } else {
                drawTexture(matrices, originX + screenWidth / 2 + 1, originY - screenHeight / 2 + yOffset, 182, 0, 51, 15);
            }
            textRenderer.draw(matrices, new TranslatableText("text.yigd.word.give_key"), originX + screenWidth / 2f + 5, originY - screenHeight / 2f + 4 + yOffset, 0x000000);
        }

        if (client != null) {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = data.inventory.get(i);
                int slotX = originX - screenWidth / 2 + 8 + i * 18;
                int slotY = originY - screenHeight / 2 + 150;

                renderSlot(matrices, stack, slotX, slotY, mouseX, mouseY);
            }
            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 9; x++) {
                    int slot = y * 9 + x + 9;
                    ItemStack stack = data.inventory.get(slot);
                    int slotX = originX - screenWidth / 2 + 8 + x * 18;
                    int slotY = originY - screenHeight / 2 + 92 + y * 18;

                    renderSlot(matrices, stack, slotX, slotY, mouseX, mouseY);
                }
            }
            for (int i = 36; i < 40; i++) {
                ItemStack stack = data.inventory.get(i);
                int slotX = originX - screenWidth / 2 + 8;
                int slotY = originY - screenHeight / 2 + 70 - (i - 36) * 18;

                renderSlot(matrices, stack, slotX, slotY, mouseX, mouseY);
            }

            int slotX = originX - screenWidth / 2 + 77;
            int slotY = originY - screenHeight / 2 + 70;
            renderSlot(matrices, data.inventory.get(40), slotX, slotY, mouseX, mouseY);


            if (hoveredStack != null && !hoveredStack.isEmpty() && client.player != null) {
                List<Text> tooltip = getTooltipFromItem(hoveredStack);
                renderTooltip(matrices, tooltip, mouseX, mouseY);
            }
        }

        String string = "death.attack." + data.deathSource.name;
        Text deathMsg = new TranslatableText(string, data.graveOwner.getName());

        super.render(matrices, mouseX, mouseY, delta);

        float textX = originX - screenWidth / 2f + 80f;
        textRenderer.draw(matrices, deathMsg, originX - screenWidth / 2f + 7f, originY - screenHeight / 2f + 5f, 0xAA0000);
        textRenderer.draw(matrices, new TranslatableText("text.yigd.gui.grave_view.dim_name", data.dimensionName), textX, originY - screenHeight / 2f + 16, 0x0055c4);
        textRenderer.draw(matrices, data.gravePos.getX() + " " + data.gravePos.getY() + " " + data.gravePos.getZ(), textX, originY - screenHeight / 2f + 28f, 0xBB00BB);
        if (data.modInventories.size() > 0 || data.inventory.size() > 41) {
            // Kind of a huge block of code made to print strings with break row
            String modItemsText = new TranslatableText("text.yigd.gui.grave_view.mod_inv_items", modItemSize).getString();
            String[] words = modItemsText.split(" ");
            StringBuilder cachedString = new StringBuilder();
            int stringLength = 0;
            int wordsOnRow = 0;
            int row = 0;
            int maxLength = screenWidth - 75;
            for (String word : words) {
                stringLength += textRenderer.getWidth(word);
                if (stringLength >= maxLength && wordsOnRow > 0) {
                    textRenderer.draw(matrices, cachedString.toString(), textX, originY - screenHeight / 2f + 40f + 9f * row++, 0x555555);
                    cachedString = new StringBuilder();
                    wordsOnRow = 0;
                    stringLength = 0;
                }

                if (wordsOnRow <= 0) {
                    cachedString.append(word);
                } else {
                    cachedString.append(" ").append(word);
                }
                wordsOnRow++;
            }
            textRenderer.draw(matrices, cachedString.toString(), textX, originY - screenHeight / 2f + 40f + 9f * row, 0x555555);
        }
        textRenderer.draw(matrices, new TranslatableText("text.yigd.gui.grave_view.level_count", this.xpLevels), textX + 18f, originY - screenHeight / 2f + 77f, 0x299608);

        if (client != null && client.player != null) {
            int playerX = originX - screenWidth / 2 + 50;
            PlayerEntity otherPlayer = client.player.world.getPlayerByUuid(data.graveOwner.getId());
            LivingEntity renderedEntity;
            if (otherPlayer != null) {
                renderedEntity = otherPlayer;
            } else {
                renderedEntity = client.player;
            }
            int playerY = originY - screenHeight / 2 + 51;
            InventoryScreen.drawEntity(playerX, playerY + 30, 30, (float) playerX - mouseX, (float) (playerY - 20) - mouseY, renderedEntity);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (client != null && client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
            this.close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (previousScreen == null) {
                this.close();
                return true;
            }
            if (client != null) {
                client.setScreen(previousScreen);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void renderSlot(MatrixStack matrices, ItemStack stack, int x, int y, int mouseX, int mouseY) {
        if (mouseX > x && mouseX < x + 16 && mouseY > y && mouseY < y + 16) {
            fill(matrices, x, y, x + 16, y + 16, 0xFFBBBB);
            this.hoveredStack = stack.copy();
        }

        itemRenderer.renderGuiItemIcon(stack, x, y);
        itemRenderer.renderGuiItemOverlay(textRenderer, stack, x, y);
    }
}
