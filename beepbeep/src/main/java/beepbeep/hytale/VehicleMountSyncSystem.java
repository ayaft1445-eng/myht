package beepbeep.hytale;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.SendPackets;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;

public final class VehicleMountSyncSystem extends EntityTickingSystem<EntityStore> {
   private final ComponentType<EntityStore, VehicleRuntimeComponent> type;

   public VehicleMountSyncSystem(ComponentType<EntityStore, VehicleRuntimeComponent> var1) {
      this.type = var1;
   }

   public Query<EntityStore> getQuery() {
      return this.type;
   }

   public boolean isParallel(int var1, int var2) {
      return false;
   }

   public Set<Dependency<EntityStore>> getDependencies() {
      return Set.of(new SystemDependency(Order.AFTER, SendPackets.class), new SystemDependency(Order.AFTER, VehicleProxyFollowSystem.class));
   }

   public void tick(float var1, int var2, ArchetypeChunk<EntityStore> var3, Store<EntityStore> var4, CommandBuffer<EntityStore> var5) {
      VehicleMount.sync(var4, (VehicleRuntimeComponent)var3.getComponent(var2, this.type), var5);
   }
}
