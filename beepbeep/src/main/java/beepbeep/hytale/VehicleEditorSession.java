package beepbeep.hytale;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class VehicleEditorSession {
   private final VehicleProfiles repository;
   private JsonObject draft;
   private String revision;
   private String tab = "body";
   private int wheel;
   private int seat;
   private boolean dirty;
   private String status = "Изменения сохраняются только кнопкой «Сохранить».";

   public VehicleEditorSession(VehicleProfiles var1) throws IOException {
      this.repository = var1;
      VehicleProfiles.Snapshot var2 = var1.load();
      this.draft = var2.value();
      this.revision = var2.revision();
   }

   public JsonObject draft() {
      return this.draft.deepCopy();
   }

   public String status() {
      return this.status;
   }

   public void setStatus(String var1) {
      this.status = var1;
   }

   public String location() {
      String var1 = this.tab;

      return switch (var1) {
         case "wheels" -> "Колесо " + (this.wheel + 1) + " / " + this.draft.getAsJsonArray("wheels").size();
         case "seats" -> "Место " + (this.seat + 1) + " / " + this.draft.getAsJsonArray("seats").size();
         case "engine" -> "Мотор и тормоза";
         default -> "Кузов и профиль";
      } + (this.dirty ? " • есть несохранённые изменения" : "");
   }

   public List<VehicleEditorSession.Field> fields() {
      ArrayList var1 = new ArrayList();
      String var2 = this.tab;
      switch (var2) {
         case "body":
            add(var1, "Название", "displayName", "text");
            add(var1, "Модель (asset ID)", "modelAsset", "text");
            add(var1, "Масса, кг", "massKg", "number");
            xyz(var1, "Центр массы", "centerOfMass");
            xyz(var1, "Полуразмер кузова", "chassis.halfExtents");
            if (this.draft.getAsJsonObject("chassis").has("center")) {
               xyz(var1, "Центр кузова", "chassis.center");
            }

            add(var1, "Стабилизатор передний, Н/м", "stabilizers.frontNPerM", "number");
            add(var1, "Стабилизатор задний, Н/м", "stabilizers.rearNPerM", "number");
            break;
         case "wheels":
            String var5 = "wheels." + this.wheel;
            add(var1, "ID колеса", var5 + ".id", "text");
            xyz(var1, "Крепление колеса", var5 + ".position");
            add(var1, "Радиус, м", var5 + ".radius", "number");
            add(var1, "Ширина, м", var5 + ".width", "number");
            add(var1, "Поворотное: true / false", var5 + ".steer", "bool");
            add(var1, "Ведущее: true / false", var5 + ".drive", "bool");
            add(var1, "Доля тормозного усилия, 0..1", var5 + ".brakeBias", "number");
            add(var1, "Свободная длина подвески, м", var5 + ".suspension.restLength", "number");
            add(var1, "Ход сжатия, м", var5 + ".suspension.travel", "number");
            add(var1, "Жёсткость пружины, Н/м", var5 + ".suspension.springNPerM", "number");
            add(var1, "Демпфер сжатия, Н·с/м", var5 + ".suspension.compressionNsPerM", "number");
            add(var1, "Демпфер отбоя, Н·с/м", var5 + ".suspension.reboundNsPerM", "number");
            add(var1, "Группа стабилизатора", var5 + ".suspension.antiRollGroup", "text");
            add(var1, "Сцепление продольное", var5 + ".tire.longitudinalGrip", "number");
            add(var1, "Сцепление поперечное", var5 + ".tire.lateralGrip", "number");
            add(var1, "Сопротивление качению", var5 + ".tire.rollingResistance", "number");
            break;
         case "engine":
            add(var1, "Холостой ход, об/мин", "engine.idleRpm", "number");
            add(var1, "Отсечка, об/мин", "engine.redlineRpm", "number");
            add(var1, "Пиковый момент, Н·м", "engine.peakTorqueNm", "number");
            add(var1, "Задняя передача", "engine.reverseRatio", "number");
            add(var1, "Передачи (через ;)", "engine.gearRatios", "gears");
            add(var1, "Главная передача", "engine.finalDrive", "number");
            add(var1, "Переключение вверх, об/мин", "engine.shiftUpRpm", "number");
            add(var1, "Переключение вниз, об/мин", "engine.shiftDownRpm", "number");
            add(var1, "Тормозное усилие, Н", "brakes.maxForceN", "number");
            add(var1, "Ручной тормоз, Н", "brakes.handbrakeForceN", "number");
            break;
         case "seats":
            String var4 = "seats." + this.seat;
            add(var1, "ID места", var4 + ".id", "text");
            xyz(var1, "Положение места", var4 + ".position");
            add(var1, "Поворот, градусы", var4 + ".yaw", "number");
            add(var1, "Водитель: true / false", var4 + ".driver", "bool");
            break;
         default:
            throw new IllegalStateException(this.tab);
      }

      return List.copyOf(var1);
   }

   private static void add(List<VehicleEditorSession.Field> var0, String var1, String var2, String var3) {
      var0.add(new VehicleEditorSession.Field(var1, var2, var3));
   }

   private static void xyz(List<VehicleEditorSession.Field> var0, String var1, String var2) {
      for (String var4 : List.of("x", "y", "z")) {
         add(var0, var1 + " " + var4.toUpperCase() + ", м", var2 + "." + var4, "number");
      }
   }

   public String value(VehicleEditorSession.Field var1) {
      JsonElement var2 = VehicleProfiles.get(this.draft, var1.path());
      if (!var1.kind().equals("gears")) {
         return var2.getAsString();
      } else {
         ArrayList var3 = new ArrayList();
         var2.getAsJsonArray().forEach(var1x -> var3.add(var1x.getAsString()));
         return String.join("; ", var3);
      }
   }

   private static double parseNumber(String var0, String var1) {
      try {
         double var2 = Double.parseDouble(var0.trim().replace(',', '.'));
         if (Double.isFinite(var2)) {
            return var2;
         }
      } catch (NumberFormatException var4) {
      }

      throw new IllegalArgumentException(var1 + ": введите конечное число");
   }

   private static JsonElement parse(VehicleEditorSession.Field var0, String var1) {
      String var2 = var0.kind();

      return (JsonElement)(switch (var2) {
         case "number" -> new JsonPrimitive(parseNumber(var1, var0.label()));
         case "bool" -> {
            if (!var1.trim().equalsIgnoreCase("true") && !var1.trim().equalsIgnoreCase("false")) {
               throw new IllegalArgumentException(var0.label() + ": введите true или false");
            }

            yield new JsonPrimitive(Boolean.parseBoolean(var1.trim()));
         }
         case "gears" -> {
            JsonArray var4 = new JsonArray();

            for (String var8 : var1.split(";", -1)) {
               var4.add(parseNumber(var8, var0.label()));
            }

            yield var4;
         }
         default -> new JsonPrimitive(var1.trim());
      });
   }

   public boolean accept(String var1, List<String> var2) throws IOException {
      if (!Set.of("save", "body", "wheels", "engine", "seats", "prev", "next", "add", "remove").contains(var1)) {
         throw new IllegalArgumentException("Неизвестное действие");
      } else {
         List var3 = this.fields();
         if (var2.size() != var3.size()) {
            throw new IllegalArgumentException("Неполные данные формы");
         } else {
            JsonObject var4 = this.draft.deepCopy();

            for (int var5 = 0; var5 < var3.size(); var5++) {
               VehicleProfiles.set(
                  var4, ((VehicleEditorSession.Field)var3.get(var5)).path(), parse((VehicleEditorSession.Field)var3.get(var5), (String)var2.get(var5))
               );
            }

            if (this.tab.equals("seats") && VehicleProfiles.get(var4, "seats." + this.seat + ".driver").getAsBoolean()) {
               JsonArray var13 = var4.getAsJsonArray("seats");

               for (int var6 = 0; var6 < var13.size(); var6++) {
                  if (var6 != this.seat) {
                     var13.get(var6).getAsJsonObject().addProperty("driver", false);
                  }
               }
            }

            int var14 = this.wheel;
            int var15 = this.seat;
            if (var1.equals("add") || var1.equals("remove")) {
               if (!this.tab.equals("seats") && !this.tab.equals("wheels")) {
                  throw new IllegalArgumentException("Выберите колёса или места");
               }

               JsonArray var7 = var4.getAsJsonArray(this.tab);
               int var8 = this.tab.equals("wheels") ? this.wheel : this.seat;
               if (var1.equals("remove")) {
                  if (var7.size() <= (this.tab.equals("wheels") ? 2 : 1)) {
                     throw new IllegalArgumentException("Нельзя удалить последний обязательный элемент");
                  }

                  if (this.tab.equals("seats") && var7.get(var8).getAsJsonObject().get("driver").getAsBoolean()) {
                     throw new IllegalArgumentException("Сначала назначьте водителем другое место");
                  }

                  var7.remove(var8);
                  var8 = Math.min(var8, var7.size() - 1);
               } else {
                  if (var7.size() >= (this.tab.equals("wheels") ? 12 : 16)) {
                     throw new IllegalArgumentException("Достигнут предел элементов");
                  }

                  JsonObject var9 = var7.get(var8).getAsJsonObject().deepCopy();
                  HashSet var10 = new HashSet();
                  var7.forEach(var1x -> var10.add(var1x.getAsJsonObject().get("id").getAsString()));
                  String var11 = this.tab.equals("wheels") ? "wheel_" : "seat_";
                  int var12 = 1;

                  while (var10.contains(var11 + var12)) {
                     var12++;
                  }

                  var9.addProperty("id", var11 + var12);
                  if (this.tab.equals("seats")) {
                     var9.addProperty("driver", false);
                  }

                  var7.add(var9);
                  var8 = var7.size() - 1;
               }

               if (this.tab.equals("wheels")) {
                  var14 = var8;
               } else {
                  var15 = var8;
               }
            }

            VehicleProfiles.validate(var4);
            if (var1.equals("save")) {
               VehicleProfiles.Snapshot var16 = this.repository.save(var4, this.revision);
               this.draft = var16.value();
               this.revision = var16.revision();
               this.dirty = false;
               this.status = "Профиль сохранён.";
            } else {
               this.dirty = this.dirty | !var4.equals(this.draft);
               this.draft = var4;
               this.status = "Черновик. Esc закрывает редактор без сохранения.";
            }

            this.wheel = var14;
            this.seat = var15;
            if (Set.of("body", "wheels", "engine", "seats").contains(var1)) {
               this.tab = var1;
            }

            if (var1.equals("prev") || var1.equals("next")) {
               int var17 = var1.equals("next") ? 1 : -1;
               if (this.tab.equals("wheels")) {
                  this.wheel = Math.floorMod(this.wheel + var17, this.draft.getAsJsonArray("wheels").size());
               }

               if (this.tab.equals("seats")) {
                  this.seat = Math.floorMod(this.seat + var17, this.draft.getAsJsonArray("seats").size());
               }
            }

            return var1.equals("save");
         }
      }
   }

   public static record Field(String label, String path, String kind) {
   }
}
