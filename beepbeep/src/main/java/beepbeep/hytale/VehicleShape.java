package beepbeep.hytale;

import com.hypixel.hytale.math.shape.Box;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class VehicleShape {
   public final Vector3d center;
   public final Vector3d half;
   public final Vector3d[] axes;

   public VehicleShape(Box var1, double var2, double var4, double var6) {
      Quaterniond var8 = new Quaterniond().rotationYXZ(var2, var4, var6);
      this.center = var8.transform(new Vector3d(var1.min).add(var1.max).mul(0.5));
      this.half = new Vector3d(var1.max).sub(var1.min).mul(0.5);
      this.axes = new Vector3d[]{
         var8.transform(new Vector3d(1.0, 0.0, 0.0)), var8.transform(new Vector3d(0.0, 1.0, 0.0)), var8.transform(new Vector3d(0.0, 0.0, 1.0))
      };
   }

   public Box bounds() {
      Vector3d var1 = new Vector3d();

      for (int var2 = 0; var2 < 3; var2++) {
         var1.add(new Vector3d(this.axes[var2]).absolute().mul(this.half.get(var2)));
      }

      return new Box(new Vector3d(this.center).sub(var1), new Vector3d(this.center).add(var1));
   }

   public VehicleTerrain.Hit against(Box var1, Vector3d var2, Vector3d var3) {
      Vector3d var4 = new Vector3d(var1.max).sub(var1.min).mul(0.5);
      Vector3d var5 = new Vector3d(var2).add(this.center).sub(new Vector3d(var1.min).add(var1.max).mul(0.5));
      Vector3d[] var6 = new Vector3d[]{new Vector3d(1.0, 0.0, 0.0), new Vector3d(0.0, 1.0, 0.0), new Vector3d(0.0, 0.0, 1.0)};
      Vector3d[] var7 = new Vector3d[15];
      int var8 = 0;

      for (Vector3d var12 : var6) {
         var7[var8++] = var12;
      }

      for (Vector3d var47 : this.axes) {
         var7[var8++] = var47;
      }

      for (Vector3d var48 : this.axes) {
         for (Vector3d var16 : var6) {
            var7[var8++] = new Vector3d(var48).cross(var16);
         }
      }

      double var41 = Double.NEGATIVE_INFINITY;
      double var46 = Double.POSITIVE_INFINITY;
      double var49 = Double.POSITIVE_INFINITY;
      Vector3d var50 = new Vector3d();
      Vector3d var51 = new Vector3d();
      boolean var17 = true;

      for (Vector3d var21 : var7) {
         if (!(var21.lengthSquared() < 1.0E-12)) {
            Vector3d var22 = new Vector3d(var21).normalize();
            double var23 = Math.abs(var22.x) * var4.x + Math.abs(var22.y) * var4.y + Math.abs(var22.z) * var4.z;

            for (int var25 = 0; var25 < 3; var25++) {
               var23 += Math.abs(var22.dot(this.axes[var25])) * this.half.get(var25);
            }

            double var53 = var22.dot(var5);
            double var27 = var22.dot(var3);
            double var29 = var23 - Math.abs(var53);
            if (var29 < 0.0) {
               var17 = false;
            }

            if (var29 < var49) {
               var49 = var29;
               var51.set(var22).mul(var53 < 0.0 ? -1.0 : 1.0);
            }

            if (Math.abs(var27) < 1.0E-12) {
               if (Math.abs(var53) > var23 + 1.0E-9) {
                  return VehicleTerrain.Hit.clear();
               }
            } else {
               double var31 = (-var23 - var53) / var27;
               double var33 = (var23 - var53) / var27;
               double var35 = Math.min(var31, var33);
               double var37 = Math.max(var31, var33);
               if (var35 > var41) {
                  var41 = var35;
                  var50.set(var22).mul(var27 > 0.0 ? -1.0 : 1.0);
               }

               var46 = Math.min(var46, var37);
               if (var41 > var46 + 1.0E-9) {
                  return VehicleTerrain.Hit.clear();
               }
            }
         }
      }

      if (var17) {
         double var52 = var51.dot(var3);
         return (!(var49 < 1.0E-7) || !(var52 >= -1.0E-10)) && (!(var49 >= 1.0E-7) || !(var52 > 1.0E-10))
            ? new VehicleTerrain.Hit(true, true, 0.0, var51, Math.max(0.0, var49))
            : VehicleTerrain.Hit.clear();
      } else {
         return !(var46 < 0.0) && !(var41 < 0.0) && !(var41 > 1.0) && !(var50.dot(var3) >= -1.0E-10)
            ? new VehicleTerrain.Hit(true, true, var41, var50, 0.0)
            : VehicleTerrain.Hit.clear();
      }
   }
}
