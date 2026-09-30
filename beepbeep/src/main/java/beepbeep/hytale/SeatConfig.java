package beepbeep.hytale;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Настройки посадки: управление, камера, «кукла» водителя. Лежат в seating.json в папке
 * данных мода, создаются со значениями по умолчанию и перечитываются /vehicle seatcfg.
 */
public final class SeatConfig {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

   public static final class Controls {
      /** fixed — W всегда «вперёд машины»; look — W туда, куда смотрит игрок. */
      public String mode = "fixed";
      public boolean invertForward = false;
      public boolean invertSteer = false;
      /** Доля направления, после которой ось считается нажатой (диагональ даёт 0.707). */
      public double deadzone = 0.38;
      /** Сколько держать «присесть», чтобы выйти, секунд. */
      public double exitHoldSeconds = 0.7;
      /** Сколько секунд после посадки не реагировать на «присесть». */
      public double enterGraceSeconds = 1.5;
      /** Средняя кнопка мыши переключает вид. */
      public boolean middleClickCyclesView = true;
      /** Брать направление из поля wishMovement клиента (по умолчанию — только по сдвигу куклы). */
      public boolean useWish = false;
      /** Тормоз, когда газ не нажат: 1 — стоять на месте (как раньше), 0 — катиться накатом. */
      public double idleBrake = 1.0;
   }

   public static final class Puppet {
      /** Высота, на которой висит невидимый персонаж водителя. Мир Hytale — до Y 320, выше рельефа нет. */
      public double altitude = 312.0;
      /** Больше нуля — держать персонажа на столько блоков выше машины вместо фиксированной высоты. */
      public double relativeHeight = 0.0;
      /** Скорость полёта персонажа. Маленькая — чтобы клавиши читались, а персонаж не улетал далеко. */
      public double flySpeed = 0.8;
      public double verticalFlySpeed = 1.2;
      /** Насколько персонаж может отойти от машины по горизонтали, прежде чем его вернут. */
      public double recenterDistance = 16.0;
      /** То же по вертикали. */
      public double recenterHeight = 3.0;
   }

   public static final class Camera {
      /** first, third или chase. */
      public String defaultView = "chase";
      /** Высота глаз над точкой сиденья (вид от первого лица). */
      public double eyeHeight = 1.05;
      /** Вид от первого лица наклоняется вместе с машиной. */
      public boolean firstPersonTilt = true;
      public double orbitDistance = 7.0;
      public double orbitHeight = 1.6;
      public double orbitPitchDegrees = -15.0;
      /** Вид third: мышь крутит камеру вокруг машины. */
      public boolean orbitMouse = true;
      public double chaseDistance = 7.5;
      public double chaseHeight = 1.8;
      public double chasePitchDegrees = -12.0;
      public double positionLerp = 1.0;
      public double rotationLerp = 1.0;
      public double chaseRotationLerp = 0.35;
      public boolean hideHeldItem = true;
      public boolean lockView = true;
      /** Не наводиться мышью на блоки и существа, пока сидишь (чтобы случайно не ломать блоки). */
      public boolean disableTargeting = true;
   }

   public static final class Riders {
      /** Показывать другим игрокам седока пристёгнутым к месту (MountedUpdate). */
      public boolean attachForOthers = true;
      /** sitting или mounting — поза, которую видят другие. */
      public String pose = "sitting";
      /** Сдвиг седока по высоте относительно точки сиденья из профиля. */
      public double offsetY = 0.0;
      public double enterDistance = 6.0;
      /** Модель невидимой точки крепления места. */
      public String anchorModel = "BeepBeep_SeatProxy";
      public double anchorModelScale = 0.001;
   }

   public Controls controls = new Controls();
   public Puppet puppet = new Puppet();
   public Camera camera = new Camera();
   public Riders riders = new Riders();

   public static SeatConfig load(Path file) {
      try {
         if (!Files.exists(file)) {
            SeatConfig defaults = new SeatConfig();
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(defaults) + "\n", StandardCharsets.UTF_8);
            return defaults;
         }

         JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
         SeatConfig config = GSON.fromJson(json, SeatConfig.class);
         return config.sanitized();
      } catch (IOException | RuntimeException error) {
         throw new IllegalArgumentException("seating.json: " + error.getMessage(), error);
      }
   }

   public static SeatConfig defaults() {
      return new SeatConfig().sanitized();
   }

   private SeatConfig sanitized() {
      if (this.controls == null) {
         this.controls = new Controls();
      }

      if (this.puppet == null) {
         this.puppet = new Puppet();
      }

      if (this.camera == null) {
         this.camera = new Camera();
      }

      if (this.riders == null) {
         this.riders = new Riders();
      }

      this.controls.deadzone = clamp(this.controls.deadzone, 0.05, 0.95);
      this.controls.exitHoldSeconds = clamp(this.controls.exitHoldSeconds, 0.2, 5.0);
      this.controls.enterGraceSeconds = clamp(this.controls.enterGraceSeconds, 0.0, 10.0);
      this.controls.idleBrake = clamp(this.controls.idleBrake, 0.0, 1.0);
      this.puppet.altitude = clamp(this.puppet.altitude, -64.0, 316.0);
      this.puppet.relativeHeight = clamp(this.puppet.relativeHeight, 0.0, 250.0);
      this.puppet.flySpeed = clamp(this.puppet.flySpeed, 0.1, 10.0);
      this.puppet.verticalFlySpeed = clamp(this.puppet.verticalFlySpeed, 0.1, 10.0);
      this.puppet.recenterDistance = clamp(this.puppet.recenterDistance, 2.0, 48.0);
      this.puppet.recenterHeight = clamp(this.puppet.recenterHeight, 1.0, 32.0);
      this.camera.eyeHeight = clamp(this.camera.eyeHeight, 0.0, 4.0);
      this.camera.orbitDistance = clamp(this.camera.orbitDistance, 1.0, 30.0);
      this.camera.chaseDistance = clamp(this.camera.chaseDistance, 1.0, 30.0);
      this.camera.positionLerp = clamp(this.camera.positionLerp, 0.01, 1.0);
      this.camera.rotationLerp = clamp(this.camera.rotationLerp, 0.01, 1.0);
      this.camera.chaseRotationLerp = clamp(this.camera.chaseRotationLerp, 0.01, 1.0);
      this.riders.offsetY = clamp(this.riders.offsetY, -3.0, 3.0);
      this.riders.enterDistance = clamp(this.riders.enterDistance, 1.0, 32.0);
      this.riders.anchorModelScale = clamp(this.riders.anchorModelScale, 0.0001, 1.0);
      if (this.controls.mode == null) {
         this.controls.mode = "fixed";
      }

      if (this.camera.defaultView == null) {
         this.camera.defaultView = "chase";
      }

      if (this.riders.pose == null) {
         this.riders.pose = "sitting";
      }

      if (this.riders.anchorModel == null || this.riders.anchorModel.isBlank()) {
         this.riders.anchorModel = "BeepBeep_SeatProxy";
      }

      return this;
   }

   public SeatInputDecoder.Settings decoderSettings() {
      SeatInputDecoder.Settings settings = new SeatInputDecoder.Settings();
      settings.lookRelative = "look".equalsIgnoreCase(this.controls.mode);
      settings.invertForward = this.controls.invertForward;
      settings.invertSteer = this.controls.invertSteer;
      settings.deadzone = this.controls.deadzone;
      return settings;
   }

   private static double clamp(double value, double min, double max) {
      return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
   }
}
