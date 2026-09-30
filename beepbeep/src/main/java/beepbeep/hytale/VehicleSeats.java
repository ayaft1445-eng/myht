package beepbeep.hytale;

import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.ComponentUpdateType;
import com.hypixel.hytale.protocol.FlyMode;
import com.hypixel.hytale.protocol.MountController;
import com.hypixel.hytale.protocol.MountedUpdate;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.packets.player.UpdateMovementSettings;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.CameraManager;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Spectating;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.entity.teleport.TeleportSystems;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.EntityViewer;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.Visible;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;

/**
 * Места в машинах: посадка, пересадка, высадка, «кукла» водителя и камера.
 *
 * Как это устроено:
 * <ul>
 *    <li>Для каждого занятого места создаётся невидимая точка крепления (anchor), которая
 *    каждый тик ставится на сиденье с учётом поворота, наклона и крена машины.</li>
 *    <li>Остальные игроки видят седока пристёгнутым к этой точке (MountedUpdate), с позой
 *    «сидит». Самому седоку этот пакет не отправляется, иначе его клиент начнёт сам
 *    управлять креплением — из-за этого в 0.3.x всё и ехало вкривь.</li>
 *    <li>Сервер держит седока ровно на сиденье, а собственный персонаж седока на его
 *    клиенте летает высоко над машиной. Камера клиента при этом висит на машине. По
 *    сдвигу этого персонажа читаются клавиши (SeatInputDecoder).</li>
 * </ul>
 *
 * Все методы, кроме input(), вызываются в потоке мира.
 */
public final class VehicleSeats {
   private static final ConcurrentHashMap<UUID, SeatInput> INPUTS = new ConcurrentHashMap<>();
   private static final ConcurrentHashMap<UUID, VehicleRiderComponent> RIDERS = new ConcurrentHashMap<>();
   private static final long RECENTER_COOLDOWN_NANOS = 300_000_000L;
   private static final long LOST_PUPPET_RETRY_NANOS = 1_500_000_000L;
   private static final long CAMERA_INTERVAL_NANOS = 30_000_000L;
   private static final float CAMERA_EPSILON = 0.002F;
   private static final Rotation3f KEEP_ROTATION = new Rotation3f(Float.NaN, Float.NaN, Float.NaN);
   private static final Box ANCHOR_BOX = new Box(-0.05, 0.0, -0.05, 0.05, 0.1, 0.05);
   private static final Box PLAYER_BOX = new Box(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3);
   static ComponentType<EntityStore, VehicleRuntimeComponent> vehicleType;
   static ComponentType<EntityStore, VehicleRiderComponent> riderType;
   private static volatile SeatConfig config = SeatConfig.defaults();
   private static volatile Path configPath;

   private VehicleSeats() {
   }

   static void init(
      ComponentType<EntityStore, VehicleRuntimeComponent> vehicle, ComponentType<EntityStore, VehicleRiderComponent> rider, SeatConfig seatConfig, Path path
   ) {
      vehicleType = vehicle;
      riderType = rider;
      config = seatConfig;
      configPath = path;
   }

   static SeatConfig config() {
      return config;
   }

   /** Перечитывает seating.json. Возвращает текст для игрока. */
   static String reloadConfig() {
      Path path = configPath;
      if (path == null) {
         return "Путь к seating.json неизвестен.";
      } else {
         try {
            config = SeatConfig.load(path);
            return "seating.json перечитан: " + path + ". Новые настройки действуют со следующей посадки.";
         } catch (IllegalArgumentException error) {
            return "Ошибка в seating.json, оставлены прежние настройки: " + error.getMessage();
         }
      }
   }

   /** Вызывается из сетевого потока. */
   static SeatInput input(UUID player) {
      return INPUTS.get(player);
   }

   // ---------------------------------------------------------------- посадка

   /** Посадка на ближайшую машину в радиусе. seat — номер места с нуля или -1 для ближайшего свободного. */
   static String enterNearest(Store<EntityStore> store, Ref<EntityStore> player, double radius, int seat) {
      Ref<EntityStore> vehicle = nearestVehicle(store, player, radius);
      return vehicle == null ? "Нет машины в радиусе " + (int)radius + " блоков. Сначала /vehicle spawn." : enter(store, player, vehicle, seat);
   }

   /** Клавиша «использовать» (F) по машине: сесть или, если уже сидишь в ней, выйти. */
   static void use(Store<EntityStore> store, Ref<EntityStore> player, Ref<EntityStore> vehicle) {
      if (valid(store, player) && valid(store, vehicle)) {
         VehicleRiderComponent rider = store.getComponent(player, riderType);
         PlayerRef playerRef = store.getComponent(player, PlayerRef.getComponentType());
         if (rider != null) {
            if (vehicle.equals(rider.vehicle)) {
               release(store, player, "клавиша взаимодействия", true);
            } else if (playerRef != null) {
               playerRef.sendMessage(Message.raw("Вы уже сидите в другой машине."));
            }
         } else {
            String error = enter(store, player, vehicle, -1);
            if (error != null && playerRef != null) {
               playerRef.sendMessage(Message.raw(error));
            }
         }
      }
   }

