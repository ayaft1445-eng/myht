package dev.hytalemodding.deathmatch.systems;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.event.KillFeedEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.deathmatch.match.MatchService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Считает убийства.
 *
 * Событие KillFeedEvent.KillerMessage сервер шлёт, когда сочиняет строку
 * «такой-то убил такого-то». В нём есть и жертва (getTargetRef), и урон, из
 * которого достаётся убийца: у Damage.EntitySource лежит ссылка на того, кто
 * бил. Ни строку, ни само событие мы не трогаем — только читаем.
 *
 * Это ECS-событие, поэтому обычным слушателем его не поймать: нужна система,
 * зарегистрированная через getEntityStoreRegistry().registerSystem(...).
 */
public class KillSystem extends EntityEventSystem<EntityStore, KillFeedEvent.KillerMessage> {

    private final MatchService service;

    public KillSystem(MatchService service) {
        super(KillFeedEvent.KillerMessage.class);
        this.service = service;
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> buffer,
            @Nonnull KillFeedEvent.KillerMessage event
    ) {
        try {
            PlayerRef victim = playerOf(store, event.getTargetRef());
            PlayerRef killer = playerOf(store, killerRef(event.getDamage()));
            if (killer == null) {
                // Упал сам, утонул, убит мобом — в дезматче это не убийство.
                return;
            }
            if (victim != null && killer.getUuid() != null
                    && killer.getUuid().equals(victim.getUuid())) {
                return; // сам себя не засчитывает
            }
            this.service.onKill(killer, victim);
        } catch (RuntimeException | LinkageError problem) {
            // Счёт важен, но не настолько, чтобы ронять обработку смерти.
            this.service.noteKillProblem(problem);
        }
    }

    /** Кто нанёс смертельный удар. null — не сущность (падение, утопление). */
    @Nullable
    private static Ref<EntityStore> killerRef(Damage damage) {
        if (damage == null) {
            return null;
        }
        Damage.Source source = damage.getSource();
        if (source instanceof Damage.EntitySource) {
            return ((Damage.EntitySource) source).getRef();
        }
        return null;
    }

    @Nullable
    private static PlayerRef playerOf(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (store == null || ref == null) {
            return null;
        }
        return store.getComponent(ref, PlayerRef.getComponentType());
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        // Пустой запрос — система слушает событие у любой сущности.
        return Archetype.empty();
    }
}
