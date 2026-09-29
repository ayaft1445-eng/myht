package beepbeep.hytale;

import com.hypixel.hytale.math.shape.Box;
import org.joml.Vector3d;

public final class VehicleBody {
   private VehicleBody() {
   }

   public static VehicleShape shape(VehicleRuntimeComponent var0, double var1, double var3, double var5) {
      return new VehicleShape(
         new Box(
            var0.bodyX - var0.halfX,
            var0.bodyY - var0.halfY,
            var0.bodyZ - var0.halfZ,
            var0.bodyX + var0.halfX,
            var0.bodyY + var0.halfY,
            var0.bodyZ + var0.halfZ
         ),
         var1,
         var3,
         var5
      );
   }

   public static Vector3d recover(VehicleTerrain var0, VehicleShape var1, Vector3d var2) {
      Vector3d var3 = new Vector3d(var2);
      double var4 = 0.3;

      for (int var6 = 0; var6 < 4; var6++) {
         VehicleTerrain.Hit var7 = var0.sweep(var1, var3, new Vector3d());
         if (!var7.loaded() || !var7.blocked() || var7.depth() < 1.0E-7) {
            break;
         }

         double var8 = Math.min(var4, var7.depth() + 0.003);
         if (var8 <= 0.0) {
            break;
         }

         Vector3d var10 = new Vector3d(var7.normal()).mul(var8);
         VehicleTerrain.Hit var11 = var0.sweep(var1, var3, var10);
         if (!var11.loaded() || var11.blocked()) {
            break;
         }

         var3.add(var10);
         var4 -= var8;
      }

      return var3;
   }

   public static VehicleMotion.Move horizontal(VehicleTerrain var0, VehicleShape var1, Vector3d var2, Vector3d var3, boolean var4) {
      if (var3.lengthSquared() < 1.0E-14) {
         return new VehicleMotion.Move(new Vector3d(var2), false, true);
      } else {
         VehicleTerrain.Hit var5 = var0.sweep(var1, var2, var3);
         if (!var5.loaded()) {
            return new VehicleMotion.Move(new Vector3d(var2), true, false);
         } else if (!var5.blocked()) {
            return new VehicleMotion.Move(new Vector3d(var2).add(var3), false, true);
         } else {
            if (var4) {
               Vector3d var6 = new Vector3d(0.0, 1.02, 0.0);
               VehicleTerrain.Hit var7 = var0.sweep(var1, var2, var6);
               if (!var7.loaded()) {
                  return new VehicleMotion.Move(new Vector3d(var2), true, false);
               }

               if (!var7.blocked()) {
                  Vector3d var8 = new Vector3d(var2).add(var6);
                  VehicleTerrain.Hit var9 = var0.sweep(var1, var8, var3);
                  if (!var9.loaded()) {
                     return new VehicleMotion.Move(new Vector3d(var2), true, false);
                  }

                  if (!var9.blocked()) {
                     var8.add(var3);
                     Vector3d var10 = new Vector3d(var6).negate();
                     VehicleTerrain.Hit var11 = var0.sweep(var1, var8, var10);
                     if (!var11.loaded()) {
                        return new VehicleMotion.Move(new Vector3d(var2), true, false);
                     }

                     if (var11.blocked() && var11.normal().y > 0.5 && var11.fraction() > 0.0) {
                        var8.fma(Math.max(0.0, var11.fraction() - 0.003 / var6.y), var10);
                        return new VehicleMotion.Move(var8, false, true);
                     }
                  }
               }
            }

            Vector3d var13 = new Vector3d(var2);
            Vector3d var14 = new Vector3d(var3);

            for (int var15 = 0; var15 < 3; var15++) {
               if (var15 > 0) {
                  var5 = var0.sweep(var1, var13, var14);
               }

               if (!var5.loaded()) {
                  return new VehicleMotion.Move(new Vector3d(var2), true, false);
               }

               if (!var5.blocked()) {
                  var13.add(var14);
                  break;
               }

               double var16 = Math.max(0.0, var5.fraction() - 0.003 / Math.max(1.0E-9, var14.length()));
               var13.fma(var16, var14);
               var14.mul(1.0 - var16);
               double var17 = var14.dot(var5.normal());
               if (var17 >= -1.0E-10) {
                  break;
               }

               var14.fma(-var17, var5.normal());
               if (var14.lengthSquared() < 1.0E-12) {
                  break;
               }
            }

            return new VehicleMotion.Move(var13, true, true);
         }
      }
   }

   public static boolean clear(VehicleTerrain var0, VehicleShape var1, Vector3d var2) {
      VehicleTerrain.Hit var3 = var0.sweep(var1, var2, new Vector3d());
      return var3.loaded() && (!var3.blocked() || var3.depth() < 1.0E-6);
   }
}
