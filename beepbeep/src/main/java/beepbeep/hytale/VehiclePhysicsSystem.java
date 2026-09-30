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
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSystems.ProcessPlayerInput;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;
import org.joml.Vector3d;

public final class VehiclePhysicsSystem extends EntityTickingSystem<EntityStore> {
   private final ComponentType<EntityStore, VehicleRuntimeComponent> type;

   public VehiclePhysicsSystem(ComponentType<EntityStore, VehicleRuntimeComponent> var1) {
      this.type = var1;
   }

   public Query<EntityStore> getQuery() {
      return Query.and(new Query[]{this.type, TransformComponent.getComponentType()});
   }

   public Set<Dependency<EntityStore>> getDependencies() {
      return Set.of(new SystemDependency(Order.BEFORE, ProcessPlayerInput.class), new SystemDependency(Order.BEFORE, HandleMountInput.class));
   }

   public boolean isParallel(int var1, int var2) {
      return false;
   }

   public void tick(float var1, int var2, ArchetypeChunk<EntityStore> var3, Store<EntityStore> var4, CommandBuffer<EntityStore> var5) {
      if (Float.isFinite(var1) && !(var1 <= 0.0F)) {
         VehicleRuntimeComponent var6 = (VehicleRuntimeComponent)var3.getComponent(var2, this.type);
         TransformComponent var7 = (TransformComponent)var3.getComponent(var2, TransformComponent.getComponentType());
         Vector3d var8 = new Vector3d(var7.getPosition());
         if (var6.debug != null) {
            var6.debug.begin(var1, var6.ticks);
            var6.debug.snapshot(var4, var6, var7, "before-input");
         }

         var6.driverDistance = Double.POSITIVE_INFINITY;
         boolean var9 = VehicleSeats.applyDriverInput(var4, var6, System.nanoTime());
         if (!var9 && var6.driver != null) {
            if (var6.driver.isValid() && var6.driver.getStore() == var4) {
               TransformComponent var12 = (TransformComponent)var4.getComponent(var6.driver, TransformComponent.getComponentType());
               PlayerInput var14 = (PlayerInput)var4.getComponent(var6.driver, PlayerInput.getComponentType());
               HeadRotation var18 = (HeadRotation)var4.getComponent(var6.driver, HeadRotation.getComponentType());
               if (var12 != null && var14 != null && var18 != null && var14.getMountId() == 0 && var12.getPosition().distance(var8) <= 24.0) {
                  VehicleControl.update(var6, var14.getMovementUpdateQueue(), new Vector3d(var12.getPosition()), var18.getDirection(), (double)var1);
               } else {
                  VehicleControl.reset(var6);
               }
            } else {
               VehicleControl.reset(var6);
            }
         }

         if (!var9 && var6.driver == null) {
            var6.driven = false;
            var6.throttle = 0.0;
            var6.steer = 0.0;
            var6.brake = 1.0;
         }

         var6.accumulator = Math.min(0.25, var6.accumulator + (double)var1);

         for (VehicleTerrain var13 = VehicleTerrain.world(var4); var6.accumulator >= 0.008333333333333333; var6.accumulator -= 0.008333333333333333) {
            var8 = VehicleMotion.step(var6, var8, var13);
         }

         var6.ticks++;
         var7.setPosition(var8);
         var7.setRotation(new Rotation3f((float)var6.pitch, (float)var6.yaw, (float)var6.roll));
         var6.authoritativeX = var8.x;
         var6.authoritativeY = var8.y;
         var6.authoritativeZ = var8.z;
         var6.authoritativeYaw = var6.yaw;
         if (var6.debug != null) {
            var6.debug.snapshot(var4, var6, var7, "after-physics");
         }
      }
   }
}
