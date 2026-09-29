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
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput.AbsoluteMovement;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput.RelativeMovement;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput.SetBody;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput.SetClientVelocity;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSystems.ProcessPlayerInput;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.PlayerRef;
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
         if (var6.seated) {
            boolean var9 = var6.driver != null
               && var6.driver.isValid()
               && var6.driver.getStore() == var4
               && var6.proxy != null
               && var6.proxy.isValid()
               && var6.mountInput != null
               && !var6.mountInput.exitRequested;
            Player var10 = var9 ? (Player)var4.getComponent(var6.driver, Player.getComponentType()) : null;
            if (var9) {
               var9 = var10 != null
                  && (var6.mountPending || var10.getMountEntityId() == ((NetworkId)var4.getComponent(var6.proxy, NetworkId.getComponentType())).getId())
                  && var4.getComponent(var6.driver, Teleport.getComponentType()) == null;
            }

            if (!var9) {
               if (var6.mountInput != null
                  && var6.mountInput.hookError != null
                  && var6.driver != null
                  && var6.driver.isValid()
                  && var6.driver.getStore() == var4) {
                  PlayerRef var11 = (PlayerRef)var4.getComponent(var6.driver, PlayerRef.getComponentType());
                  if (var11 != null) {
                     var11.sendMessage(Message.raw("Seat input failed: " + var6.mountInput.hookError));
                  }
               }

               var6.throttle = 0.0;
               var6.steer = 0.0;
               var6.brake = 1.0;
               Vector3d var15 = new Vector3d(var8);
               var5.run(var2x -> VehicleMount.release(var2x, var6, var15));
            } else if (var6.mountPending) {
               var6.mountWait += (double)var1;
               var6.throttle = 0.0;
               var6.steer = 0.0;
               var6.brake = 1.0;
               if (var6.mountWait > 5.0) {
                  PlayerRef var16 = (PlayerRef)var4.getComponent(var6.driver, PlayerRef.getComponentType());
                  if (var16 != null) {
                     var16.sendMessage(Message.raw("Seat activation timed out: seat or chassis was not replicated."));
                  }

                  var5.run(var1x -> VehicleMount.release(var1x, var6, null));
               }
            } else {
               VehicleMountInput.apply(var6, var6.mountInput, System.nanoTime());
               PlayerInput var17 = (PlayerInput)var4.getComponent(var6.driver, PlayerInput.getComponentType());
               if (var17 != null) {
                  var17.getMovementUpdateQueue()
                     .removeIf(
                        var0 -> var0 instanceof RelativeMovement
                              || var0 instanceof AbsoluteMovement
                              || var0 instanceof SetBody
                              || var0 instanceof SetClientVelocity
                     );
               }
            }
         } else if (var6.driver != null) {
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

         if (var6.driver == null) {
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
