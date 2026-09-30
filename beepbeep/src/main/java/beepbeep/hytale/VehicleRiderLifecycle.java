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

/** Седок умер, стал наблюдателем или ушёл с сервера — место освобождается. */
public final class VehicleRiderLifecycle {
   private VehicleRiderLifecycle() {
   }

   private static void releaseLater(Ref<EntityStore> ref, Store<EntityStore> store, CommandBuffer<EntityStore> buffer, String reason) {
      VehicleRiderComponent rider = store.getComponent(ref, VehicleSeats.riderType);
      if (rider != null && !rider.releasing) {
         rider.releasing = true;
         buffer.run(s -> VehicleSeats.release(s, ref, reason, false));
      }
   }

   public static final class Death extends OnDeathSystem {
      @Override
      public Query<EntityStore> getQuery() {
         return Player.getComponentType();
      }

      @Override
      public Set<Dependency<EntityStore>> getDependencies() {
         return Set.of(new SystemDependency(Order.BEFORE, DismountOnPlayerDeath.class));
      }

      @Override
      public void onComponentAdded(Ref<EntityStore> ref, DeathComponent death, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
         releaseLater(ref, store, buffer, "смерть");
      }
   }

   public static final class Remove extends RefSystem<EntityStore> {
      @Override
      public Query<EntityStore> getQuery() {
         return Player.getComponentType();
      }

      @Override
      public Set<Dependency<EntityStore>> getDependencies() {
         return Set.of(new SystemDependency(Order.BEFORE, OnPlayerRemove.class));
      }

      @Override
      public void onEntityAdded(Ref<EntityStore> ref, AddReason reason, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
      }

      @Override
      public void onEntityRemove(Ref<EntityStore> ref, RemoveReason reason, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
         VehicleSeats.onRiderRemoved(ref, store, buffer);
      }
   }

   public static final class Spectate extends RefChangeSystem<EntityStore, Spectating> {
      @Override
      public Query<EntityStore> getQuery() {
         return Player.getComponentType();
      }

      @Override
      public ComponentType<EntityStore, Spectating> componentType() {
         return Spectating.getComponentType();
      }

      @Override
      public Set<Dependency<EntityStore>> getDependencies() {
         return Set.of(new SystemDependency(Order.BEFORE, DismountOnPlayerSpectating.class));
      }

      @Override
      public void onComponentAdded(Ref<EntityStore> ref, Spectating spectating, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
         releaseLater(ref, store, buffer, "режим наблюдателя");
      }

      @Override
      public void onComponentSet(Ref<EntityStore> ref, Spectating previous, Spectating spectating, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
         releaseLater(ref, store, buffer, "режим наблюдателя");
      }

      @Override
      public void onComponentRemoved(Ref<EntityStore> ref, Spectating spectating, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
      }
   }
}
