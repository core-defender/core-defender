package lkd.namsic.coredefender.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lkd.namsic.coredefender.network.MenuActionPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Core Defender 메뉴. 표시할 내용은 전부 서버(KubeJS)가 보낸 상태 JSON 에서 온다.
 * <p>
 * 상태 JSON 구조는 kubejs/server_scripts/coredefender/menu.js 의 cdBuildMenuState 참고.
 */
public class CoreDefenderScreen extends Screen {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 204;
    private static final int SIDEBAR = 96;
    private static final int REFRESH_INTERVAL = 20;

    private static final int COLOR_PANEL = 0xF0141821;
    private static final int COLOR_SIDEBAR = 0xF01B2030;
    private static final int COLOR_BORDER = 0xFF4A5068;
    private static final int COLOR_BAR_BG = 0xFF2A2F40;
    private static final int COLOR_TEXT = 0xFFE0E0E0;
    private static final int COLOR_MUTED = 0xFF9AA0B4;
    private static final int COLOR_TITLE = 0xFFFFC94D;

    private JsonObject state;
    private int left;
    private int top;
    private int refreshTicks;
    /** 툴팁 표시용으로 이번 프레임에 그린 아이템 위치 */
    private final List<ItemSlot> itemSlots = new ArrayList<>();

    private record ItemSlot(ItemStack stack, int x, int y) {
    }

    public CoreDefenderScreen(String json) {
        super(Component.translatable("screen.coredefender.menu"));
        this.state = parse(json);
    }

    public void update(String json) {
        this.state = parse(json);
        rebuildWidgets();
    }

    private static JsonObject parse(String json) {
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private static void send(String action, String arg) {
        PacketDistributor.sendToServer(new MenuActionPayload(action, arg == null ? "" : arg));
    }

    // ---------- 위젯 ----------

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;

        initWorldButtons();
        initActionButtons();
    }

    private void initWorldButtons() {
        int y = top + 22;
        for (JsonElement el : array(state, "worlds")) {
            JsonObject world = el.getAsJsonObject();
            String id = str(world, "id");
            boolean unlocked = bool(world, "unlocked");
            boolean current = bool(world, "current");

            Button button = Button.builder(Component.literal(str(world, "name")), b -> {
                        send("enter", id);
                        onClose();
                    })
                    .bounds(left + 6, y, SIDEBAR - 12, 18)
                    .tooltip(Tooltip.create(Component.literal(str(world, "status"))))
                    .build();
            button.active = unlocked && !current;
            addRenderableWidget(button);
            y += 22;
        }
    }

    private void initActionButtons() {
        int x = left + SIDEBAR + 6;
        int y = top + HEIGHT - 26;
        int gap = 4;
        int buttonWidth = (WIDTH - SIDEBAR - 12 - gap * 2) / 3;

        JsonObject start = obj(state, "start");
        Button startButton = Button.builder(Component.translatable("screen.coredefender.start"), b -> send("start", ""))
                .bounds(x, y, buttonWidth, 20)
                .build();
        startButton.active = bool(start, "enabled");
        if (!startButton.active && !str(start, "reason").isEmpty()) {
            startButton.setTooltip(Tooltip.create(Component.literal(str(start, "reason"))));
        }
        addRenderableWidget(startButton);

        JsonObject repair = obj(state, "repair");
        Button repairButton = Button.builder(Component.translatable("screen.coredefender.repair"), b -> send("repair", ""))
                .bounds(x + buttonWidth + gap, y, buttonWidth, 20)
                .build();
        repairButton.active = bool(repair, "enabled");
        if (!str(repair, "reason").isEmpty()) {
            repairButton.setTooltip(Tooltip.create(Component.literal(str(repair, "reason"))));
        }
        addRenderableWidget(repairButton);

        JsonObject travel = obj(state, "travel");
        String action = str(travel, "action");
        Button travelButton = Button.builder(Component.translatable("screen.coredefender." + ("leave".equals(action) ? "leave" : "enter")), b -> {
                    send(action, str(travel, "world"));
                    onClose();
                })
                .bounds(x + (buttonWidth + gap) * 2, y, buttonWidth, 20)
                .build();
        travelButton.active = !action.isEmpty();
        addRenderableWidget(travelButton);
    }

