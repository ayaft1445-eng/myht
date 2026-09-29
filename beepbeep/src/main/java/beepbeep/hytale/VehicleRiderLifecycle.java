package beepbeep.hytale;

import com.hypixel.hytale.builtin.mounts.NPCMountSystems.DismountOnPlayerDeath;
import com.hypixel.hytale.builtin.mounts.NPCMountSystems.DismountOnPlayerSpectating;
import com.hypixel.hytale.builtin.mounts.NPCMountSystems.OnPlayerRemove;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.Spectating;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems.OnDeathSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;

public final class VehicleRiderLifecycle {
   public static final class Death extends OnDeathSystem {
      public Query<EntityStore> getQuery() {
         return Player.getComponentType();
      }

      public Set<Dependency<EntityStore>> getDependencies() {
         return Set.of(new SystemDependency(Order.BEFORE, DismountOnPlayerDeath.class));
      }

      public void onComponentAdded(Ref<EntityStore> var1, DeathComponent var2, Store<EntityStore> var3, CommandBuffer<EntityStore> var4) {
         VehicleMount.lifecycleRelease(var1, var4);
      }
   }

   public static final class Remove extends RefSystem<EntityStore> {
      public Query<EntityStore> getQuery() {
         return Player.getComponentType();
      }

      public Set<Dependency<EntityStore>> getDependencies() {
         return Set.of(new SystemDependency(Order.BEFORE, OnPlayerRemove.class));
      }

      public void onEntityAdded(Ref<EntityStore> var1, AddReason var2, Store<EntityStore> var3, CommandBuffer<EntityStore> var4) {
      }

      public void onEntityRemove(Ref<EntityStore> var1, RemoveReason var2, Store<EntityStore> var3, CommandBuffer<EntityStore> var4) {
         VehicleMount.lifecycleRelease(var1, var4);
      }
   }

   public static final class Spectate extends RefChangeSystem<EntityStore, Spectating> {
      public Query<EntityStore> getQuery() {
         return Player.getComponentType();
      }

      public ComponentType<EntityStore, Spectating> componentType() {
         return Spectating.getComponentType();
      }

      public Set<Dependency<EntityStore>> getDependencies() {
         return Set.of(new SystemDependency(Order.BEFORE, DismountOnPlayerSpectating.class));
      }

      public void onComponentAdded(Ref<EntityStore> var1, Spectating var2, Store<EntityStore> var3, CommandBuffer<EntityStore> var4) {
         VehicleMount.lifecycleRelease(var1, var4);
      }

      public void onComponentSet(Ref<EntityStore> var1, Spectating var2, Spectating var3, Store<EntityStore> var4, CommandBuffer<EntityStore> var5) {
         VehicleMount.lifecycleRelease(var1, var5);
      }

      public void onComponentRemoved(Ref<EntityStore> var1, Spectating var2, Store<EntityStore> var3, CommandBuffer<EntityStore> var4) {
      }
   }
}
