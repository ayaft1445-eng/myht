package beepbeep.hytale;

import com.hypixel.hytale.protocol.ApplyLookType;
import com.hypixel.hytale.protocol.ApplyMovementType;
import com.hypixel.hytale.protocol.AttachedToType;
import com.hypixel.hytale.protocol.CanMoveType;
import com.hypixel.hytale.protocol.ClientCameraView;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.MouseInputTargetType;
import com.hypixel.hytale.protocol.MovementForceRotationType;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.PositionDistanceOffsetType;
import com.hypixel.hytale.protocol.PositionType;
import com.hypixel.hytale.protocol.RotationType;
import com.hypixel.hytale.protocol.ServerCameraSettings;
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera;
import java.util.Locale;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Камера седока. Персонаж игрока в это время висит далеко над машиной (см. SeatInputDecoder),
 * поэтому камера всегда привязана к машине или к точке сиденья, а не к персонажу.
 *
 * Во всех видах W/A/S/D двигают персонажа по мировым осям (movementForceRotation = 0),
 * чтобы по его сдвигу было однозначно видно, какие клавиши зажаты.
 */
final class SeatCamera {
   enum View {
      /** От первого лица с места, смотрит вперёд машины. */
      FIRST("first"),
      /** Сзади машины, мышь крутит камеру вокруг неё. */
      THIRD("third"),
      /** Сзади машины, камера сама поворачивается за машиной. */
      CHASE("chase");

      final String id;

      View(String id) {
         this.id = id;
      }

      View next() {
         return values()[(this.ordinal() + 1) % values().length];
      }

      /** Нужно ли досылать поворот камеры, когда машина поворачивает. */
      boolean followsVehicle() {
         return this != THIRD;
      }

      static View parse(String value, View fallback) {
         if (value != null) {
            String normalized = value.trim().toLowerCase(Locale.ROOT);

            for (View view : values()) {
               if (view.id.equals(normalized)) {
                  return view;
               }
            }
         }

         return fallback;
      }
   }

   private SeatCamera() {
   }

   /**
    * Настройки камеры для вида.
    *
    * @param yaw поворот места в мире, радианы
    * @param pitch наклон машины (нос вверх — плюс), радианы
    * @param roll крен машины, радианы
    */
   static SetServerCamera packet(View view, SeatConfig.Camera config, int vehicleNetworkId, int anchorNetworkId, double yaw, double pitch, double roll) {
      yaw += Math.toRadians(config.yawOffsetDegrees);
      ServerCameraSettings settings = new ServerCameraSettings();
      settings.attachedToType = AttachedToType.EntityId;
      settings.followAttachedEntity = true;
      settings.eyeOffset = false;
      settings.positionType = PositionType.AttachedToPlusOffset;
      settings.rotationType = RotationType.Custom;
      settings.positionLerpSpeed = (float)config.positionLerp;
      settings.speedModifier = 1.0F;
      settings.displayCursor = false;
      settings.displayReticle = false;
      settings.sendMouseMotion = false;
      settings.skipCharacterPhysics = false;
      settings.hideHeldItem = config.hideHeldItem;
      settings.mouseInputTargetType = config.disableTargeting ? MouseInputTargetType.None : MouseInputTargetType.Any;
      settings.canMoveType = CanMoveType.Always;
      settings.applyMovementType = ApplyMovementType.CharacterController;
      settings.movementMultiplier = new Vector3f(1.0F, 1.0F, 1.0F);
      settings.movementForceRotationType = MovementForceRotationType.Custom;
      settings.movementForceRotation = new Direction(0.0F, 0.0F, 0.0F);
      settings.applyLookType = ApplyLookType.Rotation;
      settings.lookMultiplier = new Vector2f(0.0F, 0.0F);
      settings.allowPitchControls = false;
      switch (view) {
         case FIRST:
            settings.attachedToEntityId = anchorNetworkId;
            settings.isFirstPerson = true;
            settings.positionDistanceOffsetType = PositionDistanceOffsetType.None;
            settings.distance = 0.0F;
            settings.positionOffset = new Position(0.0, config.eyeHeight, 0.0);
            settings.rotationLerpSpeed = (float)config.rotationLerp;
            settings.rotation = config.firstPersonTilt
               ? new Direction((float)yaw, (float)pitch, (float)roll)
               : new Direction((float)yaw, 0.0F, 0.0F);
            break;
         case THIRD:
            settings.attachedToEntityId = vehicleNetworkId;
            settings.isFirstPerson = false;
            settings.positionDistanceOffsetType = PositionDistanceOffsetType.DistanceOffsetRaycast;
            settings.distance = (float)config.orbitDistance;
            settings.positionOffset = new Position(0.0, config.orbitHeight, 0.0);
            settings.rotationLerpSpeed = (float)config.rotationLerp;
            settings.rotation = new Direction((float)yaw, (float)Math.toRadians(config.orbitPitchDegrees), 0.0F);
            if (config.orbitMouse) {
               settings.lookMultiplier = new Vector2f(1.0F, 1.0F);
               settings.allowPitchControls = true;
            }
            break;
         case CHASE:
         default:
            settings.attachedToEntityId = vehicleNetworkId;
            settings.isFirstPerson = false;
            settings.positionDistanceOffsetType = PositionDistanceOffsetType.DistanceOffsetRaycast;
            settings.distance = (float)config.chaseDistance;
            settings.positionOffset = new Position(0.0, config.chaseHeight, 0.0);
            settings.rotationLerpSpeed = (float)config.chaseRotationLerp;
            settings.rotation = new Direction((float)yaw, (float)Math.toRadians(config.chasePitchDegrees), 0.0F);
      }

      return new SetServerCamera(ClientCameraView.Custom, config.lockView, settings);
   }

   /** Обычная камера игрока, как у ванильного /camera reset. */
   static SetServerCamera reset() {
      return new SetServerCamera(ClientCameraView.Custom, false, null);
   }
}