    @Override
    public void tick() {
        // 웨이브 진행 상황(코어 HP, 남은 적)을 주기적으로 갱신
        if (++refreshTicks >= REFRESH_INTERVAL) {
            refreshTicks = 0;
            send("refresh", "");
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------- 렌더링 ----------

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left - 1, top - 1, left + WIDTH + 1, top + HEIGHT + 1, COLOR_BORDER);
        g.fill(left, top, left + WIDTH, top + HEIGHT, COLOR_PANEL);
        g.fill(left, top, left + SIDEBAR, top + HEIGHT, COLOR_SIDEBAR);
        g.fill(left + SIDEBAR, top, left + SIDEBAR + 1, top + HEIGHT, COLOR_BORDER);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        itemSlots.clear();

        g.drawString(font, Component.translatable("screen.coredefender.worlds"), left + 8, top + 8, COLOR_TITLE);

        int x = left + SIDEBAR + 8;
        int contentWidth = WIDTH - SIDEBAR - 16;

        // 헤더: 월드 이름 + 진행도
        g.drawString(font, Component.literal(str(state, "title")), x, top + 8, COLOR_TITLE);
        Component progress = Component.literal(str(state, "progress"));
        g.drawString(font, progress, x + contentWidth - font.width(progress), top + 8, COLOR_MUTED);

        int y = renderCoreBar(g, x, top + 22, contentWidth);
        y = renderWave(g, x, y + 6, contentWidth);
        renderRewards(g, x, y + 4, contentWidth);

        for (ItemSlot slot : itemSlots) {
            if (mouseX >= slot.x && mouseX < slot.x + 16 && mouseY >= slot.y && mouseY < slot.y + 16) {
                g.renderTooltip(font, slot.stack, mouseX, mouseY);
            }
        }
    }

    private int renderCoreBar(GuiGraphics g, int x, int y, int width) {
        JsonObject core = obj(state, "core");
        boolean broken = bool(core, "broken");
        double hp = num(core, "hp");
        double maxHp = Math.max(1, num(core, "maxHp"));
        double ratio = broken ? 0 : Math.clamp(hp / maxHp, 0, 1);

        g.drawString(font, Component.translatable("screen.coredefender.core"), x, y, COLOR_TEXT);
        int barY = y + 11;
        g.fill(x, barY, x + width, barY + 10, COLOR_BAR_BG);
        int color = ratio > 0.5 ? 0xFF4CC26B : ratio > 0.25 ? 0xFFE0B23A : 0xFFD9443A;
        g.fill(x + 1, barY + 1, x + 1 + (int) ((width - 2) * ratio), barY + 9, color);

        Component label = broken
                ? Component.translatable("screen.coredefender.core_broken")
                : Component.literal((int) Math.ceil(hp) + " / " + (int) Math.ceil(maxHp));
        g.drawCenteredString(font, label, x + width / 2, barY + 1, 0xFFFFFFFF);

        // 파괴 상태면 복구 비용 표시
        JsonObject repair = obj(state, "repair");
        if (broken && repair.has("item")) {
            ItemStack stack = stack(str(repair, "item"), 1);
            Component cost = Component.translatable("screen.coredefender.repair_cost")
                    .append(": ").append(stack.getHoverName())
                    .append(" " + (int) num(repair, "have") + "/" + (int) num(repair, "count"));
            int costX = x + width - font.width(cost) - 18;
            drawItem(g, stack, costX, y - 4);
            g.drawString(font, cost, costX + 18, y, num(repair, "have") >= num(repair, "count") ? 0xFF4CC26B : 0xFFD9443A);
        }
        return barY + 10;
    }

