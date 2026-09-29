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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.joml.Vector3d;

public final class VehicleCommand extends CommandBase {
   private static final float MODEL_SCALE = 2.5F;
   private final ComponentType<EntityStore, VehicleRuntimeComponent> runtimeType;

   public VehicleCommand(
      ComponentType<EntityStore, VehiclePhysicsComponent> var1, ComponentType<EntityStore, VehicleRuntimeComponent> var2, VehicleProfiles var3, Path var4
   ) {
      super("vehicle", "BeepBeep profile editor and diagnostics");
      this.runtimeType = var2;
      this.requirePermission("beepbeep.vehicle.admin");
      this.addSubCommand(new VehicleCommand.ProbeCommand(var1, var2, var3));
      this.addSubCommand(new VehicleSelfTestCommand(var1));
      this.addSubCommand(new VehicleCommand.EditCommand(var2, var3));
      this.addSubCommand(new VehicleCommand.SpawnVehicleCommand(var2, var3));
      this.addSubCommand(new VehicleCommand.ClearVehicleCommand(var2));
      this.addSubCommand(new VehicleCommand.StatusVehicleCommand(var2));
      this.addSubCommand(new VehicleCommand.DriveVehicleCommand(var2));
      this.addSubCommand(new VehicleCommand.MountVehicleCommand(var2, var4));
      this.addSubCommand(new VehicleCommand.DismountVehicleCommand(var2));
      this.addSubCommand(new VehicleCommand.StopVehicleCommand(var2));
      this.addSubCommand(new VehicleDebugCommand(var2, var4));
   }

   protected void executeSync(CommandContext var1) {
      var1.sendMessage(Message.raw("BeepBeep: /vehicle spawn — тестовое шасси с подвеской; /vehicle edit — профиль; /vehicle probe ..."));
   }

   private static void releaseDriver(Store<EntityStore> var0, ComponentType<EntityStore, VehicleRuntimeComponent> var1, Ref<EntityStore> var2) {
      ArrayList var3 = new ArrayList();
      var0.forEachChunk(var1, (var3x, var4) -> {
         for (int var5x = 0; var5x < var3x.size(); var5x++) {
            if (((VehicleRuntimeComponent)var3x.getComponent(var5x, var1)).driver == var2) {
               var3.add(var3x.getReferenceTo(var5x));
            }
         }
      });

      for (Ref var5 : var3) {
         VehicleRuntimeComponent var6 = (VehicleRuntimeComponent)var0.getComponent(var5, var1);
         VehicleMount.release(var0, var6, new Vector3d(((TransformComponent)var0.getComponent(var5, TransformComponent.getComponentType())).getPosition()));
      }
   }

   private static Ref<EntityStore> nearestVehicle(
      Store<EntityStore> var0, Ref<EntityStore> var1, ComponentType<EntityStore, VehicleRuntimeComponent> var2, double var3
   ) {
      TransformComponent var5 = (TransformComponent)var0.getComponent(var1, TransformComponent.getComponentType());
      if (var5 == null) {
         return null;
      } else {
         AtomicReference var6 = new AtomicReference();
         double[] var7 = new double[]{var3};
         var0.forEachChunk(var2, (var3x, var4) -> {
            for (int var5x = 0; var5x < var3x.size(); var5x++) {
               TransformComponent var6x = (TransformComponent)var3x.getComponent(var5x, TransformComponent.getComponentType());
               if (var6x != null) {
                  double var7x = var6x.getPosition().distance(var5.getPosition());
                  if (var7x <= var7[0]) {
                     var7[0] = var7x;
                     var6.set(var3x.getReferenceTo(var5x));
                  }
               }
            }
         });
         return (Ref<EntityStore>)var6.get();
      }
   }

   private static void openEditor(
      CommandContext var0,
      Store<EntityStore> var1,
      Ref<EntityStore> var2,
      PlayerRef var3,
      ComponentType<EntityStore, VehicleRuntimeComponent> var4,
      VehicleProfiles var5
   ) {
      try {
         Ref var6 = nearestVehicle(var1, var2, var4, 12.0);
         if (var6 == null) {
            var0.sendMessage(Message.raw("Нет машины в радиусе 12 блоков. Подойдите к ней или используйте /vehicle spawn."));
            return;
         }

         Player var7 = (Player)var1.getComponent(var2, Player.getComponentType());
         if (var7 != null) {
            var7.getPageManager().openCustomPage(var2, var1, new VehicleEditorPage(var3, var5, var6, var4));
         }
      } catch (IllegalArgumentException | IOException var8) {
         var0.sendMessage(Message.raw("Не удалось открыть редактор: " + var8.getMessage() + ". Профиль: " + var5.path()));
      }
   }

