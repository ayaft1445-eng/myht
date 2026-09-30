package beepbeep.hytale;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Если седока телепортирует кто-то другой (команда, портал), он выходит из машины — как
 * ванильный MountSystems.TeleportMountedEntity. Свой телепорт при высадке мы ставим уже
 * после снятия VehicleRiderComponent, поэтому сюда он не попадает.
 */
public final class SeatTeleportWatcher extends RefChangeSystem<EntityStore, Teleport> {
   private final ComponentType<EntityStore, VehicleRiderComponent> type;

   public SeatTeleportWatcher(ComponentType<EntityStore, VehicleRiderComponent> type) {
      this.type = type;
   }

   @Override
   public Query<EntityStore> getQuery() {
      return this.type;
   }

   @Override
   public ComponentType<EntityStore, Teleport> componentType() {
      return Teleport.getComponentType();
   }

   @Override
   public void onComponentAdded(Ref<EntityStore> ref, Teleport teleport, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
      VehicleRiderComponent rider = store.getComponent(ref, this.type);
      if (rider != null && !rider.releasing) {
         rider.releasing = true;
         buffer.run(s -> VehicleSeats.release(s, ref, "телепорт", false));
      }
   }

   @Override
   public void onComponentSet(Ref<EntityStore> ref, Teleport previous, Teleport teleport, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
   }

   @Override
   public void onComponentRemoved(Ref<EntityStore> ref, Teleport teleport, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
   }
}
