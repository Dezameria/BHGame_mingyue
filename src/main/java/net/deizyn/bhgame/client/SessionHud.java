package net.deizyn.bhgame.client;

import net.deizyn.bhgame.core.config.BhGameConfig;
import net.deizyn.bhgame.core.cue.CueChannel;
import net.deizyn.bhgame.core.gauge.Gauge;
import net.deizyn.bhgame.core.input.InputKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.Locale;

/**
 * The on-screen UI: small key hints for the captured keys, brightest while a window is open, or the wooden dummy's
 * arrow row ({@link ArrowRowHud}, D39). No HP, counters or numbers (D17). P1 draws plain boxes; P6 replaces them
 * with the brush sprites in hud.png.
 */
final class SessionHud {
    private static final InputKey[] KEYS = InputKey.values();
    private static final int HINT_WIDTH = 30;
    private static final int HINT_GAP = 4;
    private static final int BANNER_TICKS = 50;
    private static final float BANNER_SCALE = 3.0F;

    static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        ClientSession session = ClientSession.get();
        Minecraft mc = Minecraft.getInstance();
        if (session == null || session.isFocusing() || mc.options.hideGui) {
            return;
        }
        double time = ClientSession.clock() + partialTick;
        renderBanner(session, graphics, mc, time, width, height);
        if (session.channel == CueChannel.WORLD_ARROW) {
            // the row is the game itself, not a hint, so it ignores showKeyHints
            ArrowRowHud.render(session, graphics, time, width, height);
            return;
        }
        int balance = session.gaugeWithFlag(Gauge.FLAG_BALANCE);
        if (balance >= 0) {
            // the balance bar is the game itself, like the arrow row
            renderBalance(session, graphics, mc, balance, time, width, height);
            return;
        }
        if (!BhGameConfig.showKeyHints) {
            return;
        }
        boolean open = session.openCue(time) != null;
        float glow = session.glow(time);
        int alpha = Math.min(255, (int) (70 + 185 * glow));

        int count = 0;
        for (InputKey key : KEYS) {
            if (session.profile.captures(key)) {
                count++;
            }
        }
        int x = width / 2 - (count * HINT_WIDTH + (count - 1) * HINT_GAP) / 2;
        // where the hotbar would be (hidden during a session), clear of the health bar above it
        int y = height - 18;
        for (InputKey key : KEYS) {
            if (!session.profile.captures(key)) {
                continue;
            }
            graphics.fill(x, y, x + HINT_WIDTH, y + 14, ((alpha / 3) << 24) | 0x0E1A14);
            int textColor = (alpha << 24) | (open ? 0xFFF2B0 : 0xB8D4C4);
            graphics.drawCenteredString(mc.font,
                    Component.translatable("bhgame.hud.key." + key.name().toLowerCase(Locale.ROOT)),
                    x + HINT_WIDTH / 2, y + 3, textColor);
            x += HINT_WIDTH + HINT_GAP;
        }
    }

    private static final int BAR_WIDTH = 160;
    private static final int BAR_HEIGHT = 10;
    /** The outer part of each side where the needle turns red: the water is about to spill. */
    private static final float DANGER = Gauge.BALANCE_DANGER;

    /**
     * water_yoke (D55): a thin bar with a mark in the middle and a needle where the yoke leans; keep the needle in
     * the middle. No numbers. The keys that push the needle sit at each end.
     */
    private static void renderBalance(ClientSession session, GuiGraphics graphics, Minecraft mc, int gauge,
                                      double time, int width, int height) {
        float value = session.gauge(gauge, time);
        float off = Math.abs(value - 0.5F) * 2.0F;
        boolean inDanger = off > 1.0F - DANGER * 2.0F;
        // in the red the bar shakes with the yoke (D70)
        int jitter = inDanger ? Math.round(Mth.sin((float) time * 2.9F) * 1.5F) : 0;
        int x0 = width / 2 - BAR_WIDTH / 2 + jitter;
        // where the hotbar would be (hidden during a session), clear of the health bar above it
        int y0 = height - 16;
        int x1 = x0 + BAR_WIDTH;
        int y1 = y0 + BAR_HEIGHT;
        graphics.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xC0101010);
        graphics.fill(x0, y0, x1, y1, 0x80302A22);
        int danger = (int) (BAR_WIDTH * DANGER);
        graphics.fill(x0, y0, x0 + danger, y1, 0x50802020);
        graphics.fill(x1 - danger, y0, x1, y1, 0x50802020);
        int mid = width / 2 + jitter;
        graphics.fill(mid, y0 + 2, mid + 1, y1 - 2, 0x90E8E0C8);
        int needleColor = inDanger ? 0xFFFF3030 : 0xFFE84040;
        int nx = x0 + Math.round(value * (BAR_WIDTH - 2));
        graphics.fill(nx, y0 - 3, nx + 2, y1 + 3, needleColor);
        if (BhGameConfig.showKeyHints) {
            int hint = 0xB0B8D4C4;
            graphics.drawString(mc.font, Component.translatable("bhgame.hud.key.lmb"),
                    x0 - 6 - mc.font.width(Component.translatable("bhgame.hud.key.lmb")), y0 + 1, hint);
            graphics.drawString(mc.font, Component.translatable("bhgame.hud.key.rmb"), x1 + 6, y0 + 1, hint);
        }
    }

    /** The banner (e.g. "Stage 4") at the top: fades in, holds, fades out over {@link #BANNER_TICKS}. */
    private static void renderBanner(ClientSession session, GuiGraphics graphics, Minecraft mc, double time, int width,
                                     int height) {
        Component banner = session.banner();
        double age = session.bannerAge(time);
        if (banner == null || age < 0 || age >= BANNER_TICKS) {
            return;
        }
        float alpha = (float) Math.min(1.0, Math.min(age / 5.0, (BANNER_TICKS - age) / 10.0));
        int a = Math.max(8, Math.round(alpha * 255));
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2.0F, height * 0.18F, 0.0F);
        graphics.pose().scale(BANNER_SCALE, BANNER_SCALE, 1.0F);
        graphics.drawCenteredString(mc.font, banner, 0, -4, (a << 24) | 0xFFE8B0);
        graphics.pose().popPose();
    }

    private SessionHud() {
    }
}
