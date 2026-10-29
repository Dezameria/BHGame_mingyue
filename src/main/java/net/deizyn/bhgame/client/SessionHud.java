package net.deizyn.bhgame.client;

import net.deizyn.bhgame.core.config.BhGameConfig;
import net.deizyn.bhgame.core.cue.CueChannel;
import net.deizyn.bhgame.core.input.InputKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
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
        int y = height - 36;
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
