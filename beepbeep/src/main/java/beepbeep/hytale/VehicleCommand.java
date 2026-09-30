package beepbeep.hytale;

import com.google.gson.JsonObject;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.joml.Vector3d;

public final class VehicleCommand extends CommandBase {
   private static final float MODEL_SCALE = 2.5F;
   private static final double NEAR = 12.0;
   /** Права на посадку: по умолчанию есть у всех игроков (группа Adventurer). */
   static final String RIDE_PERMISSION = "beepbeep.vehicle.ride";
   static final String DEFAULT_PLAYER_GROUP = "hytale:Adventurer";

   public VehicleCommand(
      ComponentType<EntityStore, VehiclePhysicsComponent> probeType, ComponentType<EntityStore, VehicleRuntimeComponent> type, VehicleProfiles profiles, Path debugFolder
   ) {
      super("vehicle", "BeepBeep vehicles: seats, profile editor and diagnostics");
      this.requirePermission("beepbeep.vehicle.admin");
      this.addSubCommand(new VehicleCommand.ProbeCommand(probeType, type, profiles));
      this.addSubCommand(new VehicleSelfTestCommand(probeType));
      this.addSubCommand(new VehicleCommand.EditCommand(type, profiles));
      this.addSubCommand(new VehicleCommand.SpawnVehicleCommand(type, profiles));
      this.addSubCommand(new VehicleCommand.ClearVehicleCommand(type));
      this.addSubCommand(new VehicleCommand.StatusVehicleCommand(type));
      this.addSubCommand(new VehicleCommand.DriveVehicleCommand(type));
      this.addSubCommand(new VehicleCommand.MountVehicleCommand());
      this.addSubCommand(new VehicleCommand.SeatVehicleCommand());
      this.addSubCommand(new VehicleCommand.DismountVehicleCommand());
      this.addSubCommand(new VehicleCommand.ViewVehicleCommand());
      this.addSubCommand(new VehicleCommand.SeatsVehicleCommand());
      this.addSubCommand(new VehicleCommand.SeatConfigCommand());
      this.addSubCommand(new VehicleCommand.StopVehicleCommand(type));
      this.addSubCommand(new VehicleDebugCommand(type, debugFolder));
   }

   @Override
   protected void executeSync(CommandContext context) {
      context.sendMessage(
         Message.raw(
            "BeepBeep: /vehicle spawn — машина; F по машине или /vehicle mount — сесть; /vehicle seat <номер>; /vehicle dismount; /vehicle view first|third|chase;"
               + " /vehicle seats; /vehicle status; /vehicle seatcfg; /vehicle edit; /vehicle debug start"
         )
      );
   }

   /** Снимает управление «снаружи» (/vehicle drive) со всех машин этого игрока. */
   private static void releaseWalkDriver(Store<EntityStore> store, ComponentType<EntityStore, VehicleRuntimeComponent> type, Ref<EntityStore> player) {
      List<VehicleRuntimeComponent> driven = new ArrayList<>();
      store.forEachChunk(type, (chunk, buffer) -> {
         for (int i = 0; i < chunk.size(); i++) {
            VehicleRuntimeComponent runtime = chunk.getComponent(i, type);
            if (runtime != null && runtime.driver == player) {
               driven.add(runtime);
            }
         }
      });

      for (VehicleRuntimeComponent runtime : driven) {
         VehicleControl.reset(runtime);
         runtime.velocityForward = 0.0;
      }
   }

   private static void openEditor(
      CommandContext context,
      Store<EntityStore> store,
      Ref<EntityStore> player,
      PlayerRef playerRef,
      ComponentType<EntityStore, VehicleRuntimeComponent> type,
      VehicleProfiles profiles
   ) {
      try {
         Ref<EntityStore> vehicle = VehicleSeats.nearestVehicle(store, player, NEAR);
         if (vehicle == null) {
            context.sendMessage(Message.raw("Нет машины в радиусе 12 блоков. Подойдите к ней или используйте /vehicle spawn."));
            return;
         }

         Player playerComponent = store.getComponent(player, Player.getComponentType());
         if (playerComponent != null) {
            playerComponent.getPageManager().openCustomPage(player, store, new VehicleEditorPage(playerRef, profiles, vehicle, type));
         }
      } catch (IllegalArgumentException | IOException error) {
         context.sendMessage(Message.raw("Не удалось открыть редактор: " + error.getMessage() + ". Профиль: " + profiles.path()));
      }
   }