   private static final class ClearVehicleCommand extends AbstractPlayerCommand {
      private final ComponentType<EntityStore, VehicleRuntimeComponent> type;

      ClearVehicleCommand(ComponentType<EntityStore, VehicleRuntimeComponent> var1) {
         super("clear", "Remove spawned BeepBeep chassis");
         this.type = var1;
         this.requirePermission("beepbeep.vehicle.admin");
      }

      protected void execute(CommandContext var1, Store<EntityStore> var2, Ref<EntityStore> var3, PlayerRef var4, World var5) {
         int[] var6 = new int[]{0};
         var2.forEachChunk(this.type, (var1x, var2x) -> {
            for (int var3x = 0; var3x < var1x.size(); var3x++) {
               var2x.removeEntity(var1x.getReferenceTo(var3x), RemoveReason.REMOVE);
               var6[0]++;
            }
         });
         var1.sendMessage(Message.raw("BeepBeep chassis removed: " + var6[0]));
      }
   }

   private static final class DismountVehicleCommand extends AbstractPlayerCommand {
      private final ComponentType<EntityStore, VehicleRuntimeComponent> type;

      DismountVehicleCommand(ComponentType<EntityStore, VehicleRuntimeComponent> var1) {
         super("dismount", "Leave the vehicle");
         this.type = var1;
         this.requirePermission("beepbeep.vehicle.admin");
      }

      protected void execute(CommandContext var1, Store<EntityStore> var2, Ref<EntityStore> var3, PlayerRef var4, World var5) {
         VehicleCommand.releaseDriver(var2, this.type, var3);
         var1.sendMessage(Message.raw("Вы вышли из транспорта."));
      }
   }

   private static final class DriveVehicleCommand extends AbstractPlayerCommand {
      private final ComponentType<EntityStore, VehicleRuntimeComponent> type;

      DriveVehicleCommand(ComponentType<EntityStore, VehicleRuntimeComponent> var1) {
         super("drive", "Assign the nearest chassis to this player");
         this.type = var1;
         this.requirePermission("beepbeep.vehicle.admin");
      }

      protected void execute(CommandContext var1, Store<EntityStore> var2, Ref<EntityStore> var3, PlayerRef var4, World var5) {
         TransformComponent var6 = (TransformComponent)var2.getComponent(var3, TransformComponent.getComponentType());
         AtomicReference var7 = new AtomicReference();
         double[] var8 = new double[]{Double.POSITIVE_INFINITY};
         var2.forEachChunk(this.type, (var3x, var4x) -> {
            for (int var5x = 0; var5x < var3x.size(); var5x++) {
               TransformComponent var6x = (TransformComponent)var3x.getComponent(var5x, TransformComponent.getComponentType());
               double var7x = var6x.getPosition().distance(var6.getPosition());
               if (var7x < var8[0]) {
                  var8[0] = var7x;
                  var7.set(var3x.getReferenceTo(var5x));
               }
            }
         });
         if (var7.get() != null && !(var8[0] > 12.0)) {
            VehicleRuntimeComponent var9 = (VehicleRuntimeComponent)var2.getComponent((Ref)var7.get(), this.type);
            if (var9.driver != null && var9.driver.isValid() && var9.driver != var3) {
               var1.sendMessage(Message.raw("Шасси уже управляется другим игроком."));
            } else {
               VehicleCommand.releaseDriver(var2, this.type, var3);
               var9.driver = var3;
               var9.driven = true;
               var9.throttle = 0.0;
               var9.steer = 0.0;
               var9.brake = 1.0;
               var9.inputAge = 1.0;
               var1.sendMessage(
                  Message.raw("Управление снаружи включено. Идите W/S, A/D задают руль относительно взгляда. /vehicle stop — отключить. Посадки пока нет.")
               );
            }
         } else {
            var1.sendMessage(Message.raw("Нет шасси в радиусе 12 блоков. Сначала /vehicle spawn."));
         }
      }
   }

