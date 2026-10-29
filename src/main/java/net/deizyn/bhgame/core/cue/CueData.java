package net.deizyn.bhgame.core.cue;

import net.minecraft.network.FriendlyByteBuf;

/**
 * One cue as sent to the client. {@code opensIn} is relative to when the packet is sent, so the client
 * places it on its own tick clock. {@code roles} is a bit mask of the roles that must act on it (0 = any one
 * participant, the usual case for one-player games). {@code target} is an index into the target set, or -1.
 * {@code steps} is the arrow row of a SEQUENCE cue (see {@link #step}), empty for every other type.
 */
public record CueData(int id, CueType type, CueChannel channel, int keys, int roles, int opensIn, int window,
                      CueVisual visual, int target, byte[] steps) {
    public static final byte[] NO_STEPS = new byte[0];
    /** Longest arrow row a SEQUENCE may carry. */
    public static final int MAX_STEPS = 16;
    private static final int REVERSED = 0x80;

    public CueData(int id, CueType type, CueChannel channel, int keys, int roles, int opensIn, int window,
                   CueVisual visual, int target) {
        this(id, type, channel, keys, roles, opensIn, window, visual, target, NO_STEPS);
    }

    /** One arrow of a SEQUENCE: the shown direction ({@code InputKey} ordinal) and whether it must be reversed. */
    public static byte step(int keyOrdinal, boolean reversed) {
        return (byte) (keyOrdinal | (reversed ? REVERSED : 0));
    }

    public static int stepKey(byte step) {
        return step & ~REVERSED & 0xFF;
    }

    public static boolean stepReversed(byte step) {
        return (step & REVERSED) != 0;
    }

    public boolean isFor(int role) {
        return roles == 0 || (roles & (1 << role)) != 0;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(id);
        buf.writeByte(type.ordinal());
        buf.writeByte(channel.ordinal());
        buf.writeByte(keys);
        buf.writeByte(roles);
        buf.writeVarInt(opensIn);
        buf.writeVarInt(window);
        buf.writeByte(visual.ordinal());
        buf.writeVarInt(target + 1);
        buf.writeByte(steps.length);
        buf.writeBytes(steps);
    }

    public static CueData read(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        CueType type = CueType.byId(buf.readUnsignedByte());
        CueChannel channel = CueChannel.byId(buf.readUnsignedByte());
        int keys = buf.readUnsignedByte();
        int roles = buf.readUnsignedByte();
        int opensIn = buf.readVarInt();
        int window = buf.readVarInt();
        CueVisual visual = CueVisual.byId(buf.readUnsignedByte());
        int target = buf.readVarInt() - 1;
        // read every byte that was written (at most 255) so the buffer stays aligned; the renderer caps the row
        int count = buf.readUnsignedByte();
        byte[] steps = count == 0 ? NO_STEPS : new byte[count];
        buf.readBytes(steps);
        return new CueData(id, type, channel, keys, roles, opensIn, window, visual, target, steps);
    }
}