   /** Сажает игрока в машину. Возвращает null или текст ошибки. */
   static String enter(Store<EntityStore> store, Ref<EntityStore> player, Ref<EntityStore> vehicle, int requestedSeat) {
      if (!valid(store, player) || !valid(store, vehicle)) {
         return "Машина или игрок уже недоступны.";
      }

      VehicleRuntimeComponent runtime = store.getComponent(vehicle, vehicleType);
      PlayerRef playerRef = store.getComponent(player, PlayerRef.getComponentType());
      Player playerComponent = store.getComponent(player, Player.getComponentType());
      TransformComponent playerTransform = store.getComponent(player, TransformComponent.getComponentType());
      TransformComponent vehicleTransform = store.getComponent(vehicle, TransformComponent.getComponentType());
      if (runtime == null) {
         return "Это не машина BeepBeep.";
      } else if (playerRef == null || playerComponent == null || playerTransform == null || vehicleTransform == null) {
         return "Сесть может только игрок.";
      } else if (store.getComponent(player, riderType) != null) {
         return "Вы уже сидите в машине. Выйти: зажать «присесть» или /vehicle dismount.";
      } else if (store.getComponent(player, DeathComponent.getComponentType()) != null) {
         return "Нельзя сесть в машину мёртвым.";
      } else if (Spectating.isSpectating(player, store)) {
         return "Нельзя сесть в машину в режиме наблюдателя.";
      } else if (store.getComponent(player, MountedComponent.getComponentType()) != null || playerComponent.getMountEntityId() != 0) {
         return "Сначала слезьте с текущего транспорта.";
      } else if (store.getComponent(player, Teleport.getComponentType()) != null || !playerRef.getTeleportAckTracker().isEmpty()) {
         return "Подождите окончания телепорта и попробуйте снова.";
      }

      Vector3d origin = new Vector3d(vehicleTransform.getPosition());
      if (playerTransform.getPosition().distance(origin) > config.riders.enterDistance + Math.max(runtime.halfX, runtime.halfZ) + 2.0) {
         return "Подойдите ближе к машине.";
      }

      runtime.fitOccupants();
      pruneOccupants(store, runtime);
      int seat;
      if (requestedSeat >= 0) {
         if (requestedSeat >= runtime.seatCount()) {
            return "В этой машине " + runtime.seatCount() + " мест(а).";
         }

         if (runtime.occupant(requestedSeat) != null) {
            return "Место " + (requestedSeat + 1) + " (" + runtime.seatLayout.get(requestedSeat).id + ") занято.";
         }

         seat = requestedSeat;
      } else {
         Vector3d local = SeatMath.toLocal(origin, runtime.yaw, runtime.pitch, runtime.roll, playerTransform.getPosition());
         seat = runtime.seatLayout.nearestFree(local.x, local.y, local.z, runtime.occupiedMask());
         if (seat < 0) {
            return "Все места заняты.";
         }
      }

      Vector3d seatPosition = seatPosition(runtime, origin, seat);
      Rotation3f seatRotation = seatRotation(runtime, seat);
      Ref<EntityStore> anchor;
      try {
         anchor = spawnAnchor(store, seatPosition, seatRotation);
      } catch (RuntimeException error) {
         return "Не удалось создать место: " + error.getMessage();
      }

      NetworkId anchorId = store.getComponent(anchor, NetworkId.getComponentType());
      NetworkId vehicleId = store.getComponent(vehicle, NetworkId.getComponentType());
      if (anchorId == null || vehicleId == null) {
         removeAnchor(store, anchor);
         return "У машины нет сетевого номера — пересоздайте её через /vehicle spawn.";
      }

      long now = System.nanoTime();
      SeatConfig settings = config;
      SeatInput input = new SeatInput(playerRef.getUuid(), settings.decoderSettings(), settings.controls.useWish, now);
      VehicleRiderComponent rider = new VehicleRiderComponent();
      rider.vehicle = vehicle;
      rider.seat = seat;
      rider.anchor = anchor;
      rider.anchorNetworkId = anchorId.getId();
      rider.vehicleNetworkId = vehicleId.getId();
      rider.playerRef = playerRef;
      rider.input = input;
      rider.view = SeatCamera.View.parse(settings.camera.defaultView, SeatCamera.View.CHASE);
      rider.enteredAt = now;
      input.debug(runtime.debug);
      store.putComponent(player, riderType, rider);
      runtime.setOccupant(seat, player);
      if (seat == runtime.seatLayout.driverIndex()) {
         runtime.driver = null;
         neutral(runtime, "seat-enter");
      }

      INPUTS.put(playerRef.getUuid(), input);
      RIDERS.put(playerRef.getUuid(), rider);
      PlayerInput playerInput = store.getComponent(player, PlayerInput.getComponentType());
      if (playerInput != null) {
         playerInput.getMovementUpdateQueue().clear();
      }

      playerComponent.moveTo(player, seatPosition.x, seatPosition.y, seatPosition.z, store);
      playerTransform.getRotation().set(seatRotation);
      MovementStatesComponent states = store.getComponent(player, MovementStatesComponent.getComponentType());
      if (states != null) {
         states.setMovementStates(seatedStates(settings));
      }

      startPuppet(store, player, playerRef, rider, origin, now);
      sendCamera(rider, runtime, seat, true, now);
      if (runtime.debug != null) {
         runtime.debug.trace.event("seat-enter", runtime.seatLayout.get(seat).id);
      }

      SeatLayout.Seat seatInfo = runtime.seatLayout.get(seat);
      playerRef.sendMessage(
         Message.raw(
            "Вы сели: место " + (seat + 1) + " из " + runtime.seatCount() + " (" + seatInfo.id + (seatInfo.driver ? ", водитель" : ", пассажир") + ")."
         )
      );
      playerRef.sendMessage(
         Message.raw(
            seatInfo.driver
               ? "W/S — газ и задний ход, A/D — руль, пробел — ручник. Выйти — зажать «присесть» или F. Средняя кнопка мыши — вид (/vehicle view first|third|chase)."
               : "Выйти — зажать «присесть» или F. Пересесть — /vehicle seat <номер>. Средняя кнопка мыши — вид."
         )
      );
      return null;
   }

