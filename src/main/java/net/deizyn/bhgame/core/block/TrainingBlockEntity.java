package net.deizyn.bhgame.core.block;

import net.deizyn.bhgame.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Holds the admin's NBT config for one training block. Never ticks and never syncs session data to clients
 * (docs/multiplayer-performance.md 5.1). The current user is transient so a server restart can't leave it locked.
 */
public class TrainingBlockEntity extends BlockEntity {
    private static final String TAG_DIFFICULTY = "Difficulty";
    private static final String TAG_MAX_HP = "MaxHP";
    private static final String TAG_MAX_MISTAKES = "MaxMistakes";
    private static final String TAG_LOOT_PERFECT = "LootPerfect";
    private static final String TAG_LOOT_NORMAL = "LootNormal";

    private int difficulty = 1;
    private int maxHp = -1;
    private int maxMistakes = -1;
    @Nullable
    private ResourceLocation lootPerfect;
    @Nullable
    private ResourceLocation lootNormal;
    @Nullable
    private UUID user;

    public TrainingBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRAINING.get(), pos, state);
    }

    public int difficulty() {
        return difficulty;
    }

    /** Admin override, or the game's default when unset. */
    public int maxHp(int fallback) {
        return maxHp > 0 ? maxHp : fallback;
    }

    public int maxMistakes(int fallback) {
        return maxMistakes > 0 ? maxMistakes : fallback;
    }

    public ResourceLocation lootTable(boolean perfect, ResourceLocation fallback) {
        ResourceLocation custom = perfect ? lootPerfect : lootNormal;
        return custom != null ? custom : fallback;
    }

    /** Only what the admin set in NBT, or null (stations drop loot only when it is set). */
    @Nullable
    public ResourceLocation customLootTable(boolean perfect) {
        return perfect ? lootPerfect : lootNormal;
    }

    @Nullable
    public UUID user() {
        return user;
    }

    public void setUser(@Nullable UUID user) {
        this.user = user;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        difficulty = tag.contains(TAG_DIFFICULTY) ? Math.max(1, Math.min(3, tag.getInt(TAG_DIFFICULTY))) : 1;
        maxHp = tag.contains(TAG_MAX_HP) ? tag.getInt(TAG_MAX_HP) : -1;
        maxMistakes = tag.contains(TAG_MAX_MISTAKES) ? tag.getInt(TAG_MAX_MISTAKES) : -1;
        lootPerfect = tag.contains(TAG_LOOT_PERFECT) ? ResourceLocation.tryParse(tag.getString(TAG_LOOT_PERFECT)) : null;
        lootNormal = tag.contains(TAG_LOOT_NORMAL) ? ResourceLocation.tryParse(tag.getString(TAG_LOOT_NORMAL)) : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (difficulty != 1) tag.putInt(TAG_DIFFICULTY, difficulty);
        if (maxHp > 0) tag.putInt(TAG_MAX_HP, maxHp);
        if (maxMistakes > 0) tag.putInt(TAG_MAX_MISTAKES, maxMistakes);
        if (lootPerfect != null) tag.putString(TAG_LOOT_PERFECT, lootPerfect.toString());
        if (lootNormal != null) tag.putString(TAG_LOOT_NORMAL, lootNormal.toString());
    }
}
