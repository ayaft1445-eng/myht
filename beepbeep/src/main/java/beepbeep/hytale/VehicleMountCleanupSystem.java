package beepbeep.hytale;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

/** Машина исчезает из мира — все седоки выходят там, где она стояла. */
public final class VehicleMountCleanupSystem extends RefSystem<EntityStore> {
   private final ComponentType<EntityStore, VehicleRuntimeComponent> type;

   public VehicleMountCleanupSystem(ComponentType<EntityStore, VehicleRuntimeComponent> type) {
      this.type = type;
   }

   @Override
   public Query<EntityStore> getQuery() {
      return this.type;
   }

   @Override
   public void onEntityAdded(Ref<EntityStore> ref, AddReason reason, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
   }

   @Override
   public void onEntityRemove(Ref<EntityStore> ref, RemoveReason reason, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
      VehicleRuntimeComponent runtime = store.getComponent(ref, this.type);
      if (runtime != null) {
         if (runtime.debug != null) {
            runtime.debug.trace.stop("chassis-removed");
         }

         TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
         VehicleSeats.onVehicleRemoved(ref, runtime, transform == null ? null : new Vector3d(transform.getPosition()), store, buffer);
      }
   }
}