   /** Пересадка на другое место той же машины. */
   static String switchSeat(Store<EntityStore> store, Ref<EntityStore> player, int seat) {
      VehicleRiderComponent rider = valid(store, player) ? store.getComponent(player, riderType) : null;
      if (rider == null || rider.releasing) {
         return null;
      }

      VehicleRuntimeComponent runtime = valid(store, rider.vehicle) ? store.getComponent(rider.vehicle, vehicleType) : null;
      if (runtime == null) {
         return "Машина недоступна.";
      } else if (seat < 0 || seat >= runtime.seatCount()) {
         return "В этой машине " + runtime.seatCount() + " мест(а).";
      } else if (seat == rider.seat) {
         return "Вы уже на месте " + (seat + 1) + ".";
      } else {
         pruneOccupants(store, runtime);
         if (runtime.occupant(seat) != null) {
            return "Место " + (seat + 1) + " занято.";
         }

         boolean wasDriver = rider.driver(runtime);
         runtime.clearOccupant(rider.seat, player);
         runtime.setOccupant(seat, player);
         rider.seat = seat;
         if (wasDriver) {
            neutral(runtime, "seat-switch");
         }

         if (rider.input != null) {
            rider.input.clearHolds();
         }

         sendCamera(rider, runtime, seat, true, System.nanoTime());
         SeatLayout.Seat info = runtime.seatLayout.get(seat);
         return "Вы пересели на место " + (seat + 1) + " (" + info.id + (info.driver ? ", водитель" : "") + ").";
      }
   }

   static String setView(Store<EntityStore> store, Ref<EntityStore> player, SeatCamera.View view) {
      VehicleRiderComponent rider = valid(store, player) ? store.getComponent(player, riderType) : null;
      if (rider == null || rider.releasing) {
         return "Вы не в машине.";
      }

      VehicleRuntimeComponent runtime = valid(store, rider.vehicle) ? store.getComponent(rider.vehicle, vehicleType) : null;
      if (runtime == null) {
         return "Машина недоступна.";
      }

      rider.view = view;
      sendCamera(rider, runtime, rider.seat, true, System.nanoTime());
      return "Вид: " + view.id + ".";
   }

   // ---------------------------------------------------------------- высадка

   /**
    * Высаживает игрока. teleportOut — поставить рядом с дверью (при смерти и выходе с
    * сервера не нужно). Вызывать вне обхода систем (из команды или CommandBuffer.run).
    */
   static boolean release(Store<EntityStore> store, Ref<EntityStore> player, String reason, boolean teleportOut) {
      return release(store, player, reason, teleportOut, null);
   }

   static boolean release(Store<EntityStore> store, Ref<EntityStore> player, String reason, boolean teleportOut, Vector3d exitOverride) {
      if (!valid(store, player)) {
         return false;
      }

      VehicleRiderComponent rider = store.getComponent(player, riderType);
      if (rider == null) {
         return false;
      }

      rider.releasing = true;
      PlayerRef playerRef = store.getComponent(player, PlayerRef.getComponentType());
      forget(playerRef != null ? playerRef.getUuid() : null, rider);
      Vector3d exit = exitOverride;
      float exitYaw = Float.NaN;
      VehicleRuntimeComponent runtime = valid(store, rider.vehicle) ? store.getComponent(rider.vehicle, vehicleType) : null;
      if (runtime != null) {
         boolean wasDriver = rider.driver(runtime);
         runtime.clearOccupant(rider.seat, player);
         if (wasDriver) {
            neutral(runtime, "seat-exit");
         }

         TransformComponent vehicleTransform = store.getComponent(rider.vehicle, TransformComponent.getComponentType());
         if (teleportOut && exit == null && vehicleTransform != null) {
            exit = exitPosition(store, runtime, vehicleTransform.getPosition(), rider.seat);
         }

         exitYaw = (float)runtime.yaw;
         if (runtime.debug != null) {
            runtime.debug.trace.event("seat-exit", reason);
         }
      }

      detachFromViewers(store, player, rider);
      removeAnchor(store, rider.anchor);
      store.tryRemoveComponent(player, riderType);
      restorePlayer(store, player, playerRef, rider);
      if (teleportOut && exit != null && store.getComponent(player, Teleport.getComponentType()) == null) {
         Rotation3f facing = Float.isFinite(exitYaw) ? new Rotation3f(0.0F, exitYaw, 0.0F) : new Rotation3f(0.0F, 0.0F, 0.0F);
         store.putComponent(player, Teleport.getComponentType(), Teleport.createForPlayer(exit, facing));
      }

      if (playerRef != null) {
         playerRef.sendMessage(Message.raw("Вы вышли из машины (" + reason + ")."));
      }

      return true;
   }

   /** Все седоки машины выходят, например машину удалили. */
   static void releaseAll(Store<EntityStore> store, VehicleRuntimeComponent runtime, String reason) {
      for (Ref<EntityStore> rider : runtime.riders()) {
         release(store, rider, reason, true);
      }
   }

   /** Машина удаляется из мира: седоков высаживаем там, где она стояла. */
   static void onVehicleRemoved(
      Ref<EntityStore> vehicle, VehicleRuntimeComponent runtime, Vector3dc lastPosition, Store<EntityStore> store, CommandBuffer<EntityStore> buffer
   ) {
      List<Ref<EntityStore>> riders = runtime.riders();
      for (int seat = 0; seat < runtime.seatCount(); seat++) {
         Ref<EntityStore> rider = runtime.occupant(seat);
         if (rider != null) {
            Vector3d exit = lastPosition == null ? null : exitPosition(store, runtime, lastPosition, seat);
            buffer.run(s -> release(s, rider, "машина удалена", exit != null, exit));
         }
      }

      if (!riders.isEmpty() && runtime.debug != null) {
         runtime.debug.trace.event("seat-exit", "vehicle-removed");
      }
   }

