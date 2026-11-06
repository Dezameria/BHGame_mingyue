package net.deizyn.bhgame.registry;

import net.deizyn.bhgame.BhGame;
import net.deizyn.bhgame.game.mob.sparring_dummy.SparringDummy;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Mobs (D73). Minigames are blocks (D16); these are plain mobs outside the session system. */
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, BhGame.MODID);

    public static final RegistryObject<EntityType<SparringDummy>> SPARRING_DUMMY = ENTITY_TYPES.register(
            "sparring_dummy", () -> EntityType.Builder.<SparringDummy>of(SparringDummy::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build(BhGame.id("sparring_dummy").toString()));

    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(SPARRING_DUMMY.get(), SparringDummy.createAttributes().build());
    }

    private ModEntities() {
    }
}
