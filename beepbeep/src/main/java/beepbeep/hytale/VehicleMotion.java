package beepbeep.hytale;

import com.hypixel.hytale.math.shape.Box;
import java.util.Arrays;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class VehicleMotion {
   public static final double STEP = 0.008333333333333333;
   public static final double MAX_STEP = 1.02;
   public static final double SKIN = 0.003;
   private static final Box PROBE = new Box(-0.025, -0.025, -0.025, 0.025, 0.025, 0.025);

   private VehicleMotion() {
   }

   public static Box chassis(VehicleRuntimeComponent var0, double var1) {
      return VehicleBody.shape(var0, var1, var0.pitch, var0.roll).bounds();
   }

   public static VehicleMotion.Move horizontal(VehicleTerrain var0, Box var1, Vector3d var2, Vector3d var3, boolean var4) {
      return VehicleBody.horizontal(var0, new VehicleShape(var1, 0.0, 0.0, 0.0), var2, var3, var4);
   }

   public static Vector3d step(VehicleRuntimeComponent var0, Vector3d var1, VehicleTerrain var2) {
      if (var0.debug != null && var0.debug.trace.active()) {
         if (var0.debugCompression.length != var0.wheelX.length) {
            var0.debugCompression = new double[var0.wheelX.length];
            var0.debugLoad = new double[var0.wheelX.length];
         }

         Arrays.fill(var0.debugCompression, 0.0);
         Arrays.fill(var0.debugLoad, 0.0);
      }

      Vector3d var3 = VehicleBody.recover(var2, VehicleBody.shape(var0, var0.yaw, var0.pitch, var0.roll), var1);
      Quaterniond var4 = new Quaterniond().rotationYXZ(var0.yaw, var0.pitch, var0.roll);
      double var5 = -var0.mass * 9.81;
      double var7 = 0.0;
      double var9 = 0.0;
      double var11 = Double.NEGATIVE_INFINITY;
      int var13 = 0;

      for (int var14 = 0; var14 < var0.wheelX.length; var14++) {
         Vector3d var15 = var4.transform(new Vector3d(var0.wheelX[var14], var0.wheelY[var14], -var0.wheelZ[var14]));
         Vector3d var16 = new Vector3d(var3).add(var15).add(0.0, 1.12, 0.0);
         double var17 = var0.rest[var14] + var0.radius[var14] + 1.02 + 0.2;
         VehicleTerrain.Hit var19 = var2.sweep(PROBE, var16, new Vector3d(0.0, -var17, 0.0));
         if (!var19.loaded()) {
            var0.terrainState = "unloaded";
            var0.velocityForward = 0.0;
            var0.verticalVelocity = 0.0;
            return new Vector3d(var1);
         }

         var0.contact[var14] = false;
         var0.contactY[var14] = Double.NaN;
         if (var19.blocked() && !(var19.normal().y < 0.5)) {
            double var20 = var16.y - var17 * var19.fraction() - 0.025;
            double var22 = var3.y + var15.y - var20 - var0.radius[var14];
            if (!(var22 > var0.rest[var14])) {
               var0.contact[var14] = true;
               var0.contactY[var14] = var20;
               var13++;
               double var24 = Math.max(0.0, Math.min(var0.travel[var14], var0.rest[var14] - var22));
               double var26 = var0.verticalVelocity + var0.rollVelocity * var0.wheelX[var14] + var0.pitchVelocity * var0.wheelZ[var14];
               double var28 = var26 < 0.0 ? var0.damping[var14] : var0.rebound[var14];
               double var30 = Math.max(0.0, var0.spring[var14] * var24 - var28 * var26);
               var30 = Math.min(var30, var0.mass * 9.81 * 5.0);
               if (var0.debug != null && var0.debug.trace.active()) {
                  var0.debugCompression[var14] = var24;
                  var0.debugLoad[var14] = var30;
               }

               var5 += var30;
               var7 += var0.wheelZ[var14] * var30;
               var9 += var0.wheelX[var14] * var30;
               var11 = Math.max(var11, var20 + var0.radius[var14] + var0.rest[var14] - var0.travel[var14] - var15.y);
            }
         }
      }

      boolean var50 = var13 > 0 || var0.bodySupported;
      var0.terrainState = var13 > 0 ? "grounded" : (var0.bodySupported ? "belly" : "airborne");
      double var51 = var0.velocityForward;
      double var55 = 0.0;

      for (double var62 : var0.radius) {
         var55 += var62;
      }

      var55 = var0.radius.length == 0 ? 0.3 : var55 / (double)var0.radius.length;
      double var58;
      if (!(var51 < -0.05) && !(var0.throttle < -0.02)) {
         if (var0.gear < 1 || var0.gear > var0.gearRatios.length) {
            var0.gear = 1;
         }

         double var60 = Math.abs(var51) / ((Math.PI * 2) * Math.max(0.05, var55)) * 60.0 * var0.gearRatios[var0.gear - 1] * var0.finalDrive;
         if (var0.throttle > 0.05 && var0.gear < var0.gearRatios.length && var60 > var0.shiftUpRpm) {
            var0.gear++;
         } else if (Math.abs(var0.throttle) < 0.05 && var0.gear > 1 && var60 < var0.shiftDownRpm) {
            var0.gear--;
         }

         var58 = var0.gearRatios[var0.gear - 1];
      } else {
         var58 = var0.reverseRatio;
      }

      double var61 = Math.abs(var51) / ((Math.PI * 2) * Math.max(0.05, var55)) * 60.0;
      var0.engineRpm = Math.max(var0.idleRpm, Math.min(var0.redlineRpm, var61 * var58 * var0.finalDrive));
      double var23 = var0.peakTorqueNm * Math.max(-1.0, Math.min(1.0, var0.throttle));
      double var25 = var50 ? var23 * var58 * var0.finalDrive / Math.max(0.05, var55) * (var13 > 0 ? 1.0 : 0.35) : 0.0;
      var0.engineForce = var25;
      var51 += (var25 - var51 * Math.abs(var51) * 55.0 - var51 * 80.0) / var0.mass * 0.008333333333333333;
      double var29 = Math.max(var0.maxBrakeForce, 0.0) * Math.max(0.0, Math.min(1.0, var0.brake));
      var51 = Math.copySign(Math.max(0.0, Math.abs(var51) - var29 / var0.mass * 0.008333333333333333), var51);
      var51 = Math.max(-4.0, Math.min(8.0, var51));
      double var31 = var0.yaw - (var50 ? steeringRate(var0, var51) * 0.008333333333333333 : 0.0);
      VehicleShape var33 = VehicleBody.shape(var0, var31, var0.pitch, var0.roll);
      if (!VehicleBody.clear(var2, var33, var3)) {
         var31 = var0.yaw;
         var33 = VehicleBody.shape(var0, var31, var0.pitch, var0.roll);
      }

      Vector3d var34 = new Vector3d(-Math.sin(var31) * var51 * 0.008333333333333333, 0.0, -Math.cos(var31) * var51 * 0.008333333333333333);
      VehicleMotion.Move var35 = VehicleBody.horizontal(var2, var33, var3, var34, var50);
      if (!var35.loaded()) {
         var0.terrainState = "unloaded";
         var0.velocityForward = 0.0;
         var0.verticalVelocity = 0.0;
         return new Vector3d(var1);
      } else {
         if (var35.blocked()) {
            Vector3d var36 = new Vector3d(var35.position()).sub(var3);
            var36.y = 0.0;
            double var37 = var34.lengthSquared() < 1.0E-12 ? 0.0 : Math.max(0.0, Math.min(1.0, var36.dot(var34) / var34.lengthSquared()));
            var51 *= var37;
            var0.terrainState = var37 > 0.01 ? "sliding" : "blocked";
         }

         double var64 = var35.position().y - var3.y;
         var3.set(var35.position());
         double var38 = var0.verticalVelocity + var5 / var0.mass * 0.008333333333333333;
         if (var64 > 0.01) {
            var38 = Math.max(0.0, var38);
         }

         Vector3d var40 = new Vector3d(0.0, var38 * 0.008333333333333333, 0.0);
         VehicleTerrain.Hit var41 = var2.sweep(var33, var3, var40);
         if (!var41.loaded()) {
            var0.terrainState = "unloaded";
            var0.velocityForward = 0.0;
            var0.verticalVelocity = 0.0;
            return new Vector3d(var1);
         } else {
            var0.bodySupported = var41.blocked() && var41.normal().y > 0.4;
            if (var41.blocked()) {
               var3.fma(Math.max(0.0, var41.fraction() - 0.003 / Math.max(1.0E-9, Math.abs(var40.y))), var40);
               var38 = 0.0;
            } else {
               var3.add(var40);
            }

            if (var13 > 0 && var3.y < var11 && var11 - var3.y < 0.3) {
               Vector3d var42 = new Vector3d(0.0, var11 - var3.y, 0.0);
               VehicleTerrain.Hit var43 = var2.sweep(var33, var3, var42);
               if (var43.loaded() && !var43.blocked()) {
                  var3.add(var42);
                  var38 = Math.max(0.0, var38);
               }
            }

            double var65 = var0.mass * (var0.halfY * var0.halfY + var0.halfZ * var0.halfZ) / 3.0;
            double var44 = var0.mass * (var0.halfX * var0.halfX + var0.halfY * var0.halfY) / 3.0;
            var0.pitchVelocity = Math.max(-1.0, Math.min(1.0, (var0.pitchVelocity + var7 / var65 * 0.008333333333333333) * Math.exp(-0.016666666666666666)));
            var0.rollVelocity = Math.max(-1.0, Math.min(1.0, (var0.rollVelocity + var9 / var44 * 0.008333333333333333) * Math.exp(-0.016666666666666666)));
            double var46 = Math.max(-0.5, Math.min(0.5, var0.pitch + var0.pitchVelocity * 0.008333333333333333));
            double var48 = Math.max(-0.5, Math.min(0.5, var0.roll + var0.rollVelocity * 0.008333333333333333));
            if (VehicleBody.clear(var2, VehicleBody.shape(var0, var31, var46, var0.roll), var3)) {
               var0.pitch = var46;
            } else {
               var0.pitchVelocity = 0.0;
            }

            if (VehicleBody.clear(var2, VehicleBody.shape(var0, var31, var0.pitch, var48), var3)) {
               var0.roll = var48;
            } else {
               var0.rollVelocity = 0.0;
            }

            var0.velocityForward = var51;
            var0.verticalVelocity = var38;
            var0.yaw = var31;
            return var3;
         }
      }
   }

   static double steeringRate(VehicleRuntimeComponent var0, double var1) {
      double var3 = Double.NEGATIVE_INFINITY;
      double var5 = Double.POSITIVE_INFINITY;

      for (double var10 : var0.wheelZ) {
         var3 = Math.max(var3, var10);
         var5 = Math.min(var5, var10);
      }

      double var12 = var0.wheelZ.length < 2 ? 2.5 : Math.max(1.0, var3 - var5);
      double var13 = Math.max(-1.0, Math.min(1.0, var0.steer)) * Math.toRadians(25.0);
      return Math.max(-0.9, Math.min(0.9, var1 * Math.tan(var13) / var12));
   }

   public static record Move(Vector3d position, boolean blocked, boolean loaded) {
   }
}