   /**
    * Игрок уходит из мира (выход с сервера, смена мира). Сущность игрока уже удаляется,
    * поэтому точку сиденья и место освобождаем отложенно.
    */
   static void onRiderRemoved(Ref<EntityStore> player, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
      VehicleRiderComponent rider = store.getComponent(player, riderType);
      if (rider != null) {
         rider.releasing = true;
         PlayerRef playerRef = store.getComponent(player, PlayerRef.getComponentType());
         forget(playerRef != null ? playerRef.getUuid() : null, rider);
         VehicleRuntimeComponent runtime = valid(store, rider.vehicle) ? store.getComponent(rider.vehicle, vehicleType) : null;
         TransformComponent vehicleTransform = runtime != null ? store.getComponent(rider.vehicle, TransformComponent.getComponentType()) : null;
         TransformComponent playerTransform = store.getComponent(player, TransformComponent.getComponentType());
         if (runtime != null && vehicleTransform != null && playerTransform != null) {
            playerTransform.getPosition().set(exitPosition(store, runtime, vehicleTransform.getPosition(), rider.seat));
         }

         MovementManager movement = store.getComponent(player, MovementManager.getComponentType());
         if (movement != null && rider.savedMovement != null && movement.getSettings() != null) {
            rider.savedMovement.restoreInto(movement.getSettings());
         }

         Ref<EntityStore> vehicle = rider.vehicle;
         Ref<EntityStore> anchor = rider.anchor;
         int seat = rider.seat;
         buffer.run(s -> {
            VehicleRuntimeComponent current = valid(s, vehicle) ? s.getComponent(vehicle, vehicleType) : null;
            if (current != null) {
               boolean wasDriver = seat == current.seatLayout.driverIndex() && current.occupant(seat) == player;
               current.clearOccupant(seat, player);
               if (wasDriver) {
                  neutral(current, "rider-left");
               }
            }

            removeAnchor(s, anchor);
         });
      }
   }

   /** Раскладка мест поменялась (редактор профиля): лишних седоков высаживаем. */
   static void onLayoutChanged(Store<EntityStore> store, Ref<EntityStore> vehicle, VehicleRuntimeComponent runtime) {
      for (Ref<EntityStore> dropped : runtime.fitOccupants()) {
         release(store, dropped, "место убрано из профиля", true);
      }
   }

   // ---------------------------------------------------------------- тик

   /**
    * Ввод водителя в газ, руль и тормоз. Возвращает false, если водительское место
    * свободно — тогда машиной управляет /vehicle drive или она стоит.
    */
   static boolean applyDriverInput(Store<EntityStore> store, VehicleRuntimeComponent runtime, long now) {
      Ref<EntityStore> player = runtime.occupant(runtime.seatLayout.driverIndex());
      if (player == null || !valid(store, player)) {
         return false;
      }

      VehicleRiderComponent rider = store.getComponent(player, riderType);
      if (rider == null || rider.releasing || rider.input == null) {
         return false;
      }

      runtime.driven = true;
      runtime.driver = null;
      SeatInput input = rider.input;
      runtime.inputEvents = input.packets;
      runtime.positionEvents = input.positions;
      runtime.wishEvents = input.wishes;
      runtime.inputAge = Math.min(99.0, input.ageSeconds(now));
      if (!input.ready(now)) {
         neutral(runtime, "seat-wait");
         return true;
      }

      SeatInputDecoder.Control control = input.control(now);
      runtime.throttle = control.handbrake ? 0.0 : control.throttle;
      runtime.steer = control.steer;
      if (control.handbrake) {
         runtime.brake = 1.0;
      } else {
         runtime.brake = Math.abs(runtime.throttle) < 0.01 ? config.controls.idleBrake : 0.0;
      }

      runtime.inputSource = "seat-" + control.source + (control.handbrake ? "+handbrake" : "");
      return true;
   }

   /** До обработки ввода игроков: высадка, вид, возврат куклы, чистка очереди движения. */
   static void tickRider(Ref<EntityStore> player, VehicleRiderComponent rider, Store<EntityStore> store, CommandBuffer<EntityStore> buffer, long now) {
      if (rider.releasing) {
         return;
      }

      PlayerInput playerInput = store.getComponent(player, PlayerInput.getComponentType());
      if (playerInput != null) {
         playerInput.getMovementUpdateQueue().removeIf(VehicleSeats::isMovementUpdate);
      }

      VehicleRuntimeComponent runtime = valid(store, rider.vehicle) ? store.getComponent(rider.vehicle, vehicleType) : null;
      if (runtime == null || runtime.occupant(rider.seat) != player || rider.input == null) {
         rider.releasing = true;
         boolean teleportOut = runtime != null;
         buffer.run(s -> release(s, player, "место потеряно", teleportOut));
         return;
      }

      SeatConfig settings = config;
      SeatInput input = rider.input;
      boolean settled = now - rider.enteredAt > (long)(settings.controls.enterGraceSeconds * 1.0E9);
      if (!settled) {
         input.clearHolds();
      }

      String exit = input.pollExit();
      if (exit == null && settled && input.control(now).descendSeconds >= settings.controls.exitHoldSeconds) {
         exit = "зажато «присесть»";
      }

      if (exit != null) {
         rider.releasing = true;
         String reason = exit;
         buffer.run(s -> release(s, player, reason, true));
         return;
      }

      int toggles = input.pollViewToggles();
      if (toggles > 0 && settings.controls.middleClickCyclesView) {
         for (int i = 0; i < toggles; i++) {
            rider.view = rider.view.next();
         }

         sendCamera(rider, runtime, rider.seat, true, now);
         if (rider.playerRef != null) {
            rider.playerRef.sendMessage(Message.raw("Вид: " + rider.view.id));
         }
      }

      keepPuppetFlying(store, player, rider);
      TransformComponent vehicleTransform = store.getComponent(rider.vehicle, TransformComponent.getComponentType());
      if (vehicleTransform != null) {
         recenterPuppet(store, player, rider, vehicleTransform.getPosition(), now);
      }
   }

