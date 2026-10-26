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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Core Defender 관리 화면. 표시할 내용은 전부 서버(KubeJS)가 보낸 상태 JSON 에서 온다.
 * <p>
 * 상태 JSON 구조는 kubejs/server_scripts/coredefender/menu.js 의 cdBuildMenuState 참고.
 */
public class CoreDefenderScreen extends Screen {
    private static final int WIDTH = 360;
    private static final int HEIGHT = 222;
    private static final int SIDEBAR = 120;
    private static final int REFRESH_INTERVAL = 20;
    private static final String[] SIDES = {"N", "E", "S", "W"};

    private static final int COLOR_PANEL = 0xF0141821;
    private static final int COLOR_SIDEBAR = 0xF01B2030;
    private static final int COLOR_BORDER = 0xFF4A5068;
    private static final int COLOR_BAR_BG = 0xFF2A2F40;
    private static final int COLOR_TEXT = 0xFFE0E0E0;
    private static final int COLOR_MUTED = 0xFF9AA0B4;
    private static final int COLOR_TITLE = 0xFFFFC94D;
    private static final int COLOR_GOOD = 0xFF4CC26B;
    private static final int COLOR_WARN = 0xFFE0B23A;
    private static final int COLOR_BAD = 0xFFD9443A;

    private JsonObject state;
    private int left;
    private int top;
    private int refreshTicks;
    private int advanceConfirmationTicks;
    /**
     * 툴팁 표시용으로 이번 프레임에 그린 아이템/구간 위치
     */
    private final List<ItemSlot> itemSlots = new ArrayList<>();
    private final List<TextSlot> textSlots = new ArrayList<>();

    private record ItemSlot(ItemStack stack, int x, int y) {
    }

    private record TextSlot(Component text, int x, int y, int w, int h) {
    }

    public CoreDefenderScreen(String json) {
        super(Component.translatable("screen.coredefender.menu"));
        this.state = parse(json);
    }

    public void update(String json) {
        this.state = parse(json);
        this.rebuildWidgets();
    }