   private static final class EditCommand extends AbstractPlayerCommand {
      private final ComponentType<EntityStore, VehicleRuntimeComponent> runtimeType;
      private final VehicleProfiles profiles;

      EditCommand(ComponentType<EntityStore, VehicleRuntimeComponent> var1, VehicleProfiles var2) {
         super("edit", "Open the nearest vehicle editor");
         this.runtimeType = var1;
         this.profiles = var2;
         this.requirePermission("beepbeep.vehicle.admin");
      }

      protected void execute(CommandContext var1, Store<EntityStore> var2, Ref<EntityStore> var3, PlayerRef var4, World var5) {
         VehicleCommand.openEditor(var1, var2, var3, var4, this.runtimeType, this.profiles);
      }
   }

   private static final class MountVehicleCommand extends AbstractPlayerCommand {
      private final ComponentType<EntityStore, VehicleRuntimeComponent> type;
      private final Path debugFolder;

      MountVehicleCommand(ComponentType<EntityStore, VehicleRuntimeComponent> var1, Path var2) {
         super("mount", "Enter the nearest BeepBeep driver seat");
         this.debugFolder = var2;
         this.type = var1;
         this.requirePermission("beepbeep.vehicle.admin");
      }

      protected void execute(CommandContext var1, Store<EntityStore> var2, Ref<EntityStore> var3, PlayerRef var4, World var5) {
         TransformComponent var6 = (TransformComponent)var2.getComponent(var3, TransformComponent.getComponentType());
         AtomicReference var7 = new AtomicReference();
         double[] var8 = new double[]{Double.POSITIVE_INFINITY};
         var2.forEachChunk(this.type, (var3x, var4x) -> {
            for (int var5x = 0; var5x < var3x.size(); var5x++) {
               TransformComponent var6x = (TransformComponent)var3x.getComponent(var5x, TransformComponent.getComponentType());
               double var7x = var6x.getPosition().distance(var6.getPosition());
               if (var7x < var8[0]) {
                  var8[0] = var7x;
                  var7.set(var3x.getReferenceTo(var5x));
               }
            }
         });
         if (var7.get() != null && !(var8[0] > 12.0)) {
            VehicleRuntimeComponent var9 = (VehicleRuntimeComponent)var2.getComponent((Ref)var7.get(), this.type);
            if (var9.driver != null && var9.driver.isValid() && var9.driver != var3) {
               var1.sendMessage(Message.raw("Место водителя занято."));
            } else if (var9.driver == var3 && var9.seated) {
               var1.sendMessage(Message.raw("Сиденье уже подключено. /vehicle status — состояние."));
            } else {
               VehicleCommand.releaseDriver(var2, this.type, var3);

               try {
                  if (var9.debug == null || !var9.debug.trace.active()) {
                     var9.debug = new VehicleDebug(this.debugFolder, var3);
                  }

                  VehicleMount.mount(var2, var9, var3, var4, (Ref<EntityStore>)var7.get());
                  var1.sendMessage(Message.raw("Диагностика включена на 90 секунд: " + var9.debug.trace.path));
                  var1.sendMessage(
                     Message.raw(
                        "Подключаю сиденье. A/D — руль, W — вперёд, S — назад; камера фиксирована, /vehicle dismount — выйти. /vehicle status — диагностика."
                     )
                  );
               } catch (RuntimeException var11) {
                  var1.sendMessage(Message.raw("Посадка не выполнена: " + var11.getMessage()));
               }
            }
         } else {
            var1.sendMessage(Message.raw("Нет шасси в радиусе 12 блоков. Сначала /vehicle spawn."));
         }
      }
   }

   private static final class ProbeCommand extends AbstractPlayerCommand {
      private final ComponentType<EntityStore, VehiclePhysicsComponent> type;
      private final ComponentType<EntityStore, VehicleRuntimeComponent> runtimeType;
      private final VehicleProfiles profiles;
      private final RequiredArg<String> action;

      ProbeCommand(ComponentType<EntityStore, VehiclePhysicsComponent> var1, ComponentType<EntityStore, VehicleRuntimeComponent> var2, VehicleProfiles var3) {
         super("probe", "Test ECS, input, block collisions and transforms");
         this.type = var1;
         this.runtimeType = var2;
         this.profiles = var3;
         this.requirePermission("beepbeep.vehicle.admin");
         this.action = this.withRequiredArg("action", "start/status/surface/spawn/clear/ui/stop", ArgTypes.STRING);
      }