   /** После физики: точки сидений и седоки встают на свои места. */
   static void follow(Store<EntityStore> store, Ref<EntityStore> vehicle, VehicleRuntimeComponent runtime, Vector3dc origin, long now) {
      SeatConfig settings = config;

      for (int seat = 0; seat < runtime.seatCount(); seat++) {
         Ref<EntityStore> player = runtime.occupant(seat);
         if (player == null) {
            continue;
         }

         VehicleRiderComponent rider = valid(store, player) ? store.getComponent(player, riderType) : null;
         if (rider == null || !vehicle.equals(rider.vehicle) || rider.seat != seat) {
            runtime.setOccupant(seat, null);
            continue;
         }

         if (rider.releasing) {
            continue;
         }

         Vector3d position = seatPosition(runtime, origin, seat);
         Rotation3f rotation = seatRotation(runtime, seat);
         if (valid(store, rider.anchor)) {
            TransformComponent anchorTransform = store.getComponent(rider.anchor, TransformComponent.getComponentType());
            if (anchorTransform != null) {
               anchorTransform.setPosition(position);
               anchorTransform.getRotation().set(rotation);
            }

            HeadRotation anchorHead = store.getComponent(rider.anchor, HeadRotation.getComponentType());
            if (anchorHead != null) {
               anchorHead.setRotation(rotation);
            }
         }

         Player playerComponent = store.getComponent(player, Player.getComponentType());
         TransformComponent playerTransform = store.getComponent(player, TransformComponent.getComponentType());
         if (playerComponent != null && playerTransform != null) {
            playerComponent.moveTo(player, position.x, position.y, position.z, store);
            playerTransform.getRotation().set(rotation);
         }

         HeadRotation playerHead = store.getComponent(player, HeadRotation.getComponentType());
         if (playerHead != null) {
            playerHead.setRotation(rotation);
         }

         MovementStatesComponent states = store.getComponent(player, MovementStatesComponent.getComponentType());
         if (states != null && !isSeated(states.getMovementStates(), settings)) {
            states.setMovementStates(seatedStates(settings));
         }

         sendCamera(rider, runtime, seat, false, now);
      }
   }

   /** В группе отправки обновлений: остальным игрокам — «седок пристёгнут к точке сиденья». */
   static void queueAttachment(Ref<EntityStore> player, VehicleRiderComponent rider, Visible visible) {
      boolean attach = config.riders.attachForOthers && !rider.releasing && rider.anchor != null && rider.anchor.isValid();
      if (!attach) {
         if (!rider.attachedViewers.isEmpty()) {
            for (Entry<Ref<EntityStore>, EntityViewer> entry : visible.visibleTo.entrySet()) {
               if (rider.attachedViewers.contains(entry.getKey())) {
                  entry.getValue().queueRemove(player, ComponentUpdateType.Mounted);
               }
            }

            rider.attachedViewers.clear();
         }
      } else {
         if (rider.attachDirty) {
            rider.attachedViewers.clear();
            rider.attachDirty = false;
         }

         rider.attachedViewers.retainAll(visible.visibleTo.keySet());
         MountedUpdate update = null;

         for (Entry<Ref<EntityStore>, EntityViewer> entry : visible.visibleTo.entrySet()) {
            Ref<EntityStore> viewer = entry.getKey();
            if (player.equals(viewer)) {
               continue;
            }

            boolean fresh = visible.newlyVisibleTo.containsKey(viewer);
            if (rider.attachedViewers.contains(viewer) && !fresh) {
               continue;
            }

            EntityViewer entityViewer = entry.getValue();
            if (!entityViewer.sent.containsKey(rider.anchor)) {
               rider.attachedViewers.remove(viewer);
               continue;
            }

            if (update == null) {
               update = new MountedUpdate(rider.anchorNetworkId, new Vector3f(0.0F, 0.0F, 0.0F), MountController.Minecart, null);
            }

            entityViewer.queueUpdate(player, update);
            rider.attachedViewers.add(viewer);
         }
      }
   }

   // ---------------------------------------------------------------- кукла и камера

   private static void startPuppet(Store<EntityStore> store, Ref<EntityStore> player, PlayerRef playerRef, VehicleRiderComponent rider, Vector3dc origin, long now) {
      MovementManager movement = store.getComponent(player, MovementManager.getComponentType());
      if (movement != null && movement.getSettings() != null) {
         rider.savedMovement = new VehicleRiderComponent.SavedMovement(movement.getSettings());
         applyPuppetMovement(movement.getSettings());
         movement.update(playerRef.getPacketHandler());
      }

      Vector3d target = puppetTarget(origin);
      TeleportSystems.queueAndSendClientTeleport(playerRef, target, new Rotation3f(KEEP_ROTATION), new Rotation3f(KEEP_ROTATION), true);
      rider.puppetTarget.set(target);
      rider.lastRecenterAt = now;
      rider.recenters++;
   }

