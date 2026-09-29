package beepbeep.hytale;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class VehicleCalibration {
   public static final String MODEL = "BeepBeep_GreenLandCruiser_AssetModel";
   public static final double WHEEL_CENTER_Y = 0.22516578125;
   public static final double TIRE_BOTTOM = -0.020454375;

   private VehicleCalibration() {
   }

   public static boolean migrate(JsonObject var0) {
      if (!"BeepBeep_GreenLandCruiser_AssetModel".equals(var0.get("modelAsset").getAsString())) {
         return false;
      } else {
         JsonObject var1 = var0.getAsJsonObject("chassis");
         JsonObject var2 = var1.getAsJsonObject("halfExtents");
         if (!var1.has("center") && eq(var2, "x", 0.95) && eq(var2, "y", 0.55) && eq(var2, "z", 2.05)) {
            JsonArray var3 = var0.getAsJsonArray("wheels");
            if (var3.size() != 4) {
               return false;
            } else {
               for (int var4 = 0; var4 < 4; var4++) {
                  JsonObject var5 = var3.get(var4).getAsJsonObject();
                  JsonObject var6 = var5.getAsJsonObject("position");
                  if (!eq(var6, "x", (double)(var4 % 2 == 0 ? -1 : 1) * 0.82)
                     || !eq(var6, "y", 0.42)
                     || !eq(var6, "z", var4 < 2 ? 1.25 : -1.25)
                     || !eq(var5, "radius", 0.38)) {
                     return false;
                  }
               }

               apply(var0);
               return true;
            }
         } else {
            return false;
         }
      }
   }

   public static void apply(JsonObject var0) {
      JsonObject var1 = var0.getAsJsonObject("chassis");
      JsonObject var2 = var1.getAsJsonObject("halfExtents");
      var2.addProperty("x", 0.55);
      var2.addProperty("y", 0.46);
      var2.addProperty("z", 0.86);
      JsonObject var3 = new JsonObject();
      var3.addProperty("x", 0);
      var3.addProperty("y", 0.73);
      var3.addProperty("z", -0.04);
      var1.add("center", var3);
      JsonArray var4 = var0.getAsJsonArray("wheels");
      double var5 = 0.4894634375;
      double var7 = 0.5261615625;

      for (int var9 = 0; var9 < 4; var9++) {
         JsonObject var10 = var4.get(var9).getAsJsonObject();
         JsonObject var11 = var10.getAsJsonObject("position");
         JsonObject var12 = var10.getAsJsonObject("suspension");
         double var13 = var0.get("massKg").getAsDouble() * 9.81 * (var9 < 2 ? var7 : var5) / (var5 + var7) / 2.0;
         var11.addProperty("x", (double)(var9 % 2 == 0 ? -1 : 1) * 37.5 / 64.0);
         var11.addProperty("z", var9 < 2 ? var5 : -var7);
         var11.addProperty("y", 0.22516578125 + var12.get("restLength").getAsDouble() - var13 / var12.get("springNPerM").getAsDouble());
         var10.addProperty("radius", 0.24562015625);
         var10.addProperty("width", 0.1875);
      }
   }

   public static VehicleRuntimeComponent runtime(JsonObject var0) {
      VehicleRuntimeComponent var1 = new VehicleRuntimeComponent();
      var1.profileId = var0.get("id").getAsString();
      var1.mass = var0.get("massKg").getAsDouble();
      JsonObject var2 = var0.getAsJsonObject("engine");
      var1.idleRpm = var2.get("idleRpm").getAsDouble();
      var1.redlineRpm = var2.get("redlineRpm").getAsDouble();
      var1.peakTorqueNm = var2.get("peakTorqueNm").getAsDouble();
      var1.reverseRatio = var2.get("reverseRatio").getAsDouble();
      var1.finalDrive = var2.get("finalDrive").getAsDouble();
      var1.shiftUpRpm = var2.get("shiftUpRpm").getAsDouble();
      var1.shiftDownRpm = var2.get("shiftDownRpm").getAsDouble();
      JsonArray var3 = var2.getAsJsonArray("gearRatios");
      var1.gearRatios = new double[var3.size()];

      for (int var4 = 0; var4 < var3.size(); var4++) {
         var1.gearRatios[var4] = var3.get(var4).getAsDouble();
      }

      JsonObject var13 = var0.getAsJsonObject("brakes");
      var1.maxBrakeForce = var13.get("maxForceN").getAsDouble();
      var1.handbrakeForce = var13.get("handbrakeForceN").getAsDouble();
      var1.engineRpm = var1.idleRpm;
      JsonObject var5 = var0.getAsJsonObject("chassis");
      JsonObject var6 = var5.getAsJsonObject("halfExtents");
      var1.halfX = var6.get("x").getAsDouble();
      var1.halfY = var6.get("y").getAsDouble();
      var1.halfZ = var6.get("z").getAsDouble();
      if (var5.has("center")) {
         JsonObject var7 = var5.getAsJsonObject("center");
         var1.bodyX = var7.get("x").getAsDouble();
         var1.bodyY = var7.get("y").getAsDouble();
         var1.bodyZ = -var7.get("z").getAsDouble();
      } else {
         var1.bodyY = var1.halfY + 0.08;
      }

      JsonArray var14 = var0.getAsJsonArray("wheels");
      int var8 = var14.size();
      var1.wheelX = new double[var8];
      var1.wheelY = new double[var8];
      var1.wheelZ = new double[var8];
      var1.radius = new double[var8];
      var1.rest = new double[var8];
      var1.travel = new double[var8];
      var1.spring = new double[var8];
      var1.damping = new double[var8];
      var1.rebound = new double[var8];
      var1.contactY = new double[var8];
      var1.contact = new boolean[var8];

      for (int var9 = 0; var9 < var8; var9++) {
         JsonObject var10 = var14.get(var9).getAsJsonObject();
         JsonObject var11 = var10.getAsJsonObject("position");
         JsonObject var12 = var10.getAsJsonObject("suspension");
         var1.wheelX[var9] = var11.get("x").getAsDouble();
         var1.wheelY[var9] = var11.get("y").getAsDouble();
         var1.wheelZ[var9] = var11.get("z").getAsDouble();
         var1.radius[var9] = var10.get("radius").getAsDouble();
         var1.rest[var9] = var12.get("restLength").getAsDouble();
         var1.travel[var9] = var12.get("travel").getAsDouble();
         var1.spring[var9] = var12.get("springNPerM").getAsDouble();
         var1.damping[var9] = var12.get("compressionNsPerM").getAsDouble();
         var1.rebound[var9] = var12.get("reboundNsPerM").getAsDouble();
      }

      for (JsonElement var16 : var0.getAsJsonArray("seats")) {
         JsonObject var17 = var16.getAsJsonObject();
         if (var17.get("driver").getAsBoolean()) {
            JsonObject var18 = var17.getAsJsonObject("position");
            var1.seatX = var18.get("x").getAsDouble();
            var1.seatY = var18.get("y").getAsDouble();
            var1.seatZ = var18.get("z").getAsDouble();
            break;
         }
      }

      return var1;
   }

   private static boolean eq(JsonObject var0, String var1, double var2) {
      return Math.abs(var0.get(var1).getAsDouble() - var2) < 1.0E-8;
   }
}
