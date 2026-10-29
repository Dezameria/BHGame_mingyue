package net.deizyn.bhgame.client;

import net.deizyn.bhgame.core.cue.CueData;
import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.pattern.common.SequenceTrack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The wooden dummy's arrow row, drawn on the player's screen below the centre (D39: in the world it was often half
 * hidden by the dummy). Arrows already pressed are gold, reversed ones red, the next one larger; a broken row turns
 * red and shakes until the dummy throws you out. The bar under the row shrinks with the set's time; the mark on it is
 * the strike point for the SPACE finisher. Bystanders see nothing of this (D17 still holds for them).
 * Plain font glyphs and fills until P6 brings the brush sprites.
 */
final class ArrowRowHud {
    private static final float ARROW_SCALE = 2.5F;
    private static final int CELL = 26;
    private static final int PANEL = 0x90100C08;
    private static final int PENDING = 0xFFF0EAD6;
    private static final int DONE = 0xFFFFD54F;
    private static final int REVERSED = 0xFFFF5A4A;
    private static final int BROKEN = 0xFFB02818;
    private static final int BAR = 0xE0E8DCC0;
    private static final int BAR_TRACK = 0x60000000;
    private static final int MARK_IDLE = 0xFF807060;
    private static final int MARK_READY = 0xFFFFD54F;

    static void render(ClientSession session, GuiGraphics graphics, double time, int width, int height) {
        ClientSession.LocalCue set = session.shownSequence(time);
        if (set == null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        byte[] steps = set.data().steps();
        int count = Math.min(steps.length, CueData.MAX_STEPS);
        int rowWidth = count * CELL;
        int left = width / 2 - rowWidth / 2;
        int top = height / 2 + 30;
        int shake = session.sequenceShaking(time) ? (int) Math.round(Math.sin(time * 3.0) * 3.0) : 0;
        boolean broken = session.sequenceBroken();
        boolean complete = session.sequenceComplete();
        int progress = session.sequenceProgress();

        graphics.fill(left - 6, top - 6, left + rowWidth + 6, top + CELL + 12, PANEL);
        for (int i = 0; i < count; i++) {
            InputKey shown = InputKey.byId(CueData.stepKey(steps[i]));
            String glyph = glyph(shown);
            int colour = broken ? BROKEN
                    : i < progress || complete ? DONE
                    : CueData.stepReversed(steps[i]) ? REVERSED : PENDING;
            float scale = i == progress && !complete && !broken ? ARROW_SCALE * 1.3F : ARROW_SCALE;
            float centreX = left + i * CELL + CELL / 2.0F + shake;
            float centreY = top + CELL / 2.0F;
            graphics.pose().pushPose();
            graphics.pose().translate(centreX, centreY, 0.0F);
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.drawString(font, glyph, -font.width(glyph) / 2, -4, colour, false);
            graphics.pose().popPose();
        }

        // time bar: shrinks from the right; the mark is where its end is at the strike point
        float remaining = (float) Math.max(0.0, Math.min(1.0, (set.close() - time) / set.data().window()));
        int barTop = top + CELL + 4;
        graphics.fill(left, barTop, left + rowWidth, barTop + 3, BAR_TRACK);
        graphics.fill(left, barTop, left + Math.round(rowWidth * remaining), barTop + 3, BAR);
        if (!session.finisherUsed() && !broken) {
            int markX = left + Math.round(rowWidth * (1.0F - SequenceTrack.STRIKE_AT));
            ClientSession.LocalCue finisher = session.finisher();
            boolean open = finisher != null && time >= finisher.open() && time < finisher.close();
            graphics.fill(markX - 1, barTop - 3, markX + 1, barTop + 6,
                    complete && open ? MARK_READY : complete ? DONE : MARK_IDLE);
            if (complete) {
                graphics.drawCenteredString(font, Component.translatable("bhgame.hud.key.space"), markX,
                        barTop + 8, open ? MARK_READY : DONE);
            }
        }
    }

    private static String glyph(InputKey key) {
        if (key == null) {
            return "?";
        }
        return switch (key) {
            case UP -> "↑";
            case LEFT -> "←";
            case DOWN -> "↓";
            case RIGHT -> "→";
            default -> "?";
        };
    }

    private ArrowRowHud() {
    }
}
