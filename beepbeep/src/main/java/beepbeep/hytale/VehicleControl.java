package beepbeep.hytale;

import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput.AbsoluteMovement;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput.InputUpdate;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput.RelativeMovement;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput.WishMovement;
import java.util.List;
import org.joml.Vector3d;

public final class VehicleControl {
   private VehicleControl() {
   }

   public static void reset(VehicleRuntimeComponent var0) {
      var0.driver = null;
      var0.driven = false;
      var0.seated = false;
      var0.throttle = 0.0;
      var0.steer = 0.0;
      var0.brake = 1.0;
      var0.inputAge = 1.0;
      var0.inputSource = "off";
   }

   public static void update(VehicleRuntimeComponent var0, List<InputUpdate> var1, Vector3d var2, Vector3d var3, double var4) {
      var0.inputAge += var4;
      double var6 = var2.x;
      double var8 = var2.y;
      double var10 = var2.z;
      boolean var12 = false;
      boolean var13 = false;

      for (InputUpdate var15 : var1) {
         var0.inputEvents++;
         if (var15 instanceof WishMovement) {
            var0.wishEvents++;
         }

         if (var15 instanceof RelativeMovement var16) {
            var6 += var16.getX();
            var8 += var16.getY();
            var10 += var16.getZ();
            var12 = true;
         } else if (var15 instanceof AbsoluteMovement var17) {
            var6 = var17.getX();
            var8 = var17.getY();
            var10 = var17.getZ();
            var12 = true;
         }

         if (!Double.isFinite(var6) || !Double.isFinite(var8) || !Double.isFinite(var10)) {
            var13 = true;
         }
      }

      if (var12) {
         var0.positionEvents++;
         double var32 = var6 - var2.x;
         double var33 = var8 - var2.y;
         double var18 = var10 - var2.z;
         double var20 = Math.hypot(var32, var18);
         double var22 = Math.hypot(var3.x, var3.z);
         if (!var13 && !(var20 > 2.0) && !(Math.abs(var33) > 3.0) && Double.isFinite(var22) && !(var22 < 0.01)) {
            double var24 = var3.x / var22;
            double var26 = var3.z / var22;
            double var28 = var32 * var24 + var18 * var26;
            double var30 = -var32 * var26 + var18 * var24;
            if (var20 < 0.002) {
               var0.throttle = 0.0;
               var0.steer = 0.0;
               var0.brake = 1.0;
            } else {
               var0.throttle = Math.abs(var28) < 0.002 ? 0.0 : Math.max(-1.0, Math.min(1.0, var28 / var20));
               var0.steer = Math.abs(var30) < 0.002 ? 0.0 : Math.max(-1.0, Math.min(1.0, var30 / var20));
               var0.brake = 0.0;
            }

            var0.inputSource = "walking";
         } else {
            var0.throttle = 0.0;
            var0.steer = 0.0;
            var0.brake = 1.0;
            var0.inputSource = "rejected-movement";
         }

         var0.inputAge = 0.0;
      }

      if (var0.inputAge >= 0.15) {
         var0.throttle = 0.0;
         var0.steer = 0.0;
         var0.brake = 1.0;
         var0.inputSource = "idle";
      }
   }
}