   private static void applyPuppetMovement(MovementSettings settings) {
      settings.fly = FlyMode.Forced;
      settings.horizontalFlySpeed = (float)config.puppet.flySpeed;
      settings.verticalFlySpeed = (float)config.puppet.verticalFlySpeed;
   }

   /** Если что-то вернуло игроку обычные настройки движения (смена режима игры), снова включаем полёт куклы. */
   private static void keepPuppetFlying(Store<EntityStore> store, Ref<EntityStore> player, VehicleRiderComponent rider) {
      MovementManager movement = store.getComponent(player, MovementManager.getComponentType());
      if (movement != null && rider.playerRef != null) {
         MovementSettings settings = movement.getSettings();
         if (settings != null
            && (settings.fly != FlyMode.Forced || Math.abs(settings.horizontalFlySpeed - (float)config.puppet.flySpeed) > 1.0E-4F)) {
            if (rider.savedMovement == null) {
               rider.savedMovement = new VehicleRiderComponent.SavedMovement(settings);
            }

            applyPuppetMovement(settings);
            movement.update(rider.playerRef.getPacketHandler());
         }
      }
   }

   private static void recenterPuppet(Store<EntityStore> store, Ref<EntityStore> player, VehicleRiderComponent rider, Vector3dc origin, long now) {
      PlayerRef playerRef = rider.playerRef;
      SeatInput input = rider.input;
      if (playerRef == null || input == null || now - rider.lastRecenterAt < RECENTER_COOLDOWN_NANOS) {
         return;
      }

      if (!playerRef.getTeleportAckTracker().isEmpty() || store.getComponent(player, Teleport.getComponentType()) != null) {
         return;
      }

      Vector3d target = puppetTarget(origin);
      boolean resend;
      if (!input.hasPuppetPosition()) {
         resend = input.ready(now) && now - rider.lastRecenterAt > LOST_PUPPET_RETRY_NANOS;
      } else {
         double dx = input.puppetX() - target.x;
         double dy = input.puppetY() - target.y;
         double dz = input.puppetZ() - target.z;
         resend = Math.hypot(dx, dz) > config.puppet.recenterDistance || Math.abs(dy) > config.puppet.recenterHeight;
      }

      if (resend) {
         TeleportSystems.queueAndSendClientTeleport(playerRef, target, new Rotation3f(KEEP_ROTATION), new Rotation3f(KEEP_ROTATION), false);
         rider.puppetTarget.set(target);
         rider.lastRecenterAt = now;
         rider.recenters++;
      }
   }

   private static Vector3d puppetTarget(Vector3dc origin) {
      SeatConfig.Puppet puppet = config.puppet;
      double y = puppet.relativeHeight > 0.0 ? origin.y() + puppet.relativeHeight : puppet.altitude;
      return new Vector3d(origin.x(), y, origin.z());
   }

   private static void sendCamera(VehicleRiderComponent rider, VehicleRuntimeComponent runtime, int seat, boolean force, long now) {
      if (rider.playerRef == null) {
         return;
      }

      SeatCamera.View view = rider.view;
      if (!force && (!view.followsVehicle() || now - rider.cameraSentAt < CAMERA_INTERVAL_NANOS)) {
         return;
      }

      float yaw;
      float pitch;
      float roll;
      if (view == SeatCamera.View.FIRST) {
         Rotation3f rotation = seatRotation(runtime, seat);
         yaw = rotation.yaw();
         pitch = rotation.pitch();
         roll = rotation.roll();
      } else {
         yaw = (float)runtime.yaw;
         pitch = 0.0F;
         roll = 0.0F;
      }

      if (!force
         && angleDelta(yaw, rider.cameraYaw) < CAMERA_EPSILON
         && angleDelta(pitch, rider.cameraPitch) < CAMERA_EPSILON
         && angleDelta(roll, rider.cameraRoll) < CAMERA_EPSILON) {
         return;
      }

      rider.playerRef
         .getPacketHandler()
         .writeNoCache(SeatCamera.packet(view, config.camera, rider.vehicleNetworkId, rider.anchorNetworkId, yaw, pitch, roll));
      rider.cameraYaw = yaw;
      rider.cameraPitch = pitch;
      rider.cameraRoll = roll;
      rider.cameraSentAt = now;
      rider.cameraPackets++;
   }

   private static float angleDelta(float a, float b) {
      if (!Float.isFinite(b)) {
         return Float.POSITIVE_INFINITY;
      }

      return (float)Math.abs(SeatMath.wrap((double)a - (double)b));
   }

   // ---------------------------------------------------------------- геометрия

   static Vector3d seatPosition(VehicleRuntimeComponent runtime, Vector3dc origin, int seat) {
      SeatLayout.Seat info = runtime.seatLayout.get(seat);
      return SeatMath.toWorld(origin, runtime.yaw, runtime.pitch, runtime.roll, info.x, info.y + config.riders.offsetY, info.z);
   }

   /** Поворот места в мире: поворот машины (рыскание, тангаж, крен) и поворот самого кресла. */
   static Rotation3f seatRotation(VehicleRuntimeComponent runtime, int seat) {
      SeatLayout.Seat info = runtime.seatLayout.get(seat);
      Quaterniond rotation = SeatMath.rotation(runtime.yaw, runtime.pitch, runtime.roll);
      if (info.yaw != 0.0) {
         rotation.rotateY(info.yaw);
      }

      Vector3d euler = rotation.getEulerAnglesYXZ(new Vector3d());
      return new Rotation3f((float)euler.x, (float)euler.y, (float)euler.z);
   }