      protected void execute(CommandContext var1, Store<EntityStore> var2, Ref<EntityStore> var3, PlayerRef var4, World var5) {
         String var6 = (String)this.action.get(var1);
         switch (var6) {
            case "start":
               var2.ensureAndGetComponent(var3, this.type);
               var1.sendMessage(Message.raw("Input probe enabled. Walk, mount a vehicle, then run /vehicle probe status."));
               break;
            case "status":
               VehiclePhysicsComponent var16 = (VehiclePhysicsComponent)var2.getComponent(var3, this.type);
               MountedComponent var17 = (MountedComponent)var2.getComponent(var3, MountedComponent.getComponentType());
               Player var18 = (Player)var2.getComponent(var3, Player.getComponentType());
               var1.sendMessage(
                  Message.raw(
                     var16 == null
                        ? "Probe is off. Run /vehicle probe start."
                        : "ticks="
                           + var16.ticks
                           + " inputEvents="
                           + var16.inputEvents
                           + " wishEvents="
                           + var16.wishEvents
                           + " last="
                           + var16.lastInput
                           + " age="
                           + (var16.lastInputTime < 0.0 ? "never" : String.format(Locale.ROOT, "%.2fs", var16.elapsed - var16.lastInputTime))
                           + " wish="
                           + var16.wishX
                           + ","
                           + var16.wishZ
                  )
               );
               var1.sendMessage(Message.raw("MountedComponent=" + (var17 != null) + " playerMountId=" + (var18 == null ? "n/a" : var18.getMountEntityId())));
               break;
            case "surface":
               TransformComponent var15 = (TransformComponent)var2.getComponent(var3, TransformComponent.getComponentType());
               var1.sendMessage(Message.raw(SurfaceProbe.below(var2, new Vector3d(var15.getPosition()).add(0.0, 0.5, 0.0))));
               break;
            case "spawn":
               this.clear(var2);
               ModelAsset var8 = (ModelAsset)ModelAsset.getAssetMap().getAsset("BeepBeep_GreenLandCruiser_AssetModel");
               if (var8 == null) {
                  var1.sendMessage(Message.raw("Enable the original BeepBeep_Vehicle_Pack asset pack first."));
                  return;
               }

               TransformComponent var9 = (TransformComponent)var2.getComponent(var3, TransformComponent.getComponentType());
               Model var10 = Model.createScaledModel(var8, 1.0F);
               Holder var11 = EntityStore.REGISTRY.newHolder();
               Vector3d var12 = new Vector3d(var9.getPosition()).add(3.0, 1.0, 0.0);
               var11.addComponent(TransformComponent.getComponentType(), new TransformComponent(var12, new Rotation3f()));
               var11.addComponent(ModelComponent.getComponentType(), new ModelComponent(var10));
               var11.addComponent(BoundingBox.getComponentType(), new BoundingBox(var10.getBoundingBox()));
               var11.addComponent(NetworkId.getComponentType(), new NetworkId(((EntityStore)var2.getExternalData()).takeNextNetworkId()));
               var11.addComponent(UUIDComponent.getComponentType(), UUIDComponent.randomUUID());
               var11.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
               VehiclePhysicsComponent var13 = new VehiclePhysicsComponent();
               var13.animatedProbe = true;
               var13.baseY = var12.y;
               var11.addComponent(this.type, var13);
               Ref var14 = var2.addEntity(var11, AddReason.SPAWN);
               if (var14 == null || !var14.isValid()) {
                  var1.sendMessage(Message.raw("Probe spawn failed: target chunk unavailable."));
                  return;
               }

               var1.sendMessage(
                  Message.raw("Transform probe spawned 3 blocks east. It bobs and rotates; this is NOT driving physics. /vehicle probe clear removes it.")
               );
               break;
            case "clear":
               this.clear(var2);
               var1.sendMessage(Message.raw("Animated probes removed from this world."));
               break;
            case "stop":
               var2.tryRemoveComponent(var3, this.type);
               var1.sendMessage(Message.raw("Input probe disabled."));
               break;
            case "ui":
               VehicleCommand.openEditor(var1, var2, var3, var4, this.runtimeType, this.profiles);
               break;
            default:
               var1.sendMessage(Message.raw("Use start, status, surface, spawn, clear, ui or stop."));
         }
      }

