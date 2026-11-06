package net.deizyn.bhgame.registry;

import net.deizyn.bhgame.core.block.CarryBlock;
import net.deizyn.bhgame.core.block.HerbBlock;
import net.deizyn.bhgame.core.block.StationBlock;
import net.deizyn.bhgame.core.block.TrainingBlock;
import net.deizyn.bhgame.game.herb.polarity_grass.PolarityGrassBlock;
import net.deizyn.bhgame.game.herb.pulse_grass.PulseGrassBlock;
import net.deizyn.bhgame.game.station.qi_station.QiStationBlock;
import net.deizyn.bhgame.game.herb.thunder_grass.ThunderGrassBlock;
import net.deizyn.bhgame.game.herb.twin_grass.TwinGrassBlock;
import net.deizyn.bhgame.game.carry.water_yoke.WaterYokeBlock;
import net.deizyn.bhgame.game.station.wooden_dummy.WoodenDummyBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * The one place a minigame is registered (docs/adding-a-minigame.md). {@link #register} creates the block, its item,
 * the creative tab entry and the shared block entity link; the game itself is described by its block class.
 */
public final class Minigames {
    private static final List<RegistryObject<? extends TrainingBlock>> ALL = new ArrayList<>();

    public static final RegistryObject<PulseGrassBlock> PULSE_GRASS =
            register("pulse_grass", () -> new PulseGrassBlock(HerbBlock.herbProperties()));
    public static final RegistryObject<TwinGrassBlock> TWIN_GRASS =
            register("twin_grass", () -> new TwinGrassBlock(HerbBlock.herbProperties()));
    public static final RegistryObject<PolarityGrassBlock> POLARITY_GRASS =
            register("polarity_grass", () -> new PolarityGrassBlock(HerbBlock.herbProperties()));
    public static final RegistryObject<ThunderGrassBlock> THUNDER_GRASS =
            register("thunder_grass", () -> new ThunderGrassBlock(HerbBlock.herbProperties()));
    public static final RegistryObject<WoodenDummyBlock> WOODEN_DUMMY =
            register("wooden_dummy", () -> new WoodenDummyBlock(StationBlock.stationProperties()));
    public static final RegistryObject<WaterYokeBlock> WATER_YOKE =
            register("water_yoke", () -> new WaterYokeBlock(CarryBlock.carryProperties()));
    public static final RegistryObject<QiStationBlock> QI_STATION =
            register("qi_station", () -> new QiStationBlock(StationBlock.stationProperties().sound(SoundType.STONE)));

    public static <B extends TrainingBlock> RegistryObject<B> register(String id, Supplier<B> factory) {
        RegistryObject<B> block = ModBlocks.BLOCKS.register(id, factory);
        ModItems.ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
        ALL.add(block);
        return block;
    }

    public static List<RegistryObject<? extends TrainingBlock>> all() {
        return Collections.unmodifiableList(ALL);
    }

    /** Valid blocks of the shared block entity type. Only call after block registration. */
    static Block[] blocks() {
        Block[] blocks = new Block[ALL.size()];
        for (int i = 0; i < blocks.length; i++) {
            blocks[i] = ALL.get(i).get();
        }
        return blocks;
    }

    /** Loads this class so every register call above runs before the deferred registers are attached. */
    public static void init() {
    }

    private Minigames() {
    }
}
