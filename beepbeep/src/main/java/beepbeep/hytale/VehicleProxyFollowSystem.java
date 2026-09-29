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

public final class VehicleProxyFollowSystem extends EntityTickingSystem<EntityStore> {
   private final ComponentType<EntityStore, VehicleRuntimeComponent> type;

   public VehicleProxyFollowSystem(ComponentType<EntityStore, VehicleRuntimeComponent> var1) {
      this.type = var1;
   }

   public Query<EntityStore> getQuery() {
      return Query.and(new Query[]{this.type, TransformComponent.getComponentType()});
   }

   public boolean isParallel(int var1, int var2) {
      return false;
   }

   public Set<Dependency<EntityStore>> getDependencies() {
      return Set.of(
         new SystemDependency(Order.AFTER, VehiclePhysicsSystem.class),
         new SystemDependency(Order.AFTER, ProcessPlayerInput.class),
         new SystemDependency(Order.AFTER, HandleMountInput.class),
         new SystemDependency(Order.BEFORE, EntityTrackerUpdate.class)
      );
   }

   public void tick(float var1, int var2, ArchetypeChunk<EntityStore> var3, Store<EntityStore> var4, CommandBuffer<EntityStore> var5) {
      VehicleRuntimeComponent var6 = (VehicleRuntimeComponent)var3.getComponent(var2, this.type);
      TransformComponent var7 = (TransformComponent)var3.getComponent(var2, TransformComponent.getComponentType());
      if (var6.debug != null) {
         var6.debug.snapshot(var4, var6, var7, "before-seat-correction");
      }

      if (var6.seated) {
         VehicleMount.follow(var4, var6, ((TransformComponent)var3.getComponent(var2, TransformComponent.getComponentType())).getPosition());
      }

      if (var6.debug != null) {
         var6.debug.snapshot(var4, var6, var7, "after-seat-correction");
      }
   }
}
