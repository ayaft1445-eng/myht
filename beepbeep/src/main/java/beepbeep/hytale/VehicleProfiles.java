package beepbeep.hytale;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

public final class VehicleProfiles {
   public static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
   private static final int MAX_BYTES = 131072;
   private final Path file;

   public VehicleProfiles(Path var1) {
      this.file = var1.resolve("profiles/beepbeep_suv.json");
   }

   public Path path() {
      return this.file;
   }

   public static JsonObject defaults() throws IOException {
      JsonObject var1;
      try (InputStream var0 = VehicleProfiles.class.getResourceAsStream("/profiles/default.json")) {
         if (var0 == null) {
            throw new IOException("Default profile resource missing");
         }

         var1 = decode(new String(var0.readAllBytes(), StandardCharsets.UTF_8));
      }

      return var1;
   }

   public synchronized VehicleProfiles.Snapshot load() throws IOException {
      if (!Files.exists(this.file)) {
         return new VehicleProfiles.Snapshot(defaults(), "missing");
      } else {
         byte[] var1 = this.readBounded();
         JsonObject var2 = decode(new String(var1, StandardCharsets.UTF_8));
         return VehicleCalibration.migrate(var2) ? this.save(var2, revision(var1)) : new VehicleProfiles.Snapshot(var2, revision(var1));
      }
   }

