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
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput.InputUpdate;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput.WishMovement;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSystems.ProcessPlayerInput;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;
import org.joml.Vector3d;

public final class VehicleProbeSystem extends EntityTickingSystem<EntityStore> {
   private final ComponentType<EntityStore, VehiclePhysicsComponent> type;

   public VehicleProbeSystem(ComponentType<EntityStore, VehiclePhysicsComponent> var1) {
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
      VehiclePhysicsComponent var6 = (VehiclePhysicsComponent)var3.getComponent(var2, this.type);
      if (Float.isFinite(var1) && !(var1 <= 0.0F)) {
         var6.ticks++;
         var6.elapsed += (double)var1;
         PlayerInput var7 = (PlayerInput)var3.getComponent(var2, PlayerInput.getComponentType());
         if (var7 != null) {
            for (InputUpdate var9 : var7.getMovementUpdateQueue()) {
               var6.inputEvents++;
               var6.lastInput = var9.getClass().getSimpleName();
               var6.lastInputTime = var6.elapsed;
               if (var9 instanceof WishMovement var10) {
                  var6.wishEvents++;
                  var6.wishX = var10.getX();
                  var6.wishZ = var10.getZ();
               }
            }
         }

         if (var6.animatedProbe) {
            TransformComponent var11 = (TransformComponent)var3.getComponent(var2, TransformComponent.getComponentType());
            var11.setPosition(new Vector3d(var11.getPosition().x, var6.baseY + 0.15 * Math.sin(var6.elapsed * 2.0), var11.getPosition().z));
            var11.setRotation(new Rotation3f((float)(0.12 * Math.sin(var6.elapsed)), (float)(var6.elapsed * 0.2), (float)(0.12 * Math.cos(var6.elapsed))));
         }
      }
   }
}
