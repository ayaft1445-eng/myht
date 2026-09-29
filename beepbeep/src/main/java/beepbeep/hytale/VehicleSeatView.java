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
import com.hypixel.hytale.server.core.receiver.IPacketReceiver;
import org.joml.Vector2f;
import org.joml.Vector3f;

final class VehicleSeatView {
   static SetServerCamera camera(int var0, float var1) {
      return camera(var0, var1, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
   }

   static SetServerCamera camera(int var0, float var1, float var2, float var3) {
      return camera(var0, var1, var2, var3, 0.0F, 0.0F, 0.0F);
   }

   static SetServerCamera camera(int var0, float var1, float var2, float var3, float var4, float var5, float var6) {
      ServerCameraSettings var7 = new ServerCameraSettings();
      var7.attachedToType = AttachedToType.EntityId;
      var7.attachedToEntityId = var0;
      var7.followAttachedEntity = true;
      var7.eyeOffset = false;
      var7.positionDistanceOffsetType = PositionDistanceOffsetType.None;
      var7.positionOffset = new Position((double)var4, (double)var5 + 1.2, (double)var6);
      var7.positionType = PositionType.AttachedToPlusOffset;
      var7.position = new Position(0.0, 0.0, 0.0);
      var7.distance = 6.0F;
      var7.positionLerpSpeed = 1.0F;
      var7.rotationLerpSpeed = 1.0F;
      var7.speedModifier = 1.0F;
      var7.isFirstPerson = false;
      var7.rotationType = RotationType.Custom;
      var7.rotation = new Direction(var1, var2 - 0.15F, var3);
      var7.applyLookType = ApplyLookType.Rotation;
      var7.lookMultiplier = new Vector2f(0.0F, 0.0F);
      var7.allowPitchControls = false;
      var7.mouseInputTargetType = MouseInputTargetType.None;
      var7.sendMouseMotion = true;
      var7.skipCharacterPhysics = true;
      var7.displayCursor = false;
      var7.hideHeldItem = true;
      var7.canMoveType = CanMoveType.Always;
      var7.applyMovementType = ApplyMovementType.CharacterController;
      var7.movementMultiplier = new Vector3f(1.0F, 1.0F, 1.0F);
      var7.movementForceRotationType = MovementForceRotationType.CameraRotation;
      var7.movementForceRotation = null;
      return new SetServerCamera(ClientCameraView.Custom, false, var7);
   }

   static void reset(IPacketReceiver var0) {
      var0.writeNoCache(new SetServerCamera(ClientCameraView.Custom, false, null));
   }
}