      private void clear(Store<EntityStore> var1) {
         var1.forEachChunk(this.type, (var1x, var2) -> {
            for (int var3 = 0; var3 < var1x.size(); var3++) {
               if (((VehiclePhysicsComponent)var1x.getComponent(var3, this.type)).animatedProbe) {
                  var2.removeEntity(var1x.getReferenceTo(var3), RemoveReason.REMOVE);
               }
            }
         });
      }
   }

   private static final class SpawnVehicleCommand extends AbstractPlayerCommand {
      private final ComponentType<EntityStore, VehicleRuntimeComponent> type;
      private final VehicleProfiles profiles;

      SpawnVehicleCommand(ComponentType<EntityStore, VehicleRuntimeComponent> var1, VehicleProfiles var2) {
         super("spawn", "Spawn the profile-driven suspension chassis");
         this.type = var1;
         this.profiles = var2;
         this.requirePermission("beepbeep.vehicle.admin");
      }

      protected void execute(CommandContext var1, Store<EntityStore> var2, Ref<EntityStore> var3, PlayerRef var4, World var5) {
         try {
            JsonObject var6 = this.profiles.load().value();
            ModelAsset var7 = (ModelAsset)ModelAsset.getAssetMap().getAsset(var6.get("modelAsset").getAsString());
            if (var7 == null) {
               var1.sendMessage(Message.raw("Model asset not loaded: " + var6.get("modelAsset").getAsString()));
               return;
            }

            TransformComponent var8 = (TransformComponent)var2.getComponent(var3, TransformComponent.getComponentType());
            Vector3d var9 = new Vector3d(var8.getPosition()).add(3.0, 2.0, 0.0);
            Model var10 = Model.createScaledModel(var7, 2.5F);
            Holder var11 = EntityStore.REGISTRY.newHolder();
            var11.addComponent(TransformComponent.getComponentType(), new TransformComponent(var9, new Rotation3f(0.0F, var8.getRotation().yaw(), 0.0F)));
            var11.addComponent(NetworkId.getComponentType(), new NetworkId(((EntityStore)var2.getExternalData()).takeNextNetworkId()));
            var11.addComponent(UUIDComponent.getComponentType(), UUIDComponent.randomUUID());
            var11.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
            VehicleRuntimeComponent var12 = VehicleCalibration.runtime(var6);
            var12.yaw = (double)var8.getRotation().yaw();
            Box var13 = VehicleBody.shape(var12, 0.0, 0.0, 0.0).bounds();
            var10 = Model.createScaledModel(var7, 2.5F, Map.of(), var13);
            var11.addComponent(ModelComponent.getComponentType(), new ModelComponent(var10));
            BoundingBox var14 = new BoundingBox(var13);
            var14.setBaseModelBox(var13);
            var14.applyRotation(0.0F, (float)var12.yaw, 0.0F);
            var11.addComponent(BoundingBox.getComponentType(), var14);
            var11.addComponent(this.type, var12);
            Ref var15 = var2.addEntity(var11, AddReason.SPAWN);
            if (var15 == null || !var15.isValid()) {
               var1.sendMessage(Message.raw("Vehicle spawn failed: chunk unavailable."));
               return;
            }

            var1.sendMessage(Message.raw("Шасси создано. /vehicle mount — занять сиденье; /vehicle dismount — выйти."));
         } catch (Exception var16) {
            var1.sendMessage(Message.raw("Vehicle spawn error: " + var16.getMessage()));
         }
      }
   }

   private static final class StatusVehicleCommand extends AbstractPlayerCommand {
      private final ComponentType<EntityStore, VehicleRuntimeComponent> type;

      StatusVehicleCommand(ComponentType<EntityStore, VehicleRuntimeComponent> var1) {
         super("status", "Show prototype chassis input and speed");
         this.type = var1;
         this.requirePermission("beepbeep.vehicle.admin");
      }

