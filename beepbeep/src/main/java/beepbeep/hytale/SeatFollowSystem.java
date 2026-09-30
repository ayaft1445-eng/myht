package beepbeep.hytale;

import com.hypixel.hytale.builtin.mounts.MountSystems.HandleMountInput;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSystems.ProcessPlayerInput;
import com.hypixel.hytale.server.core.modules.entity.system.TransformSystems.EntityTrackerUpdate;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;

/**
 * После физики машины ставит точки сидений и седоков на места, с поворотом, наклоном
 * и креном машины, и досылает поворот камеры. Идёт до отправки позиций клиентам,
 * поэтому машина и седок приходят к зрителям в одном и том же тике.
 */
public final class SeatFollowSystem extends EntityTickingSystem<EntityStore> {
   private final ComponentType<EntityStore, VehicleRuntimeComponent> type;

   public SeatFollowSystem(ComponentType<EntityStore, VehicleRuntimeComponent> type) {
      this.type = type;
   }

   @Override
   public Query<EntityStore> getQuery() {
      return Query.and(new Query[]{this.type, TransformComponent.getComponentType()});
   }

   @Override
   public Set<Dependency<EntityStore>> getDependencies() {
      return Set.of(
         new SystemDependency(Order.AFTER, VehiclePhysicsSystem.class),
         new SystemDependency(Order.AFTER, ProcessPlayerInput.class),
         new SystemDependency(Order.AFTER, HandleMountInput.class),
         new SystemDependency(Order.BEFORE, EntityTrackerUpdate.class)
      );
   }

   @Override
   public boolean isParallel(int archetypeChunkSize, int taskCount) {
      return false;
   }

   @Override
   public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
      VehicleRuntimeComponent runtime = chunk.getComponent(index, this.type);
      TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
      if (runtime != null && transform != null) {
         if (runtime.debug != null) {
            runtime.debug.snapshot(store, runtime, transform, "before-seat-follow");
         }

         VehicleSeats.follow(store, chunk.getReferenceTo(index), runtime, transform.getPosition(), System.nanoTime());
         if (runtime.debug != null) {
            runtime.debug.snapshot(store, runtime, transform, "after-seat-follow");
         }
      }
   }
}
