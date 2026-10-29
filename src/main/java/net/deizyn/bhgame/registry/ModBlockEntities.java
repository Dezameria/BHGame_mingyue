package net.deizyn.bhgame.registry;

import net.deizyn.bhgame.BhGame;
import net.deizyn.bhgame.core.block.TrainingBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, BhGame.MODID);

    /** One shared type for every training block; the valid-block list comes from {@link Minigames}. */
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<TrainingBlockEntity>> TRAINING =
            BLOCK_ENTITIES.register("training", () -> BlockEntityType.Builder
                    .of(TrainingBlockEntity::new, Minigames.blocks())
                    .build(null));

    private ModBlockEntities() {
    }
}