   public synchronized VehicleProfiles.Snapshot save(JsonObject var1, String var2) throws IOException {
      validate(var1);
      String var3 = Files.exists(this.file) ? revision(this.readBounded()) : "missing";
      if (!Objects.equals(var3, var2)) {
         throw new IllegalArgumentException("Профиль изменён другим редактором. Откройте редактор заново.");
      } else {
         byte[] var4 = (JSON.toJson(var1) + "\n").getBytes(StandardCharsets.UTF_8);
         if (var4.length > 131072) {
            throw new IllegalArgumentException("Профиль слишком большой");
         } else {
            Files.createDirectories(this.file.getParent());
            Path var5 = Files.createTempFile(this.file.getParent(), "vehicle-", ".tmp");

            try {
               Files.write(var5, var4);
               if (Files.exists(this.file)) {
                  Files.copy(this.file, this.file.resolveSibling(this.file.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
               }

               Files.move(var5, this.file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
               Files.deleteIfExists(var5);
            }

            return new VehicleProfiles.Snapshot(var1.deepCopy(), revision(var4));
         }
      }
   }

   private byte[] readBounded() throws IOException {
      byte[] var3;
      try (InputStream var1 = Files.newInputStream(this.file)) {
         byte[] var2 = var1.readNBytes(131073);
         if (var2.length > 131072) {
            throw new IOException("Profile exceeds 128 KiB");
         }

         var3 = var2;
      }

      return var3;
   }

   private static String revision(byte[] var0) {
      try {
         return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(var0));
      } catch (NoSuchAlgorithmException var2) {
         throw new AssertionError(var2);
      }
   }

   public static JsonObject decode(String var0) {
      try {
         JsonObject var1 = JsonParser.parseString(var0).getAsJsonObject();
         validate(var1);
         return var1;
      } catch (IllegalStateException | JsonParseException var2) {
         throw new IllegalArgumentException("Некорректный JSON профиля", var2);
      }
   }

   public static JsonElement get(JsonObject var0, String var1) {
      Object var2 = var0;

      for (String var6 : var1.split("\\.")) {
         if (var2 == null || var2.isJsonNull()) {
            throw bad(var1, "поле отсутствует");
         }

         try {
            var2 = var2.isJsonArray() ? var2.getAsJsonArray().get(Integer.parseInt(var6)) : var2.getAsJsonObject().get(var6);
         } catch (RuntimeException var8) {
            throw bad(var1, "неверная структура");
         }
      }

      if (var2 != null && !var2.isJsonNull()) {
         return (JsonElement)var2;
      } else {
         throw bad(var1, "поле отсутствует");
      }
   }

   public static void set(JsonObject var0, String var1, JsonElement var2) {
      int var3 = var1.lastIndexOf(46);
      Object var4 = var3 < 0 ? var0 : get(var0, var1.substring(0, var3));
      String var5 = var1.substring(var3 + 1);
      if (var4.isJsonArray()) {
         var4.getAsJsonArray().set(Integer.parseInt(var5), var2);
      } else {
         var4.getAsJsonObject().add(var5, var2);
      }
   }

   private static IllegalArgumentException bad(String var0, String var1) {
      return new IllegalArgumentException(var0 + ": " + var1);
   }

   private static double number(JsonObject var0, String var1, double var2, double var4) {
      JsonElement var6 = get(var0, var1);
      if (var6.isJsonPrimitive() && var6.getAsJsonPrimitive().isNumber()) {
         double var7 = var6.getAsDouble();
         if (Double.isFinite(var7) && !(var7 < var2) && !(var7 > var4)) {
            return var7;
         } else {
            throw bad(var1, "диапазон " + var2 + " .. " + var4);
         }
      } else {
         throw bad(var1, "нужно число");
      }
   }

   private static String string(JsonObject var0, String var1, int var2) {
      JsonElement var3 = get(var0, var1);
      if (var3.isJsonPrimitive() && var3.getAsJsonPrimitive().isString()) {
         String var4 = var3.getAsString();
         if (!var4.isBlank() && var4.length() <= var2 && !var4.chars().anyMatch(Character::isISOControl)) {
            return var4;
         } else {
            throw bad(var1, "пустой или недопустимый текст");
         }
      } else {
         throw bad(var1, "нужен текст");
      }
   }

   private static boolean bool(JsonObject var0, String var1) {
      JsonElement var2 = get(var0, var1);
      if (var2.isJsonPrimitive() && var2.getAsJsonPrimitive().isBoolean()) {
         return var2.getAsBoolean();
      } else {
         throw bad(var1, "нужно true или false");
      }
   }

   private static JsonArray array(JsonObject var0, String var1, int var2, int var3) {
      JsonElement var4 = get(var0, var1);
      if (var4.isJsonArray() && var4.getAsJsonArray().size() >= var2 && var4.getAsJsonArray().size() <= var3) {
         return var4.getAsJsonArray();
      } else {
         throw bad(var1, "количество " + var2 + " .. " + var3);
      }
   }

   private static void vector(JsonObject var0, String var1, double var2, double var4) {
      for (String var7 : List.of("x", "y", "z")) {
         number(var0, var1 + "." + var7, var2, var4);
      }
   }

   public static void validate(JsonObject var0) {
      number(var0, "schemaVersion", 1.0, 1.0);
      string(var0, "id", 64);
      string(var0, "displayName", 100);
      string(var0, "modelAsset", 128);
      string(var0, "movementConfig", 128);
      number(var0, "massKg", 50.0, 50000.0);
      vector(var0, "centerOfMass", -10.0, 10.0);
      vector(var0, "chassis.halfExtents", 0.05, 20.0);
      if (var0.getAsJsonObject("chassis").has("center")) {
         vector(var0, "chassis.center", -20.0, 20.0);
      }

      number(var0, "chassis.maxSlopeDegrees", 0.0, 85.0);
      number(var0, "chassis.airControl", 0.0, 1.0);
      JsonArray var1 = array(var0, "wheels", 2, 12);
      HashSet var2 = new HashSet();

      for (int var3 = 0; var3 < var1.size(); var3++) {
         String var4 = "wheels." + var3;
         if (!var2.add(string(var0, var4 + ".id", 64))) {
            throw bad(var4 + ".id", "повторяется");
         }

         vector(var0, var4 + ".position", -20.0, 20.0);
         number(var0, var4 + ".radius", 0.05, 3.0);
         number(var0, var4 + ".width", 0.02, 3.0);
         bool(var0, var4 + ".steer");
         bool(var0, var4 + ".drive");
         number(var0, var4 + ".brakeBias", 0.0, 1.0);
         String var5 = var4 + ".suspension";
         double var6 = number(var0, var5 + ".restLength", 0.05, 3.0);
         number(var0, var5 + ".travel", 0.01, var6);
         number(var0, var5 + ".springNPerM", 100.0, 1000000.0);
         number(var0, var5 + ".compressionNsPerM", 0.0, 100000.0);
         number(var0, var5 + ".reboundNsPerM", 0.0, 100000.0);
         string(var0, var5 + ".antiRollGroup", 64);
         number(var0, var4 + ".tire.longitudinalGrip", 0.0, 5.0);
         number(var0, var4 + ".tire.lateralGrip", 0.0, 5.0);
         number(var0, var4 + ".tire.rollingResistance", 0.0, 1.0);
      }

      number(var0, "stabilizers.frontNPerM", 0.0, 1000000.0);
      number(var0, "stabilizers.rearNPerM", 0.0, 1000000.0);
      double var14 = number(var0, "engine.idleRpm", 100.0, 5000.0);
      double var15 = number(var0, "engine.redlineRpm", var14 + 1.0, 30000.0);
      double var7 = number(var0, "engine.shiftDownRpm", var14, var15);
      number(var0, "engine.shiftUpRpm", var7 + 1.0, var15);
      number(var0, "engine.peakTorqueNm", 1.0, 100000.0);
      number(var0, "engine.reverseRatio", 0.01, 30.0);
      number(var0, "engine.finalDrive", 0.01, 30.0);
      JsonArray var9 = array(var0, "engine.gearRatios", 1, 12);

      for (int var10 = 0; var10 < var9.size(); var10++) {
         number(var0, "engine.gearRatios." + var10, 0.01, 30.0);
      }

      number(var0, "brakes.maxForceN", 0.0, 1000000.0);
      number(var0, "brakes.handbrakeForceN", 0.0, 1000000.0);
      JsonArray var16 = array(var0, "seats", 1, 16);
      var2.clear();
      int var11 = 0;

      for (int var12 = 0; var12 < var16.size(); var12++) {
         String var13 = "seats." + var12;
         if (!var2.add(string(var0, var13 + ".id", 64))) {
            throw bad(var13 + ".id", "повторяется");
         }

         vector(var0, var13 + ".position", -20.0, 20.0);
         number(var0, var13 + ".yaw", -180.0, 180.0);
         if (bool(var0, var13 + ".driver")) {
            var11++;
         }
      }

      if (var11 != 1) {
         throw bad("seats", "должно быть ровно одно место водителя");
      }
   }

   public static record Snapshot(JsonObject value, String revision) {
   }
}