      protected void execute(CommandContext var1, Store<EntityStore> var2, Ref<EntityStore> var3, PlayerRef var4, World var5) {
         TransformComponent var6 = (TransformComponent)var2.getComponent(var3, TransformComponent.getComponentType());
         double[] var7 = new double[]{Double.POSITIVE_INFINITY};
         VehicleRuntimeComponent[] var8 = new VehicleRuntimeComponent[]{null};
         var2.forEachChunk(this.type, (var4x, var5x) -> {
            for (int var6x = 0; var6x < var4x.size(); var6x++) {
               TransformComponent var7x = (TransformComponent)var4x.getComponent(var6x, TransformComponent.getComponentType());
               double var8x = var7x.getPosition().x - var6.getPosition().x;
               double var10x = var7x.getPosition().z - var6.getPosition().z;
               double var12 = Math.sqrt(var8x * var8x + var10x * var10x);
               if (var12 < var7[0]) {
                  var7[0] = var12;
                  var8[0] = (VehicleRuntimeComponent)var4x.getComponent(var6x, this.type);
               }
            }
         });
         if (var8[0] == null) {
            var1.sendMessage(Message.raw("No BeepBeep chassis. Run /vehicle spawn."));
         } else {
            VehicleMountInput.Session var9 = var8[0].mountInput;
            if (var9 != null) {
               VehicleMountInput.Sample var10 = var9.lastActive;
               var1.sendMessage(Message.raw("mousePackets=" + var9.mousePackets + " mouseSteer=" + var9.mouseSteer + " controls=fixed-WASD"));
               var1.sendMessage(
                  Message.raw(
                     String.format(
                        Locale.ROOT,
                        "inputHook=%s velocityPackets=%d lastActive=%s right=%.2f forward=%.2f age=%.1fs mountIdle=%s",
                        var9.hookReady,
                        var9.velocityPackets,
                        var10.source(),
                        var10.right(),
                        var10.forward(),
                        var10.at() == 0L ? -1.0 : (double)(System.nanoTime() - var10.at()) / 1.0E9,
                        var9.mountIdle
                     )
                  )
               );
               var1.sendMessage(
                  Message.raw(
                     String.format(
                        Locale.ROOT,
                        "inputRaw packets=%d wishPackets=%d fixed=%s wish=(%.2f,%.2f) velocity=(%.2f,%.2f) source=%s",
                        var9.movementPackets,
                        var9.wishPackets,
                        var9.fixedControls,
                        var9.lastWish.right(),
                        var9.lastWish.forward(),
                        var9.lastVelocity.right(),
                        var9.lastVelocity.forward(),
                        var9.sample.source()
                     )
                  )
               );
            }

            int var15 = 0;

            for (boolean var14 : var8[0].contact) {
               if (var14) {
                  var15++;
               }
            }

            var1.sendMessage(
               Message.raw(
                  "terrain="
                     + var8[0].terrainState
                     + " contacts="
                     + var15
                     + " pitch="
                     + Math.round(Math.toDegrees(var8[0].pitch))
                     + " roll="
                     + Math.round(Math.toDegrees(var8[0].roll))
               )
            );
            var1.sendMessage(
               Message.raw(
                  String.format(
                     Locale.ROOT,
                     "distance=%.2f yours=%s source=%s events=%d positions=%d wishes=%d age=%.2f throttle=%.2f steer=%.2f speed=%.2f gear=%d rpm=%.0f force=%.0f ticks=%d",
                     var7[0],
                     var8[0].driver == var3,
                     var8[0].inputSource,
                     var8[0].inputEvents,
                     var8[0].positionEvents,
                     var8[0].wishEvents,
                     var8[0].inputAge,
                     var8[0].throttle,
                     var8[0].steer,
                     var8[0].velocityForward,
                     var8[0].gear,
                     var8[0].engineRpm,
                     var8[0].engineForce,
                     var8[0].ticks
                  )
               )
            );
         }
      }
   }

   private static final class StopVehicleCommand extends AbstractPlayerCommand {
      private final ComponentType<EntityStore, VehicleRuntimeComponent> type;

      StopVehicleCommand(ComponentType<EntityStore, VehicleRuntimeComponent> var1) {
         super("stop", "Release control and stop your chassis");
         this.type = var1;
         this.requirePermission("beepbeep.vehicle.admin");
      }

      protected void execute(CommandContext var1, Store<EntityStore> var2, Ref<EntityStore> var3, PlayerRef var4, World var5) {
         VehicleCommand.releaseDriver(var2, this.type, var3);
         var1.sendMessage(Message.raw("Управление отключено, ваше шасси остановлено."));
      }
   }
}