   /** Машина, в которой сидит игрок, иначе ближайшая. */
   private static Ref<EntityStore> ownOrNearest(Store<EntityStore> store, Ref<EntityStore> player, double radius) {
      Ref<EntityStore> own = VehicleSeats.vehicleOf(store, player);
      return own != null ? own : VehicleSeats.nearestVehicle(store, player, radius);
   }

   private static final class ClearVehicleCommand extends AbstractPlayerCommand {
      private final ComponentType<EntityStore, VehicleRuntimeComponent> type;

      ClearVehicleCommand(ComponentType<EntityStore, VehicleRuntimeComponent> type) {
         super("clear", "Remove spawned BeepBeep chassis");
         this.type = type;
         this.requirePermission("beepbeep.vehicle.admin");
      }

      @Override
      protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> player, PlayerRef playerRef, World world) {
         int[] removed = new int[]{0};
         store.forEachChunk(this.type, (chunk, buffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
               buffer.removeEntity(chunk.getReferenceTo(i), RemoveReason.REMOVE);
               removed[0]++;
            }
         });
         context.sendMessage(Message.raw("Удалено машин: " + removed[0]));
      }
   }

   private static final class MountVehicleCommand extends AbstractPlayerCommand {
      MountVehicleCommand() {
         super("mount", "Sit in the nearest free seat of the nearest vehicle");
         this.requirePermission(RIDE_PERMISSION);
         this.setPermissionGroups(DEFAULT_PLAYER_GROUP);
      }

      @Override
      protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> player, PlayerRef playerRef, World world) {
         String error = VehicleSeats.enterNearest(store, player, NEAR, -1);
         if (error != null) {
            context.sendMessage(Message.raw(error));
         }
      }
   }

   private static final class SeatVehicleCommand extends AbstractPlayerCommand {
      private final RequiredArg<Integer> seat;

      SeatVehicleCommand() {
         super("seat", "Sit in (or move to) seat number N of the nearest vehicle");
         this.requirePermission(RIDE_PERMISSION);
         this.setPermissionGroups(DEFAULT_PLAYER_GROUP);
         this.seat = this.withRequiredArg("number", "seat number, from 1", ArgTypes.INTEGER);
      }

      @Override
      protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> player, PlayerRef playerRef, World world) {
         int index = this.seat.get(context) - 1;
         String result = VehicleSeats.vehicleOf(store, player) != null
            ? VehicleSeats.switchSeat(store, player, index)
            : VehicleSeats.enterNearest(store, player, NEAR, index);
         if (result != null) {
            context.sendMessage(Message.raw(result));
         }
      }
   }

   private static final class DismountVehicleCommand extends AbstractPlayerCommand {
      DismountVehicleCommand() {
         super("dismount", "Leave the vehicle");
         this.requirePermission(RIDE_PERMISSION);
         this.setPermissionGroups(DEFAULT_PLAYER_GROUP);
      }

      @Override
      protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> player, PlayerRef playerRef, World world) {
         if (!VehicleSeats.release(store, player, "команда", true)) {
            context.sendMessage(Message.raw("Вы не сидите в машине."));
         }
      }
   }

   private static final class ViewVehicleCommand extends AbstractPlayerCommand {
      private final RequiredArg<String> mode;

      ViewVehicleCommand() {
         super("view", "Seat camera: first, third (mouse orbit) or chase");
         this.requirePermission(RIDE_PERMISSION);
         this.setPermissionGroups(DEFAULT_PLAYER_GROUP);
         this.mode = this.withRequiredArg("mode", "first/third/chase", ArgTypes.STRING);
      }

      @Override
      protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> player, PlayerRef playerRef, World world) {
         SeatCamera.View view = SeatCamera.View.parse(this.mode.get(context), null);
         context.sendMessage(Message.raw(view == null ? "Виды: first, third, chase." : VehicleSeats.setView(store, player, view)));
      }
   }

   private static final class SeatsVehicleCommand extends AbstractPlayerCommand {
      SeatsVehicleCommand() {
         super("seats", "List the seats of the nearest vehicle");
         this.requirePermission(RIDE_PERMISSION);
         this.setPermissionGroups(DEFAULT_PLAYER_GROUP);
      }

      @Override
      protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> player, PlayerRef playerRef, World world) {
         Ref<EntityStore> vehicle = ownOrNearest(store, player, NEAR);
         VehicleRuntimeComponent runtime = vehicle == null ? null : store.getComponent(vehicle, VehicleSeats.vehicleType);
         context.sendMessage(Message.raw(runtime == null ? "Рядом нет машины." : VehicleSeats.describeSeats(store, runtime)));
      }
   }

   private static final class SeatConfigCommand extends AbstractPlayerCommand {
      SeatConfigCommand() {
         super("seatcfg", "Reload seating.json (camera, controls, riders)");
         this.requirePermission("beepbeep.vehicle.admin");
      }

      @Override
      protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> player, PlayerRef playerRef, World world) {
         context.sendMessage(Message.raw(VehicleSeats.reloadConfig()));
      }
   }

   private static final class DriveVehicleCommand extends AbstractPlayerCommand {
      private final ComponentType<EntityStore, VehicleRuntimeComponent> type;

      DriveVehicleCommand(ComponentType<EntityStore, VehicleRuntimeComponent> type) {
         super("drive", "Control the nearest chassis by walking next to it");
         this.type = type;
         this.requirePermission("beepbeep.vehicle.admin");
      }

      @Override
      protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> player, PlayerRef playerRef, World world) {
         Ref<EntityStore> vehicle = VehicleSeats.nearestVehicle(store, player, NEAR);
         VehicleRuntimeComponent runtime = vehicle == null ? null : store.getComponent(vehicle, this.type);
         if (runtime == null) {
            context.sendMessage(Message.raw("Нет машины в радиусе 12 блоков. Сначала /vehicle spawn."));
         } else if (runtime.occupant(runtime.seatLayout.driverIndex()) != null) {
            context.sendMessage(Message.raw("На месте водителя кто-то сидит."));
         } else if (runtime.driver != null && runtime.driver.isValid() && runtime.driver != player) {
            context.sendMessage(Message.raw("Машиной уже управляет другой игрок."));
         } else {
            VehicleCommand.releaseWalkDriver(store, this.type, player);
            runtime.driver = player;
            runtime.driven = true;
            runtime.throttle = 0.0;
            runtime.steer = 0.0;
            runtime.brake = 1.0;
            runtime.inputAge = 1.0;
            context.sendMessage(Message.raw("Управление снаружи: идите W/S, A/D задают руль относительно взгляда. /vehicle stop — отключить."));
         }
      }
   }

   private static final class StopVehicleCommand extends AbstractPlayerCommand {
      private final ComponentType<EntityStore, VehicleRuntimeComponent> type;

      StopVehicleCommand(ComponentType<EntityStore, VehicleRuntimeComponent> type) {
         super("stop", "Release outside control and stop your chassis");
         this.type = type;
         this.requirePermission("beepbeep.vehicle.admin");
      }

      @Override
      protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> player, PlayerRef playerRef, World world) {
         VehicleCommand.releaseWalkDriver(store, this.type, player);
         context.sendMessage(Message.raw("Управление снаружи отключено, машина остановлена."));
      }
   }

   private static final class EditCommand extends AbstractPlayerCommand {
      private final ComponentType<EntityStore, VehicleRuntimeComponent> type;
      private final VehicleProfiles profiles;

      EditCommand(ComponentType<EntityStore, VehicleRuntimeComponent> type, VehicleProfiles profiles) {
         super("edit", "Open the nearest vehicle editor");
         this.type = type;
         this.profiles = profiles;
         this.requirePermission("beepbeep.vehicle.admin");
      }

      @Override
      protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> player, PlayerRef playerRef, World world) {
         VehicleCommand.openEditor(context, store, player, playerRef, this.type, this.profiles);
      }
   }

   private static final class ProbeCommand extends AbstractPlayerCommand {
      private final ComponentType<EntityStore, VehiclePhysicsComponent> type;
      private final ComponentType<EntityStore, VehicleRuntimeComponent> runtimeType;
      private final VehicleProfiles profiles;
      private final RequiredArg<String> action;

      ProbeCommand(ComponentType<EntityStore, VehiclePhysicsComponent> type, ComponentType<EntityStore, VehicleRuntimeComponent> runtimeType, VehicleProfiles profiles) {
         super("probe", "Test ECS, input, block collisions and transforms");
         this.type = type;
         this.runtimeType = runtimeType;
         this.profiles = profiles;
         this.requirePermission("beepbeep.vehicle.admin");
         this.action = this.withRequiredArg("action", "start/status/surface/spawn/clear/ui/stop", ArgTypes.STRING);
      }

      @Override
      protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> player, PlayerRef playerRef, World world) {
         String action = this.action.get(context);
         switch (action) {
            case "start":
               store.ensureAndGetComponent(player, this.type);
               context.sendMessage(Message.raw("Input probe enabled. Walk, mount a vehicle, then run /vehicle probe status."));
               break;
            case "status":
               VehiclePhysicsComponent probe = store.getComponent(player, this.type);
               MountedComponent mounted = store.getComponent(player, MountedComponent.getComponentType());
               Player playerComponent = store.getComponent(player, Player.getComponentType());
               context.sendMessage(
                  Message.raw(
                     probe == null
                        ? "Probe is off. Run /vehicle probe start."
                        : "ticks="
                           + probe.ticks
                           + " inputEvents="
                           + probe.inputEvents
                           + " wishEvents="
                           + probe.wishEvents
                           + " last="
                           + probe.lastInput
                           + " age="
                           + (probe.lastInputTime < 0.0 ? "never" : String.format(Locale.ROOT, "%.2fs", probe.elapsed - probe.lastInputTime))
                           + " wish="
                           + probe.wishX
                           + ","
                           + probe.wishZ
                  )
               );
               context.sendMessage(
                  Message.raw("MountedComponent=" + (mounted != null) + " playerMountId=" + (playerComponent == null ? "n/a" : playerComponent.getMountEntityId()))
               );
               break;
            case "surface":
               TransformComponent transform = store.getComponent(player, TransformComponent.getComponentType());
               context.sendMessage(Message.raw(SurfaceProbe.below(store, new Vector3d(transform.getPosition()).add(0.0, 0.5, 0.0))));
               break;
            case "spawn":
               this.clear(store);
               ModelAsset asset = ModelAsset.getAssetMap().getAsset("BeepBeep_GreenLandCruiser_AssetModel");
               if (asset == null) {
                  context.sendMessage(Message.raw("Enable the original BeepBeep_Vehicle_Pack asset pack first."));
                  return;
               }

               TransformComponent playerTransform = store.getComponent(player, TransformComponent.getComponentType());
               Model model = Model.createScaledModel(asset, 1.0F);
               Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
               Vector3d position = new Vector3d(playerTransform.getPosition()).add(3.0, 1.0, 0.0);
               holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(position, new Rotation3f()));
               holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
               holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(model.getBoundingBox()));
               holder.addComponent(NetworkId.getComponentType(), new NetworkId(store.getExternalData().takeNextNetworkId()));
               holder.addComponent(UUIDComponent.getComponentType(), UUIDComponent.randomUUID());
               holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
               VehiclePhysicsComponent animated = new VehiclePhysicsComponent();
               animated.animatedProbe = true;
               animated.baseY = position.y;
               holder.addComponent(this.type, animated);
               Ref<EntityStore> spawned = store.addEntity(holder, AddReason.SPAWN);
               if (spawned == null || !spawned.isValid()) {
                  context.sendMessage(Message.raw("Probe spawn failed: target chunk unavailable."));
                  return;
               }

               context.sendMessage(
                  Message.raw("Transform probe spawned 3 blocks east. It bobs and rotates; this is NOT driving physics. /vehicle probe clear removes it.")
               );
               break;
            case "clear":
               this.clear(store);
               context.sendMessage(Message.raw("Animated probes removed from this world."));
               break;
            case "stop":
               store.tryRemoveComponent(player, this.type);
               context.sendMessage(Message.raw("Input probe disabled."));
               break;
            case "ui":
               VehicleCommand.openEditor(context, store, player, playerRef, this.runtimeType, this.profiles);
               break;
            default:
               context.sendMessage(Message.raw("Use start, status, surface, spawn, clear, ui or stop."));
         }
      }

      private void clear(Store<EntityStore> store) {
         store.forEachChunk(this.type, (chunk, buffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
               VehiclePhysicsComponent probe = chunk.getComponent(i, this.type);
               if (probe != null && probe.animatedProbe) {
                  buffer.removeEntity(chunk.getReferenceTo(i), RemoveReason.REMOVE);
               }
            }
         });
      }
   }

   private static final class SpawnVehicleCommand extends AbstractPlayerCommand {
      private final ComponentType<EntityStore, VehicleRuntimeComponent> type;
      private final VehicleProfiles profiles;

      SpawnVehicleCommand(ComponentType<EntityStore, VehicleRuntimeComponent> type, VehicleProfiles profiles) {
         super("spawn", "Spawn the profile-driven suspension chassis");
         this.type = type;
         this.profiles = profiles;
         this.requirePermission("beepbeep.vehicle.admin");
      }

      @Override
      protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> player, PlayerRef playerRef, World world) {
         try {
            JsonObject profile = this.profiles.load().value();
            ModelAsset asset = ModelAsset.getAssetMap().getAsset(profile.get("modelAsset").getAsString());
            if (asset == null) {
               context.sendMessage(Message.raw("Model asset not loaded: " + profile.get("modelAsset").getAsString()));
               return;
            }

            TransformComponent playerTransform = store.getComponent(player, TransformComponent.getComponentType());
            Vector3d position = new Vector3d(playerTransform.getPosition()).add(3.0, 2.0, 0.0);
            Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
            holder.addComponent(
               TransformComponent.getComponentType(), new TransformComponent(position, new Rotation3f(0.0F, playerTransform.getRotation().yaw(), 0.0F))
            );
            holder.addComponent(NetworkId.getComponentType(), new NetworkId(store.getExternalData().takeNextNetworkId()));
            holder.addComponent(UUIDComponent.getComponentType(), UUIDComponent.randomUUID());
            holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
            VehicleRuntimeComponent runtime = VehicleCalibration.runtime(profile);
            runtime.yaw = playerTransform.getRotation().yaw();
            Box bounds = VehicleBody.shape(runtime, 0.0, 0.0, 0.0).bounds();
            Model model = Model.createScaledModel(asset, MODEL_SCALE, Map.of(), bounds);
            holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
            BoundingBox boundingBox = new BoundingBox(bounds);
            boundingBox.setBaseModelBox(bounds);
            boundingBox.applyRotation(0.0F, (float)runtime.yaw, 0.0F);
            holder.addComponent(BoundingBox.getComponentType(), boundingBox);
            holder.addComponent(this.type, runtime);
            Ref<EntityStore> vehicle = store.addEntity(holder, AddReason.SPAWN);
            if (vehicle == null || !vehicle.isValid()) {
               context.sendMessage(Message.raw("Vehicle spawn failed: chunk unavailable."));
               return;
            }

            context.sendMessage(
               Message.raw(
                  "Машина создана, мест: " + runtime.seatCount() + ". Подойдите и нажмите F (или /vehicle mount), выйти — зажать «присесть» (или /vehicle dismount)."
               )
            );
         } catch (Exception error) {
            context.sendMessage(Message.raw("Vehicle spawn error: " + error.getMessage()));
         }
      }
   }

   private static final class StatusVehicleCommand extends AbstractPlayerCommand {
      private final ComponentType<EntityStore, VehicleRuntimeComponent> type;

      StatusVehicleCommand(ComponentType<EntityStore, VehicleRuntimeComponent> type) {
         super("status", "Show seat input, camera and chassis state");
         this.type = type;
         this.requirePermission("beepbeep.vehicle.admin");
      }

      @Override
      protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> player, PlayerRef playerRef, World world) {
         long now = System.nanoTime();
         String rider = VehicleSeats.describeRider(store, player, now);
         if (rider != null) {
            context.sendMessage(Message.raw(rider));
         }

         Ref<EntityStore> vehicle = ownOrNearest(store, player, 64.0);
         VehicleRuntimeComponent runtime = vehicle == null ? null : store.getComponent(vehicle, this.type);
         if (runtime == null) {
            context.sendMessage(Message.raw("Рядом нет машины. /vehicle spawn."));
            return;
         }

         int contacts = 0;

         for (boolean contact : runtime.contact) {
            if (contact) {
               contacts++;
            }
         }

         TransformComponent vehicleTransform = store.getComponent(vehicle, TransformComponent.getComponentType());
         TransformComponent playerTransform = store.getComponent(player, TransformComponent.getComponentType());
         double distance = vehicleTransform != null && playerTransform != null ? vehicleTransform.getPosition().distance(playerTransform.getPosition()) : -1.0;
         context.sendMessage(Message.raw(VehicleSeats.describeSeats(store, runtime)));
         context.sendMessage(
            Message.raw(
               String.format(
                  Locale.ROOT,
                  "машина: расстояние=%.1f земля=%s колёс на земле=%d тангаж=%d° крен=%d° | управление=%s газ=%.2f руль=%.2f тормоз=%.2f скорость=%.2f передача=%d обороты=%.0f тяга=%.0f тиков=%d",
                  distance,
                  runtime.terrainState,
                  contacts,
                  Math.round(Math.toDegrees(runtime.pitch)),
                  Math.round(Math.toDegrees(runtime.roll)),
                  runtime.inputSource,
                  runtime.throttle,
                  runtime.steer,
                  runtime.brake,
                  runtime.velocityForward,
                  runtime.gear,
                  runtime.engineRpm,
                  runtime.engineForce,
                  runtime.ticks
               )
            )
         );
      }
   }
}