    private int renderWave(GuiGraphics g, int x, int y, int width) {
        JsonObject wave = obj(state, "wave");
        JsonObject active = state.has("active") && state.get("active").isJsonObject() ? state.getAsJsonObject("active") : null;

        g.drawString(font, Component.literal(str(wave, "label")), x, y, COLOR_TITLE);
        Component mult = Component.literal("x" + str(wave, "mult"));
        g.drawString(font, mult, x + width - font.width(mult), y, COLOR_MUTED);
        y += 12;

        if (active != null) {
            double total = Math.max(1, num(active, "total"));
            double remaining = num(active, "remaining");
            g.fill(x, y, x + width, y + 8, COLOR_BAR_BG);
            g.fill(x + 1, y + 1, x + 1 + (int) ((width - 2) * (remaining / total)), y + 7, 0xFFB04CE0);
            y += 10;
            g.drawString(font, Component.translatable("screen.coredefender.remaining", (int) remaining, (int) total), x, y, COLOR_TEXT);
            y += 12;
        }

        g.drawString(font, Component.translatable("screen.coredefender.enemies"), x, y, COLOR_MUTED);
        y += 11;

        JsonArray mobs = array(wave, "mobs");
        int columns = mobs.size() > 4 ? 2 : 1;
        int columnWidth = width / columns;
        int rows = (mobs.size() + columns - 1) / columns;
        for (int i = 0; i < mobs.size(); i++) {
            JsonObject mob = mobs.get(i).getAsJsonObject();
            int mx = x + (i / rows) * columnWidth;
            int my = y + (i % rows) * 18;
            EntityType<?> type = entityType(str(mob, "type"));

            SpawnEggItem egg = type == null ? null : SpawnEggItem.byId(type);
            drawItem(g, egg != null ? new ItemStack(egg) : new ItemStack(Items.BARRIER), mx, my);

            MutableComponent name = (type != null ? type.getDescription().copy() : Component.literal(str(mob, "type")))
                    .append(" x" + (int) num(mob, "count"));
            if (bool(mob, "elite")) {
                name.append(Component.literal(" ").append(Component.translatable("screen.coredefender.elite")).withStyle(ChatFormatting.RED));
            }
            g.drawString(font, name, mx + 18, my + 4, COLOR_TEXT);
        }
        return y + rows * 18;
    }

    private void renderRewards(GuiGraphics g, int x, int y, int width) {
        JsonObject wave = obj(state, "wave");
        g.drawString(font, Component.translatable("screen.coredefender.rewards"), x, y, COLOR_MUTED);
        y += 10;
        int ix = x;
        for (JsonElement el : array(wave, "rewards")) {
            JsonObject reward = el.getAsJsonObject();
            ItemStack stack = stack(str(reward, "item"), (int) num(reward, "count"));
            if (ix + 18 > x + width) {
                break;
            }
            drawItem(g, stack, ix, y);
            ix += 20;
        }
    }

    private void drawItem(GuiGraphics g, ItemStack stack, int x, int y) {
        g.renderItem(stack, x, y);
        g.renderItemDecorations(font, stack, x, y);
        itemSlots.add(new ItemSlot(stack, x, y));
    }

    // ---------- JSON / 레지스트리 헬퍼 ----------

    private static ItemStack stack(String id, int count) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return new ItemStack(Items.BARRIER);
        }
        return BuiltInRegistries.ITEM.getOptional(rl)
                .map(item -> new ItemStack(item, Math.max(1, count)))
                .orElseGet(() -> new ItemStack(Items.BARRIER));
    }

    private static EntityType<?> entityType(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null);
    }

    private static JsonObject obj(JsonObject parent, String key) {
        JsonElement el = parent.get(key);
        return el != null && el.isJsonObject() ? el.getAsJsonObject() : new JsonObject();
    }

    private static JsonArray array(JsonObject parent, String key) {
        JsonElement el = parent.get(key);
        return el != null && el.isJsonArray() ? el.getAsJsonArray() : new JsonArray();
    }

    private static String str(JsonObject parent, String key) {
        JsonElement el = parent.get(key);
        return el != null && el.isJsonPrimitive() ? el.getAsString() : "";
    }

    private static double num(JsonObject parent, String key) {
        JsonElement el = parent.get(key);
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber() ? el.getAsDouble() : 0;
    }

    private static boolean bool(JsonObject parent, String key) {
        JsonElement el = parent.get(key);
        return el != null && el.isJsonPrimitive() && el.getAsBoolean();
    }
}
