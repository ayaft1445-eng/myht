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

public final class VehicleMountCleanupSystem extends RefSystem<EntityStore> {
   private final ComponentType<EntityStore, VehicleRuntimeComponent> type;

   public VehicleMountCleanupSystem(ComponentType<EntityStore, VehicleRuntimeComponent> var1) {
      this.type = var1;
   }

   public Query<EntityStore> getQuery() {
      return this.type;
   }

   public void onEntityAdded(Ref<EntityStore> var1, AddReason var2, Store<EntityStore> var3, CommandBuffer<EntityStore> var4) {
   }

   public void onEntityRemove(Ref<EntityStore> var1, RemoveReason var2, Store<EntityStore> var3, CommandBuffer<EntityStore> var4) {
      VehicleRuntimeComponent var5 = (VehicleRuntimeComponent)var3.getComponent(var1, this.type);
      if (var5.debug != null) {
         var5.debug.trace.stop("chassis-removed");
      }

      TransformComponent var6 = (TransformComponent)var3.getComponent(var1, TransformComponent.getComponentType());
      Vector3d var7 = var6 == null ? null : new Vector3d(var6.getPosition());
      if (var5.proxy != null || var5.driver != null) {
         var4.run(var2x -> VehicleMount.release(var2x, var5, var7));
      }
   }
}
