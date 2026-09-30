package beepbeep.hytale;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.CollectVisible;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.EntityViewer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Iterator;
import java.util.Set;

/**
 * Двойника седока видит только сам седок, и то не в виде от первого лица (камера была бы
 * у него в голове). Устроено как ванильный EntityTrackerSystems.HideFromPlayer: после
 * сбора видимых сущностей лишнее убирается из списка зрителя.
 */
public final class SeatDummyVisibilitySystem extends EntityTickingSystem<EntityStore> {
   private final ComponentType<EntityStore, SeatDummyComponent> dummyType;
   private final ComponentType<EntityStore, VehicleRiderComponent> riderType;

   public SeatDummyVisibilitySystem(ComponentType<EntityStore, SeatDummyComponent> dummyType, ComponentType<EntityStore, VehicleRiderComponent> riderType) {
      this.dummyType = dummyType;
      this.riderType = riderType;
   }

   @Override
   public SystemGroup<EntityStore> getGroup() {
      return EntityTrackerSystems.FIND_VISIBLE_ENTITIES_GROUP;
   }

   @Override
   public Set<Dependency<EntityStore>> getDependencies() {
      return Set.of(new SystemDependency(Order.AFTER, CollectVisible.class));
   }

   @Override
   public Query<EntityStore> getQuery() {
      return Query.and(new Query[]{EntityViewer.getComponentType(), PlayerRef.getComponentType()});
   }

   @Override
   public boolean isParallel(int archetypeChunkSize, int taskCount) {
      return false;
   }

   @Override
   public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
      EntityViewer viewer = chunk.getComponent(index, EntityViewer.getComponentType());
      if (viewer == null || viewer.visible.isEmpty()) {
         return;
      }

      Ref<EntityStore> self = chunk.getReferenceTo(index);
      Iterator<Ref<EntityStore>> iterator = viewer.visible.iterator();

      while (iterator.hasNext()) {
         Ref<EntityStore> target = iterator.next();
         if (target != null && target.isValid()) {
            SeatDummyComponent dummy = buffer.getComponent(target, this.dummyType);
            if (dummy != null && (!self.equals(dummy.owner) || this.firstPerson(buffer, self))) {
               iterator.remove();
            }
         }
      }
   }

   private boolean firstPerson(CommandBuffer<EntityStore> buffer, Ref<EntityStore> owner) {
      VehicleRiderComponent rider = buffer.getComponent(owner, this.riderType);
      return rider == null || rider.view == SeatCamera.View.FIRST;
   }
}
