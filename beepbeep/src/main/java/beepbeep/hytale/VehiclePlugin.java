package beepbeep.hytale;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger.Api;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;

public final class VehiclePlugin extends JavaPlugin {
   public static final String VERSION = "0.4.0";
   private ComponentType<EntityStore, VehiclePhysicsComponent> vehicleType;
   private ComponentType<EntityStore, VehicleRuntimeComponent> runtimeType;
   private ComponentType<EntityStore, VehicleRiderComponent> riderType;
   private PacketFilter seatFilter;

   public VehiclePlugin(JavaPluginInit init) {
      super(init);
   }

   @Override
   protected void shutdown() {
      VehicleSeats.shutdown();
      if (this.seatFilter != null) {
         try {
            PacketAdapters.deregisterInbound(this.seatFilter);
         } catch (IllegalArgumentException ignored) {
         }

         this.seatFilter = null;
      }

      VehicleDebugTrace.shutdown();
   }

   @Override
   protected void setup() {
      this.vehicleType = this.getEntityStoreRegistry().registerComponent(VehiclePhysicsComponent.class, VehiclePhysicsComponent::new);
      this.runtimeType = this.getEntityStoreRegistry().registerComponent(VehicleRuntimeComponent.class, VehicleRuntimeComponent::new);
      this.riderType = this.getEntityStoreRegistry().registerComponent(VehicleRiderComponent.class, VehicleRiderComponent::new);
      VehicleSeats.init(this.runtimeType, this.riderType, this.loadSeatConfig(), this.getDataDirectory().resolve("seating.json"));
      this.registerUseInteraction();
      this.getEntityStoreRegistry().registerSystem(new VehicleProbeSystem(this.vehicleType));
      this.getEntityStoreRegistry().registerSystem(new SeatInputSystem(this.riderType));
      this.getEntityStoreRegistry().registerSystem(new VehiclePhysicsSystem(this.runtimeType));
      this.getEntityStoreRegistry().registerSystem(new SeatFollowSystem(this.runtimeType));
      this.getEntityStoreRegistry().registerSystem(new SeatTrackerSystem(this.riderType));
      this.getEntityStoreRegistry().registerSystem(new SeatTeleportWatcher(this.riderType));
      this.getEntityStoreRegistry().registerSystem(new VehicleInteractableSystem(this.runtimeType));
      this.getEntityStoreRegistry().registerSystem(new VehicleMountCleanupSystem(this.runtimeType));
      this.getEntityStoreRegistry().registerSystem(new VehicleRiderLifecycle.Remove());
      this.getEntityStoreRegistry().registerSystem(new VehicleRiderLifecycle.Death());
      this.getEntityStoreRegistry().registerSystem(new VehicleRiderLifecycle.Spectate());
      this.seatFilter = PacketAdapters.registerInbound(new SeatPacketFilter());
      this.getCommandRegistry()
         .registerCommand(
            new VehicleCommand(this.vehicleType, this.runtimeType, new VehicleProfiles(this.getDataDirectory()), this.getDataDirectory().resolve("debug"))
         );
      ((Api)this.getLogger().atInfo())
         .log("BeepBeep VehicleCore " + VERSION + ": suspension chassis, seats (F to enter, hold crouch to leave), seat input filter and diagnostics registered.");
   }

   private SeatConfig loadSeatConfig() {
      Path path = this.getDataDirectory().resolve("seating.json");
      try {
         return SeatConfig.load(path);
      } catch (IllegalArgumentException error) {
         ((Api)this.getLogger().at(Level.WARNING)).log("BeepBeep: " + error.getMessage() + " — using default seat settings.");
         return SeatConfig.defaults();
      }
   }

   /** Клавиша F по машине. Как у ванильного UseNPCInteraction: тип, взаимодействие и корень регистрируются кодом. */
   private void registerUseInteraction() {
      try {
         this.getCodecRegistry(Interaction.CODEC).register(VehicleUseInteraction.TYPE, VehicleUseInteraction.class, VehicleUseInteraction.CODEC);
         String pack = String.valueOf(this.getIdentifier());
         Interaction.getAssetStore().loadAssets(pack, List.of(new VehicleUseInteraction(VehicleUseInteraction.ID)));
         RootInteraction.getAssetStore().loadAssets(pack, List.of(VehicleUseInteraction.ROOT));
         VehicleUseInteraction.markRegistered();
      } catch (RuntimeException error) {
         ((Api)this.getLogger().at(Level.WARNING).withCause(error))
            .log("BeepBeep: could not register the vehicle Use interaction (F). Use /vehicle mount instead.");
      }
   }
}