   /** Где поставить вышедшего: сбоку от места, иначе с другой стороны, сзади, спереди, на крышу. */
   static Vector3d exitPosition(Store<EntityStore> store, VehicleRuntimeComponent runtime, Vector3dc origin, int seat) {
      SeatLayout.Seat info = runtime.seatLayout.get(Math.max(0, Math.min(seat, runtime.seatCount() - 1)));
      int side = info.x < 0.0 ? -1 : 1;
      double sideOffset = Math.abs(runtime.bodyX) + runtime.halfX + 0.7;
      double lengthOffset = Math.abs(runtime.bodyZ) + runtime.halfZ + 0.9;
      double[][] candidates = new double[][]{
         {side * sideOffset, info.z}, {-side * sideOffset, info.z}, {0.0, -lengthOffset}, {0.0, lengthOffset}
      };
      VehicleTerrain terrain = VehicleTerrain.world(store);

      for (double[] candidate : candidates) {
         for (double lift : new double[]{0.1, 1.1}) {
            Vector3d position = SeatMath.toWorld(origin, runtime.yaw, 0.0, 0.0, candidate[0], 0.0, candidate[1]);
            position.y = origin.y() + lift;
            VehicleTerrain.Hit hit = terrain.sweep(PLAYER_BOX, position, new Vector3d());
            if (hit.loaded() && !hit.blocked()) {
               return position;
            }
         }
      }

      return new Vector3d(origin.x(), origin.y() + runtime.bodyY + runtime.halfY + 0.3, origin.z());
   }

   // ---------------------------------------------------------------- служебное

   static boolean valid(Store<EntityStore> store, Ref<EntityStore> ref) {
      return ref != null && ref.isValid() && ref.getStore() == store;
   }

   static Ref<EntityStore> nearestVehicle(Store<EntityStore> store, Ref<EntityStore> player, double radius) {
      TransformComponent transform = store.getComponent(player, TransformComponent.getComponentType());
      if (transform == null) {
         return null;
      }

      Vector3d position = new Vector3d(transform.getPosition());
      Ref<?>[] best = new Ref<?>[1];
      double[] bestDistance = new double[]{radius};
      store.forEachChunk(vehicleType, (chunk, buffer) -> {
         for (int i = 0; i < chunk.size(); i++) {
            TransformComponent vehicleTransform = chunk.getComponent(i, TransformComponent.getComponentType());
            if (vehicleTransform != null) {
               double distance = vehicleTransform.getPosition().distance(position);
               if (distance <= bestDistance[0]) {
                  bestDistance[0] = distance;
                  best[0] = chunk.getReferenceTo(i);
               }
            }
         }
      });
      @SuppressWarnings("unchecked")
      Ref<EntityStore> vehicle = (Ref<EntityStore>)best[0];
      return vehicle;
   }

   /** Машина, в которой сидит игрок, или null. */
   static Ref<EntityStore> vehicleOf(Store<EntityStore> store, Ref<EntityStore> player) {
      VehicleRiderComponent rider = valid(store, player) ? store.getComponent(player, riderType) : null;
      return rider != null && valid(store, rider.vehicle) ? rider.vehicle : null;
   }

   static String describeSeats(Store<EntityStore> store, VehicleRuntimeComponent runtime) {
      StringBuilder text = new StringBuilder("Мест: " + runtime.seatCount() + ".");

      for (int seat = 0; seat < runtime.seatCount(); seat++) {
         SeatLayout.Seat info = runtime.seatLayout.get(seat);
         Ref<EntityStore> occupant = runtime.occupant(seat);
         PlayerRef playerRef = valid(store, occupant) ? store.getComponent(occupant, PlayerRef.getComponentType()) : null;
         text.append(String.format(Locale.ROOT, " %d) %s%s [%.2f %.2f %.2f] — %s;", seat + 1, info.id, info.driver ? " (водитель)" : "", info.x, info.y, info.z,
            playerRef != null ? playerRef.getUsername() : "свободно"));
      }

      return text.toString();
   }

   static String describeRider(Store<EntityStore> store, Ref<EntityStore> player, long now) {
      VehicleRiderComponent rider = valid(store, player) ? store.getComponent(player, riderType) : null;
      if (rider == null || rider.input == null) {
         return null;
      }

      PlayerRef playerRef = rider.playerRef;
      return String.format(
            Locale.ROOT,
            "место=%d вид=%s возвратов куклы=%d камера(пакетов)=%d зрителей с креплением=%d телепорт ждёт=%s | ",
            rider.seat + 1,
            rider.view.id,
            rider.recenters,
            rider.cameraPackets,
            rider.attachedViewers.size(),
            playerRef == null ? "?" : !playerRef.getTeleportAckTracker().isEmpty()
         )
         + rider.input.describe(now);
   }

   static void attachDebug(VehicleRuntimeComponent runtime, Store<EntityStore> store) {
      for (Ref<EntityStore> player : runtime.riders()) {
         VehicleRiderComponent rider = valid(store, player) ? store.getComponent(player, riderType) : null;
         if (rider != null && rider.input != null) {
            rider.input.debug(runtime.debug);
         }
      }
   }

   /** Плагин выгружается: вернуть камеру и движение всем, кто сидит. */
   static void shutdown() {
      for (VehicleRiderComponent rider : RIDERS.values()) {
         try {
            if (rider.input != null) {
               rider.input.release();
            }

            if (rider.playerRef != null) {
               if (rider.savedMovement != null) {
                  rider.savedMovement.restoreInto(rider.savedMovement.settings);
                  rider.playerRef.getPacketHandler().writeNoCache(new UpdateMovementSettings(new MovementSettings(rider.savedMovement.settings)));
               }

               rider.playerRef.getPacketHandler().writeNoCache(SeatCamera.reset());
            }
         } catch (RuntimeException ignored) {
         }
      }

      RIDERS.clear();
      INPUTS.clear();
   }

