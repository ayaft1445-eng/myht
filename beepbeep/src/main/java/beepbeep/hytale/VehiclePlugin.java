package beepbeep.hytale;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger.Api;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class VehiclePlugin extends JavaPlugin {
   private ComponentType<EntityStore, VehiclePhysicsComponent> vehicleType;
   private ComponentType<EntityStore, VehicleRuntimeComponent> runtimeType;

   public VehiclePlugin(JavaPluginInit var1) {
      super(var1);
   }

   protected void shutdown() {
      VehicleDebugTrace.shutdown();
      VehicleMountInput.stop();
   }

   protected void setup() {
      this.vehicleType = this.getEntityStoreRegistry().registerComponent(VehiclePhysicsComponent.class, VehiclePhysicsComponent::new);
      this.runtimeType = this.getEntityStoreRegistry().registerComponent(VehicleRuntimeComponent.class, VehicleRuntimeComponent::new);
      this.getEntityStoreRegistry().registerSystem(new VehicleProbeSystem(this.vehicleType));
      this.getEntityStoreRegistry().registerSystem(new VehiclePhysicsSystem(this.runtimeType));
      VehicleMountInput.start();
      this.getEntityStoreRegistry().registerSystem(new VehicleMountCleanupSystem(this.runtimeType));
      this.getEntityStoreRegistry().registerSystem(new VehicleProxyFollowSystem(this.runtimeType));
      this.getEntityStoreRegistry().registerSystem(new VehicleMountSyncSystem(this.runtimeType));
      this.getEntityStoreRegistry().registerSystem(new VehicleRiderLifecycle.Remove());
      this.getEntityStoreRegistry().registerSystem(new VehicleRiderLifecycle.Death());
      this.getEntityStoreRegistry().registerSystem(new VehicleRiderLifecycle.Spectate());
      this.getCommandRegistry()
         .registerCommand(
            new VehicleCommand(this.vehicleType, this.runtimeType, new VehicleProfiles(this.getDataDirectory()), this.getDataDirectory().resolve("debug"))
         );
      ((Api)this.getLogger().atInfo()).log("BeepBeep VehicleCore 0.3.13: profile editor, suspension chassis and diagnostics registered.");
   }
}
