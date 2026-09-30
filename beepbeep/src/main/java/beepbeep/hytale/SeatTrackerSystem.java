package beepbeep.hytale;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.Visible;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Рассылает остальным игрокам, что седок пристёгнут к точке своего сиденья. Работает в
 * той же группе, что ванильная рассылка креплений (MountSystems.TrackerUpdate).
 */
public final class SeatTrackerSystem extends EntityTickingSystem<EntityStore> {
   private final ComponentType<EntityStore, VehicleRiderComponent> type;

   public SeatTrackerSystem(ComponentType<EntityStore, VehicleRiderComponent> type) {
      this.type = type;
   }

   @Override
   public SystemGroup<EntityStore> getGroup() {
      return EntityTrackerSystems.QUEUE_UPDATE_GROUP;
   }

   @Override
   public Query<EntityStore> getQuery() {
      return Query.and(new Query[]{this.type, Visible.getComponentType()});
   }

   @Override
   public boolean isParallel(int archetypeChunkSize, int taskCount) {
      return false;
   }

   @Override
   public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
      VehicleRiderComponent rider = chunk.getComponent(index, this.type);
      Visible visible = chunk.getComponent(index, Visible.getComponentType());
      if (rider != null && visible != null) {
         VehicleSeats.queueAttachment(store, chunk.getReferenceTo(index), rider, visible);
      }
   }
}
