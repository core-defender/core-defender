package lkd.namsic.coredefender.client;

import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import lkd.namsic.coredefender.CoreDefender;
import lkd.namsic.coredefender.registry.ModTurrets;
import lkd.namsic.coredefender.turret.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

import java.util.*;

@EventBusSubscriber(modid = CoreDefender.MOD_ID, value = Dist.CLIENT)
public class TurretScreen extends AbstractContainerScreen<TurretMenu> {
    private int tab;
    private final Map<Integer, Button> actions = new HashMap<>();
    private static final String[] PRIORITIES = {"포탑에서 가까운 적", "성벽에서 가까운 적", "체력이 낮은 적", "체력이 높은 적"};
    private static final String[] SIDES = {"아래", "위", "북쪽", "남쪽", "서쪽", "동쪽"};
    private static final String[] PORTS = {"차단", "입력", "출력", "입출력"};
    private static final String[] ROLES = {"탄약", "연료", "증폭", "종자유", "용암"};

    public TurretScreen(TurretMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 400;
        this.imageHeight = 270;
        this.inventoryLabelY = 170;
    }

    @SubscribeEvent
    public static void screens(RegisterMenuScreensEvent event) {
        event.register(ModTurrets.MENU.get(), TurretScreen::new);
    }

    @SubscribeEvent
    public static void entities(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModTurrets.STONE_PROJECTILE.get(), ThrownItemRenderer::new);
    }

    private int d(int i) {
        return this.menu.data.get(i);
    }

    private String number(int i) {
        return String.format(Locale.ROOT, "%.2f", (i == 13 ? ((this.d(13) & 65535) | (this.d(45) << 16)) : this.d(i)) / 100.0);
    }

    @Override
    protected void init() {
        super.init();
        this.widgets();
    }

    private void widgets() {
        this.clearWidgets();
        this.actions.clear();
        String[] tabs = {"운용", "모듈", "설정"};
        for (int i = 0; i < 3; i++) {
            int next = i;
            this.addRenderableWidget(Button.builder(Component.literal(tabs[i]), b -> {
                this.tab = next;
                this.widgets();
            }).bounds(this.leftPos + 184 + i * 68, this.topPos + 9, 64, 18).build());
        }

        if (this.tab == 0 && this.d(0) != 3) {
            this.button(0, 184, 196, 200, 18, "");
            this.button(1, 184, 218, 200, 18, "");
        }

        if (this.tab == 2) {
            this.button(2, 184, 32, 200, 18, "");
            if (this.d(0) != 0) {
                this.button(3, 184, 53, 200, 18, "");
            }

            for (int i = 0; i < 6; i++) {
                this.button(10 + i, 184 + (i % 2) * 102, 78 + (i / 2) * 21, 98, 18, "");
            }

            for (int i = 0; i < 5; i++) {
                if (this.d(0) == 3 && i < 3) {
                    continue;
                }

                this.button(30 + i * 2, 330, 146 + i * 18, 24, 16, "−");
                this.button(31 + i * 2, 358, 146 + i * 18, 24, 16, "+");
            }
        }

        if (this.d(0) >= 2) {
            this.button(50, 14, 145, 66, 18, "기름 회수");
            this.button(51, 84, 145, 66, 18, "용암 회수");
        }

        this.refreshLabels();
    }

    private void button(int id, int x, int y, int w, int h, String label) {
        Button b = Button.builder(Component.literal(label), v -> {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
            }
        }).bounds(this.leftPos + x, this.topPos + y, w, h).build();
        this.actions.put(id, this.addRenderableWidget(b));
    }

    private void label(int id, String text) {
        Button b = this.actions.get(id);
        if (b != null) {
            b.setMessage(Component.literal(text));
        }
    }

    private void refreshLabels() {
        this.label(0, "우선순위: " + PRIORITIES[Math.clamp(this.d(14), 0, 3)]);
        this.label(1, "대상: " + (this.d(15) == 1 ? "유효한 동안 유지" : "공격마다 재선택"));
        this.label(2, "레드스톤: " + new String[]{"항상 작동", "신호가 있을 때", "신호가 없을 때"}[Math.clamp(this.d(16), 0, 2)]);
        this.label(3, "증폭 소진 시: " + (this.d(17) == 1 ? "공격 정지" : "기본 공격 계속"));
        for (int i = 0; i < 6; i++) {
            this.label(10 + i, SIDES[i] + ": " + PORTS[Math.clamp(this.d(21 + i), 0, 3)]);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.refreshLabels();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        this.renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        g.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xff141c28);
        g.fill(this.leftPos + 178, this.topPos + 31, this.leftPos + 392, this.topPos + 261, 0xff1d2938);
        g.fill(this.leftPos + 8, this.topPos + 29, this.leftPos + 170, this.topPos + 165, 0xff202b38);
        for (Slot slot : this.menu.slots) {
            if (slot.isActive()) {
                g.fill(this.leftPos + slot.x - 1, this.topPos + slot.y - 1, this.leftPos + slot.x + 17, this.topPos + slot.y + 17, 0xff607185);
                g.fill(this.leftPos + slot.x, this.topPos + slot.y, this.leftPos + slot.x + 16, this.topPos + slot.y + 16, 0xff101822);
            }
        }

        if (this.d(0) >= 2) {
            this.bar(g, 14, 52, 138, this.d(3), 9000, 0xffd5af37);
            this.bar(g, 14, 85, 138, this.d(4), 1000, 0xffed7431);
        }

        if (this.tab == 0) {
            this.bar(g, 188, 170, 192, this.d(5), Math.max(1, this.d(6)), 0xff57bdac);
        }
    }

    private void bar(GuiGraphics g, int x, int y, int width, int value, int max, int color) {
        g.fill(this.leftPos + x, this.topPos + y, this.leftPos + x + width, this.topPos + y + 8, 0xff080e16);
        g.fill(this.leftPos + x, this.topPos + y, this.leftPos + x + Math.clamp((long) value * width / max, 0, width), this.topPos + y + 8, color);
    }

    private void text(GuiGraphics g, String s, int x, int y, int color) {
        g.drawString(this.font, s, x, y, color, false);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        this.text(g, this.title.getString(), 14, 12, 0xffebd292);
        this.text(g, "소지품", 14, 170, 0xffa8bacd);
        if (this.d(0) >= 2) {
            this.text(g, "종자유 " + this.d(3) + " / 9000mB", 14, 37, 0xffe3c665);
            this.text(g, "용암 " + this.d(4) + " / 1000mB", 14, 70, 0xffffa56a);
            this.text(g, "회수", 82, 110, 0xffa8bacd);
        } else {
            this.text(g, this.d(0) == 0 ? "화살" : "돌 · 연료", 14, 34, 0xffa8bacd);
            this.text(g, "탄약 " + this.d(2) + "개", 77, 53, 0xffe6edf5);
            if (this.d(0) == 1) {
                this.text(g, "연료 잔량", 77, 70, 0xffa8bacd);
                this.text(g, this.d(18) + "회", 77, 82, 0xffe6edf5);
                this.text(g, "증폭", 65, 110, 0xffa8bacd);
            }
        }

        this.text(g, "모듈", 14, 110, 0xffa8bacd);
        if (this.d(0) == 1) {
            this.text(g, "증폭: " + new String[]{"없음", "블레이즈", "화약", "용암"}[Math.clamp(this.d(20), 0, 3)] + " · " + this.d(19) + "회", 14, 145, 0xffe3c665);
        }

        if (this.tab == 0) {
            int y = 39;
            String[] lines = {this.d(1) == 11 ? "성벽 가장자리에서 바깥을 향해 설치" : BasicTurretBlockEntity.STATUS[Math.clamp(this.d(1), 0, BasicTurretBlockEntity.STATUS.length - 1)], "피해 " + (this.d(0) == 0 ? 5 : this.d(0) == 1 ? (this.d(43) == 1 ? 13 : 9) : 3) + " → " + this.number(8) + (this.d(0) >= 2 ? " HP ×4 / " : " HP / ") + String.format(Locale.ROOT, "%.2f", this.d(6) / 20.0) + "초", (this.d(0) == 3 ? "흐름 거리 " : "사거리 ") + this.number(9) + "m", this.d(0) == 3 ? "전방 감지 " + String.format(Locale.ROOT, "%.1f", this.d(9) / 100.0 * 2) + "m · 잔류 6초" : this.d(0) == 2 ? "방사각 " + this.d(10) + "° · 화상 " + this.number(34) + " HP/s" : this.d(0) == 1 ? "폭발 반경 " + this.number(11) + "m · 파편 " + this.d(35) : "관통 +" + this.d(36) + " / 추가 대상 +" + this.d(37) + " / 도탄 " + this.d(38), "단일 대상 이론 DPS " + this.number(12), "최근 10초 실제 DPS " + this.number(13)};
            for (String line : lines) {
                this.text(g, line, 188, y, 0xffe6edf5);
                y += 17;
            }

            String target = "대상: 없음";
            if (this.minecraft != null && this.minecraft.level != null) {
                Entity entity = this.minecraft.level.getEntity((this.d(7) & 65535) | (this.d(44) << 16));
                if (entity != null) {
                    target = "대상: " + entity.getDisplayName().getString();
                }
            }

            this.text(g, this.font.plainSubstrByWidth(target, 194), 188, 144, 0xffa8bacd);
            this.text(g, "준비 " + Math.min(100, 100 * this.d(5) / Math.max(1, this.d(6))) + "%" + (this.d(33) > 0 ? " · 분사 중" : ""), 188, 157, 0xffa8bacd);
            if (this.d(32) != 0) {
                this.text(g, (this.d(32) & 1) != 0 ? "! 증폭 재료 소진" : "! 용기 회수칸이 가득 참", 188, 244, 0xffffbb66);
            }
        } else if (this.tab == 1) {
            int y = 39;
            for (int i = 9; i <= 10; i++) {
                ItemStack stack = this.menu.slots.get(i).getItem();
                this.text(g, "슬롯 " + (i - 8) + ": " + (stack.isEmpty() ? "비어 있음" : stack.getHoverName().getString()), 188, y, 0xffebd292);
                y += 17;
                if (stack.getItem() instanceof TurretModule m) {
                    for (FormattedCharSequence line : this.font.split(m.description(), 192)) {
                        g.drawString(this.font, line, 188, y, 0xffe6edf5, false);
                        y += 12;
                    }
                }

                y += 14;
            }

            if (this.d(0) == 3) {
                this.text(g, "장판 6초 · 화상 " + this.number(34) + " HP/s", 188, 195, 0xffa8bacd);
            }

            this.text(g, "공격력 +" + this.d(41) + "% / 공격속도 +" + this.d(42) + "%", 188, 211, 0xffebd292);
            this.text(g, this.d(0) >= 2 ? "1회: 기름 " + (10 * TurretStats.FLAME_OIL_PER_TICK) + "mB / 증폭 용암 10mB" : "탄속 " + this.number(39) + "m/t / 조준 " + this.d(40) + "°/s", 188, 225, 0xffa8bacd);
        } else {
            for (int i = 0; i < 5; i++) {
                if (this.d(0) == 3 && i != 3) {
                    continue;
                }

                this.text(g, ROLES[i] + " 상한: " + this.d(27 + i) + (i >= 3 ? "mB" : "개"), 188, 150 + i * 18, 0xffe6edf5);
            }
        }
    }
}