    private static JsonObject parse(String json) {
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private static void send(String action, @Nullable String arg) {
        PacketDistributor.sendToServer(new MenuActionPayload(action, arg == null ? "" : arg));
    }


    @Override
    protected void init() {
        this.left = (this.width - WIDTH) / 2;
        this.top = (this.height - HEIGHT) / 2;
        this.initSideUpgradeButtons();
        this.initActionButtons();
        this.addRenderableWidget(Button.builder(Component.translatable("screen.coredefender.storage"), b -> send("storage", ""))
            .bounds(this.left + 8, this.top + HEIGHT - 26, SIDEBAR - 16, 20)
            .build());
    }

    private int columnX(int column) {
        return this.left + 8 + column * 27;
    }

    /**
     * 변별 강화 버튼 (성벽 격자 각 열 위)
     */
    private void initSideUpgradeButtons() {
        JsonArray sides = array(obj(this.state, "wall"), "sides");
        for (int c = 0; c < SIDES.length; c++) {
            JsonObject side = findSide(sides, SIDES[c]);
            JsonObject upgrade = obj(side, "upgrade");
            String sideId = SIDES[c];
            Button button = Button.builder(Component.literal("▲"), b -> send("upgrade", sideId))
                .bounds(this.columnX(c), this.top + 32, 24, 12)
                .tooltip(Tooltip.create(costTooltip(upgrade, Component.translatable("screen.coredefender.upgrade"))))
                .build();
            button.active = bool(upgrade, "enabled");
            this.addRenderableWidget(button);
        }
    }

    private void initActionButtons() {
        int x = this.left + SIDEBAR + 6;
        int y = this.top + HEIGHT - 26;
        int gap = 4;
        int buttonWidth = (WIDTH - SIDEBAR - 12 - gap * 3) / 4;

        this.addAction(x, y, buttonWidth, "advance", this.advanceConfirmationTicks > 0 ? "screen.coredefender.advance_confirm" : "screen.coredefender.advance", obj(this.state, "advance"), null);
        this.addAction(x + (buttonWidth + gap), y, buttonWidth, "repair", "screen.coredefender.repair", obj(this.state, "repair"),
            costTooltip(obj(this.state, "repair"), Component.translatable("screen.coredefender.repair")));
        this.addAction(x + (buttonWidth + gap) * 2, y, buttonWidth, "expand", "screen.coredefender.expand", obj(this.state, "expand"),
            costTooltip(obj(this.state, "expand"), Component.translatable("screen.coredefender.expand")));

        String travel = str(obj(this.state, "travel"), "action");
        Button travelButton = Button.builder(Component.translatable("screen.coredefender." + ("leave".equals(travel) ? "leave" : "enter")), b -> {
                send(travel, "");
                this.onClose();
            })
            .bounds(x + (buttonWidth + gap) * 3, y, buttonWidth, 20)
            .build();
        travelButton.active = !travel.isEmpty();
        this.addRenderableWidget(travelButton);
    }

    private void addAction(int x, int y, int w, String action, String labelKey, JsonObject info, @Nullable Component tooltip) {
        Button button = Button.builder(Component.translatable(labelKey), b -> {
                if ("advance".equals(action) && this.advanceConfirmationTicks == 0) {
                    this.advanceConfirmationTicks = 100;
                    this.rebuildWidgets();
                    return;
                }

                this.advanceConfirmationTicks = 0;
                send(action, "");
            })
            .bounds(x, y, w, 20)
            .build();
        button.active = bool(info, "enabled");
        Component reason = str(info, "reason").isEmpty() ? null : Component.literal(str(info, "reason")).withStyle(ChatFormatting.RED);
        if (tooltip != null || reason != null) {
            MutableComponent text = Component.empty();
            if (tooltip != null) {
                text.append(tooltip);
            }

            if (reason != null) {
                text.append(tooltip != null ? "\n" : "").append(reason);
            }

            button.setTooltip(Tooltip.create(text));
        }

        this.addRenderableWidget(button);
    }

    /**
     * "<label>: <아이템> 보유/필요" (비용 정보가 없으면 label 만)
     */
    private static Component costTooltip(JsonObject info, Component label) {
        if (!info.has("item")) {
            return label;
        }

        ItemStack stack = stack(str(info, "item"), 1);
        int have = (int) num(info, "have");
        int count = (int) num(info, "count");
        return label.copy().append(": ").append(stack.getHoverName())
            .append(Component.literal(" " + have + "/" + count).withStyle(have >= count ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    @Override
    public void tick() {
        if (this.advanceConfirmationTicks > 0 && --this.advanceConfirmationTicks == 0) {
            this.rebuildWidgets();
        }

        // 웨이브 진행 상황/성벽 HP 를 주기적으로 갱신
        if (++this.refreshTicks >= REFRESH_INTERVAL) {
            this.refreshTicks = 0;
            send("refresh", "");
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }


    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(this.left - 1, this.top - 1, this.left + WIDTH + 1, this.top + HEIGHT + 1, COLOR_BORDER);
        g.fill(this.left, this.top, this.left + WIDTH, this.top + HEIGHT, COLOR_PANEL);
        g.fill(this.left, this.top, this.left + SIDEBAR, this.top + HEIGHT, COLOR_SIDEBAR);
        g.fill(this.left + SIDEBAR, this.top, this.left + SIDEBAR + 1, this.top + HEIGHT, COLOR_BORDER);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        this.itemSlots.clear();
        this.textSlots.clear();

        this.renderWallGrid(g);

        int x = this.left + SIDEBAR + 8;
        int contentWidth = WIDTH - SIDEBAR - 16;

        g.drawString(this.font, Component.literal(str(this.state, "title")), x, this.top + 8, COLOR_TITLE);
        Component progress = Component.literal(str(this.state, "progress"));
        g.drawString(this.font, progress, x + contentWidth - this.font.width(progress), this.top + 8, COLOR_MUTED);

        int y = this.renderWallTotal(g, x, this.top + 22, contentWidth);
        y = this.renderWave(g, x, y + 4, contentWidth);
        this.renderRewards(g, x, y + 4, contentWidth);

        for (ItemSlot slot : this.itemSlots) {
            if (mouseX >= slot.x && mouseX < slot.x + 16 && mouseY >= slot.y && mouseY < slot.y + 16) {
                g.renderTooltip(this.font, slot.stack, mouseX, mouseY);
                return;
            }
        }

        for (TextSlot slot : this.textSlots) {
            if (mouseX >= slot.x && mouseX < slot.x + slot.w && mouseY >= slot.y && mouseY < slot.y + slot.h) {
                g.renderTooltip(this.font, slot.text, mouseX, mouseY);
                return;
            }
        }
    }

    /**
     * 왼쪽: 변(N/E/S/W) x 구간 격자. 칸 색 = HP 비율, 마우스를 올리면 HP 표시.
     */
    private void renderWallGrid(GuiGraphics g) {
        JsonObject wall = obj(this.state, "wall");
        int chunks = (int) num(wall, "interiorChunks");
        g.drawString(this.font, Component.translatable("screen.coredefender.wall").append(" " + chunks + "x" + chunks),
            this.left + 8, this.top + 8, COLOR_TITLE);

        JsonArray sides = array(wall, "sides");
        JsonArray segments = array(wall, "segments");
        int perSide = Math.max(1, (int) num(wall, "segmentsPerSide"));
        int gridTop = this.top + 48;
        int cellHeight = Math.min(16, (HEIGHT - 56 - 32) / perSide);

        for (int c = 0; c < SIDES.length; c++) {
            int cx = this.columnX(c);
            int level = (int) num(findSide(sides, SIDES[c]), "level");
            g.drawString(this.font, SIDES[c] + level, cx + 2, this.top + 21, COLOR_MUTED);
            for (int i = 0; i < perSide; i++) {
                JsonObject segment = findSegment(segments, SIDES[c] + i);
                int cy = gridTop + i * cellHeight;
                double hp = num(segment, "hp");
                double max = Math.max(1, num(segment, "maxHp"));
                boolean collapsed = bool(segment, "collapsed");
                double ratio = collapsed ? 0 : hp / max;
                int color = collapsed ? 0xFF5A1010 : ratio > 0.5 ? COLOR_GOOD : ratio > 0.25 ? COLOR_WARN : COLOR_BAD;
                g.fill(cx, cy, cx + 24, cy + cellHeight - 2, COLOR_BAR_BG);
                g.fill(cx + 1, cy + 1, cx + 1 + (int) (22 * ratio), cy + cellHeight - 3, color);
                Component tip = Component.literal(SIDES[c] + i + "  ")
                    .append(collapsed ? Component.translatable("screen.coredefender.wall_collapsed").withStyle(ChatFormatting.RED)
                        : Component.literal((int) hp + " / " + (int) max));
                this.textSlots.add(new TextSlot(tip, cx, cy, 24, cellHeight - 2));
            }
        }
    }

    private int renderWallTotal(GuiGraphics g, int x, int y, int width) {
        double hp = 0;
        double max = 0;
        boolean collapsed = false;
        for (JsonElement el : array(obj(this.state, "wall"), "segments")) {
            JsonObject segment = el.getAsJsonObject();
            hp += bool(segment, "collapsed") ? 0 : num(segment, "hp");
            max += num(segment, "maxHp");
            collapsed |= bool(segment, "collapsed");
        }

        double ratio = max > 0 ? hp / max : 0;
        g.drawString(this.font, Component.translatable("screen.coredefender.wall_total"), x, y, COLOR_TEXT);
        int barY = y + 11;
        g.fill(x, barY, x + width, barY + 10, COLOR_BAR_BG);
        int color = collapsed ? COLOR_BAD : ratio > 0.5 ? COLOR_GOOD : ratio > 0.25 ? COLOR_WARN : COLOR_BAD;
        g.fill(x + 1, barY + 1, x + 1 + (int) ((width - 2) * ratio), barY + 9, color);
        Component label = collapsed ? Component.translatable("screen.coredefender.wall_collapsed")
            : Component.literal((int) hp + " / " + (int) max);
        g.drawCenteredString(this.font, label, x + width / 2, barY + 1, 0xFFFFFFFF);
        return barY + 10;
    }


    private int renderWave(GuiGraphics g, int x, int y, int width) {
        JsonObject wave = obj(this.state, "wave");
        JsonObject active = this.state.has("active") && this.state.get("active").isJsonObject() ? this.state.getAsJsonObject("active") : null;

        g.drawString(this.font, Component.literal(str(wave, "label")), x, y, COLOR_TITLE);
        Component mult = Component.literal("x" + str(wave, "mult"));
        g.drawString(this.font, mult, x + width - this.font.width(mult), y, COLOR_MUTED);
        y += 12;

        if (active != null) {
            double total = Math.max(1, num(active, "total"));
            double remaining = num(active, "remaining");
            g.fill(x, y, x + width, y + 8, COLOR_BAR_BG);
            g.fill(x + 1, y + 1, x + 1 + (int) ((width - 2) * (remaining / total)), y + 7, 0xFFB04CE0);
            y += 10;
            g.drawString(this.font, Component.translatable("screen.coredefender.remaining", (int) remaining, (int) total), x, y, COLOR_TEXT);
            y += 12;
        }

        g.drawString(this.font, Component.translatable("screen.coredefender.enemies"), x, y, COLOR_MUTED);
        y += 11;

        JsonArray mobs = array(wave, "mobs");
        int columns = mobs.size() > 3 ? 2 : 1;
        int columnWidth = width / columns;
        int rows = Math.max(1, (mobs.size() + columns - 1) / columns);
        for (int i = 0; i < mobs.size(); i++) {
            JsonObject mob = mobs.get(i).getAsJsonObject();
            int mx = x + (i / rows) * columnWidth;
            int my = y + (i % rows) * 18;
            EntityType<?> type = entityType(str(mob, "type"));

            SpawnEggItem egg = type == null ? null : SpawnEggItem.byId(type);
            this.drawItem(g, egg != null ? new ItemStack(egg) : new ItemStack(Items.BARRIER), mx, my);

            MutableComponent name = (type != null ? type.getDescription().copy() : Component.literal(str(mob, "type")))
                .append(" x" + (int) num(mob, "count"));
            if (!str(mob, "boss").isEmpty()) {
                name = Component.translatable("entity.coredefender." + str(mob, "boss"), name).withStyle(ChatFormatting.GOLD);
            }

            if (bool(mob, "elite")) {
                name.append(Component.literal(" ").append(Component.translatable("screen.coredefender.elite")).withStyle(ChatFormatting.RED));
            }

            g.drawString(this.font, name, mx + 18, my + 4, COLOR_TEXT);
        }

        return y + rows * 18;
    }

    private void renderRewards(GuiGraphics g, int x, int y, int width) {
        g.drawString(this.font, Component.translatable("screen.coredefender.rewards"), x, y, COLOR_MUTED);
        y += 10;
        int ix = x;
        for (JsonElement el : array(obj(this.state, "wave"), "rewards")) {
            JsonObject reward = el.getAsJsonObject();
            if (ix + 18 > x + width) {
                break;
            }

            this.drawItem(g, stack(str(reward, "item"), (int) num(reward, "count")), ix, y);
            ix += 20;
        }
    }

    private void drawItem(GuiGraphics g, ItemStack stack, int x, int y) {
        g.renderItem(stack, x, y);
        g.renderItemDecorations(this.font, stack, x, y);
        this.itemSlots.add(new ItemSlot(stack, x, y));
    }


    private static JsonObject findSide(JsonArray sides, String side) {
        for (JsonElement el : sides) {
            if (side.equals(str(el.getAsJsonObject(), "side"))) {
                return el.getAsJsonObject();
            }
        }

        return new JsonObject();
    }

    private static JsonObject findSegment(JsonArray segments, String id) {
        for (JsonElement el : segments) {
            if (id.equals(str(el.getAsJsonObject(), "id"))) {
                return el.getAsJsonObject();
            }
        }

        return new JsonObject();
    }

    private static ItemStack stack(String id, int count) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return new ItemStack(Items.BARRIER);
        }

        return BuiltInRegistries.ITEM.getOptional(rl)
            .map(item -> new ItemStack(item, Math.max(1, count)))
            .orElseGet(() -> new ItemStack(Items.BARRIER));
    }

    @Nullable
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
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber() ? el.getAsDouble() : -1;
    }

    private static boolean bool(JsonObject parent, String key) {
        JsonElement el = parent.get(key);
        return el != null && el.isJsonPrimitive() && el.getAsBoolean();
    }
}