   private static void forget(UUID uuid, VehicleRiderComponent rider) {
      if (rider.input != null) {
         rider.input.release();
      }

      UUID key = uuid != null ? uuid : rider.input != null ? rider.input.player() : null;
      if (key != null) {
         INPUTS.remove(key, rider.input);
         RIDERS.remove(key, rider);
      }
   }

   private static void pruneOccupants(Store<EntityStore> store, VehicleRuntimeComponent runtime) {
      for (int seat = 0; seat < runtime.seatCount(); seat++) {
         Ref<EntityStore> occupant = runtime.occupant(seat);
         if (occupant != null && (!valid(store, occupant) || store.getComponent(occupant, riderType) == null)) {
            runtime.setOccupant(seat, null);
         }
      }
   }

   private static void neutral(VehicleRuntimeComponent runtime, String source) {
      runtime.throttle = 0.0;
      runtime.steer = 0.0;
      runtime.brake = 1.0;
      runtime.inputSource = source;
   }

   private static Ref<EntityStore> spawnAnchor(Store<EntityStore> store, Vector3d position, Rotation3f rotation) {
      String modelName = config.riders.anchorModel;
      ModelAsset asset = ModelAsset.getAssetMap().getAsset(modelName);
      if (asset == null) {
         throw new IllegalStateException("не загружена модель " + modelName + " (нужен пак BeepBeep_Vehicle_Pack)");
      }

      Model model = Model.createScaledModel(asset, (float)config.riders.anchorModelScale, Map.of(), ANCHOR_BOX);
      Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
      holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
      holder.addComponent(UUIDComponent.getComponentType(), UUIDComponent.randomUUID());
      holder.addComponent(NetworkId.getComponentType(), new NetworkId(store.getExternalData().takeNextNetworkId()));
      holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(position, rotation));
      holder.addComponent(HeadRotation.getComponentType(), new HeadRotation(rotation));
      holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
      holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(new Box(ANCHOR_BOX)));
      holder.addComponent(Invulnerable.getComponentType(), Invulnerable.INSTANCE);
      holder.addComponent(Intangible.getComponentType(), Intangible.INSTANCE);
      Ref<EntityStore> anchor = store.addEntity(holder, AddReason.SPAWN);
      if (anchor == null || !anchor.isValid()) {
         throw new IllegalStateException("чанк под машиной не загружен");
      }

      return anchor;
   }

   private static void removeAnchor(Store<EntityStore> store, Ref<EntityStore> anchor) {
      if (valid(store, anchor)) {
         store.removeEntity(anchor, RemoveReason.REMOVE);
      }
   }

   private static void detachFromViewers(Store<EntityStore> store, Ref<EntityStore> player, VehicleRiderComponent rider) {
      Visible visible = store.getComponent(player, Visible.getComponentType());
      if (visible != null && !rider.attachedViewers.isEmpty()) {
         for (Entry<Ref<EntityStore>, EntityViewer> entry : visible.visibleTo.entrySet()) {
            if (rider.attachedViewers.contains(entry.getKey())) {
               entry.getValue().queueRemove(player, ComponentUpdateType.Mounted);
            }
         }
      }

      rider.attachedViewers.clear();
   }

   private static void restorePlayer(Store<EntityStore> store, Ref<EntityStore> player, PlayerRef playerRef, VehicleRiderComponent rider) {
      MovementManager movement = store.getComponent(player, MovementManager.getComponentType());
      if (movement != null && rider.savedMovement != null) {
         MovementSettings current = movement.getSettings();
         if (current != null) {
            rider.savedMovement.restoreInto(current);
         }

         if (playerRef != null) {
            movement.update(playerRef.getPacketHandler());
         }
      }

      if (playerRef != null) {
         CameraManager camera = store.getComponent(player, CameraManager.getComponentType());
         if (camera != null) {
            camera.resetCamera(playerRef);
         } else {
            playerRef.getPacketHandler().writeNoCache(SeatCamera.reset());
         }
      }

      MovementStatesComponent states = store.getComponent(player, MovementStatesComponent.getComponentType());
      if (states != null) {
         MovementStates standing = new MovementStates();
         standing.idle = true;
         standing.horizontalIdle = true;
         standing.onGround = true;
         states.setMovementStates(standing);
      }

      PlayerInput playerInput = store.getComponent(player, PlayerInput.getComponentType());
      if (playerInput != null) {
         playerInput.getMovementUpdateQueue().clear();
      }
   }

   private static MovementStates seatedStates(SeatConfig settings) {
      MovementStates states = new MovementStates();
      states.idle = true;
      states.horizontalIdle = true;
      states.onGround = true;
      if ("mounting".equalsIgnoreCase(settings.riders.pose)) {
         states.mounting = true;
      } else {
         states.sitting = true;
      }

      return states;
   }

   private static boolean isSeated(MovementStates states, SeatConfig settings) {
      if (states == null || states.flying || states.falling || states.jumping) {
         return false;
      }

      return "mounting".equalsIgnoreCase(settings.riders.pose) ? states.mounting : states.sitting;
   }

   private static boolean isMovementUpdate(PlayerInput.InputUpdate update) {
      return update instanceof PlayerInput.AbsoluteMovement
         || update instanceof PlayerInput.RelativeMovement
         || update instanceof PlayerInput.WishMovement
         || update instanceof PlayerInput.SetBody
         || update instanceof PlayerInput.SetHead
         || update instanceof PlayerInput.SetClientVelocity
         || update instanceof PlayerInput.SetMovementStates
         || update instanceof PlayerInput.SetRiderMovementStates;
   }
}
