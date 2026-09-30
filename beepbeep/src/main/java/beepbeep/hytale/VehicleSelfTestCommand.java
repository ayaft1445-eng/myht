package beepbeep.hytale;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.concurrent.CompletableFuture;
import org.joml.Vector3d;

public final class VehicleSelfTestCommand extends AbstractAsyncCommand {
   private final ComponentType<EntityStore, VehiclePhysicsComponent> type;

   public VehicleSelfTestCommand(ComponentType<EntityStore, VehiclePhysicsComponent> var1) {
      super("selftest", "Test ECS ticking and transform writes on a temporary entity");
      this.type = var1;
      this.requirePermission("beepbeep.vehicle.admin");
   }

   protected CompletableFuture<Void> executeAsync(CommandContext var1) {
      Ref var2 = var1.isPlayer() ? var1.senderAsPlayerRef() : null;
      World var3 = var2 != null ? ((EntityStore)var2.getStore().getExternalData()).getWorld() : Universe.get().getDefaultWorld();
      if (var3 == null) {
         var1.sendMessage(Message.raw("FAIL: no default world"));
         return CompletableFuture.completedFuture(null);
      } else {
         return var2 == null ? var3.getChunkAsync(ChunkUtil.indexChunk(0, 0)).thenAcceptAsync(var3x -> {
            if (var3x == null) {
               var1.sendMessage(Message.raw("FAIL: test chunk unavailable"));
            } else {
               var3x.addKeepLoaded();

               try {
                  this.runTest(var3.getEntityStore().getStore(), new Vector3d(0.0, 200.0, 0.0), var1);
                  if (Boolean.getBoolean("beepbeep.isolatedTerrainTest")) {
                     VehicleTerrainSelfTest.run(var3.getEntityStore().getStore());
                  }
               } finally {
                  var3x.removeKeepLoaded();
               }
            }
         }, var3) : CompletableFuture.runAsync(() -> {
            Store var4 = var3.getEntityStore().getStore();
            if (!var2.isValid()) {
               var1.sendMessage(Message.raw("Test cancelled: player left world."));
            } else {
               TransformComponent var5 = (TransformComponent)var4.getComponent(var2, TransformComponent.getComponentType());
               if (var5 == null) {
                  var1.sendMessage(Message.raw("FAIL: player transform unavailable"));
               } else {
                  this.runTest(var4, new Vector3d(var5.getPosition()), var1);
               }
            }
         }, var3);
      }
   }

   private void runTest(Store<EntityStore> var1, Vector3d var2, CommandContext var3) {
      Holder var4 = EntityStore.REGISTRY.newHolder();
      VehiclePhysicsComponent var5 = new VehiclePhysicsComponent();
      var5.animatedProbe = true;
      var5.baseY = var2.y;
      var4.addComponent(this.type, var5);
      var4.addComponent(TransformComponent.getComponentType(), new TransformComponent(var2, new Rotation3f()));
      var4.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
      Ref var6 = var1.addEntity(var4, AddReason.SPAWN);
      if (var6 != null && var6.isValid()) {
         try {
            VehicleProbeSystem var7 = new VehicleProbeSystem(this.type);
            var1.forEachChunk(this.type, (var3x, var4x) -> {
               for (int var5x = 0; var5x < var3x.size(); var5x++) {
                  if (var3x.getReferenceTo(var5x) == var6) {
                     var7.tick(0.016666668F, var5x, var3x, var1, var4x);
                  }
               }
            });
            VehiclePhysicsComponent var8 = (VehiclePhysicsComponent)var1.getComponent(var6, this.type);
            TransformComponent var9 = (TransformComponent)var1.getComponent(var6, TransformComponent.getComponentType());
            VehiclePhysicsComponent var10 = var8.clone();
            var10.ticks = 999L;
            boolean var11 = var8.ticks > 0L && var9.getPosition().y != var5.baseY && var8.ticks != var10.ticks;
            var3.sendMessage(Message.raw((var11 ? "PASS" : "FAIL") + ": ECS entity + tick + transform write + clone isolation; ticks=" + var8.ticks));
            if (!var11) {
               throw new IllegalStateException("Vehicle ECS self-test failed");
            }
         } finally {
            if (var6.isValid()) {
               var1.removeEntity(var6, RemoveReason.REMOVE);
            }
         }
      } else {
         var3.sendMessage(Message.raw("FAIL: test entity could not spawn; no valid reference. Try again in a loaded area."));
      }
   }
}
