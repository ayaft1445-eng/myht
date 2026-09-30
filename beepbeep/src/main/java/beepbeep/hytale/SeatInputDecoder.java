package beepbeep.hytale;

/**
 * Превращает движение «куклы» водителя в газ, руль и ручник.
 *
 * Пока игрок сидит в машине, его собственный персонаж на клиенте не привязан ни к
 * какому маунту и летает (FlyMode.Forced) высоко над машиной. Камера клиента при
 * этом висит на машине, поэтому самого персонажа водитель не видит. W/A/S/D двигают
 * персонажа по мировым осям (камера задаёт movementForceRotation = 0), и по тому, куда
 * он сдвинулся за пакет, мы знаем, какие клавиши зажаты. Пробел поднимает персонажа
 * (ручник), приседание опускает (долгое удержание — выход из машины).
 *
 * Класс не зависит от сервера Hytale, чтобы его можно было проверить тестами.
 * Все методы вызываются под внешней блокировкой (см. SeatInput).
 */
public final class SeatInputDecoder {
   public static final class Settings {
      /** true — клавиши считаются относительно взгляда игрока, false — относительно мировых осей. */
      public boolean lookRelative;
      public boolean invertForward;
      public boolean invertSteer;
      /** Доля направления, после которой ось считается нажатой (диагональ даёт 0.707). */
      public double deadzone = 0.38;
      /** Сколько держать последнее направление, если пакет пришёл без сдвига. */
      public long holdNanos = 250_000_000L;
      /** Состояния движения старше этого считаются неизвестными. */
      public long statesFreshNanos = 1_000_000_000L;
      /** Сдвиг меньше этого — стоим на месте, блоки. */
      public double minStep = 0.0002;
      /** Сдвиг больше этого — телепорт или рывок, опору сбрасываем. */
      public double maxStep = 3.0;
      /** Пауза между пакетами, после которой опору сбрасываем. */
      public long gapResetNanos = 500_000_000L;
      /** Сколько после подъёма/спуска считать пробел/приседание зажатыми. */
      public long verticalHoldNanos = 180_000_000L;
   }

   public static final class Control {
      public final double throttle;
      public final double steer;
      public final boolean handbrake;
      /** Сколько секунд подряд зажато приседание (спуск куклы или флаг crouching), 0 — не зажато. */
      public final double descendSeconds;
      public final boolean active;
      public final String source;

      Control(double throttle, double steer, boolean handbrake, double descendSeconds, boolean active, String source) {
         this.throttle = throttle;
         this.steer = steer;
         this.handbrake = handbrake;
         this.descendSeconds = descendSeconds;
         this.active = active;
         this.source = source;
      }
   }

   private final Settings settings;
   private boolean havePosition;
   private double px;
   private double py;
   private double pz;
   private long positionAt;
   private double dirX;
   private double dirZ;
   private long dirAt;
   private boolean statesKnown;
   private boolean horizontalIdle;
   /** Клиент хоть раз прислал horizontalIdle = false, значит флагу можно верить. */
   private boolean idleFlagLive;
   private boolean jumping;
   private boolean crouchHeld;
   private long crouchSince;
   private long statesAt;
   private float lookYaw;
   private double wishX;
   private double wishZ;
   private long wishAt;
   private long upAt;
   private long downAt;
   private long descendSince;
   public long samples;
   public long resets;
   public double lastDx;
   public double lastDy;
   public double lastDz;

   public SeatInputDecoder(Settings settings) {
      this.settings = settings;
   }

   public Settings settings() {
      return this.settings;
   }

   /**
    * Позиция персонажа из пакета. absolute = true — абсолютная позиция, иначе (x, y, z) —
    * сдвиг с прошлого пакета. teleportAck — пакет подтверждает телепорт.
    *
    * Для направления хватает одних сдвигов, абсолютная позиция нужна только чтобы знать,
    * где сейчас персонаж (и вовремя вернуть его к машине).
    */
   public void onPosition(boolean absolute, double x, double y, double z, boolean teleportAck, long now) {
      double dx;
      double dy;
      double dz;
      if (absolute) {
         if (!this.havePosition || teleportAck || now - this.positionAt > this.settings.gapResetNanos) {
            this.reset(x, y, z, now);
            return;
         }

         dx = x - this.px;
         dy = y - this.py;
         dz = z - this.pz;
      } else {
         dx = x;
         dy = y;
         dz = z;
      }

      double horizontal = Math.hypot(dx, dz);
      if (!Double.isFinite(horizontal) || !Double.isFinite(dy) || horizontal > this.settings.maxStep || Math.abs(dy) > this.settings.maxStep) {
         if (absolute) {
            this.reset(x, y, z, now);
         }

         return;
      }

      this.samples++;
      this.lastDx = dx;
      this.lastDy = dy;
      this.lastDz = dz;
      if (absolute) {
         this.px = x;
         this.py = y;
         this.pz = z;
      } else if (this.havePosition) {
         this.px += dx;
         this.py += dy;
         this.pz += dz;
      }

      this.positionAt = now;
      if (horizontal > this.settings.minStep) {
         this.dirX = dx / horizontal;
         this.dirZ = dz / horizontal;
         this.dirAt = now;
      }

      if (dy > this.settings.minStep) {
         this.upAt = now;
         this.descendSince = 0L;
      } else if (dy < -this.settings.minStep) {
         if (this.descendSince == 0L || now - this.downAt > this.settings.verticalHoldNanos) {
            this.descendSince = now;
         }

         this.downAt = now;
      }
   }

   public void onStates(boolean horizontalIdle, boolean jumping, boolean crouching, long now) {
      this.statesKnown = true;
      this.horizontalIdle = horizontalIdle;
      this.jumping = jumping;
      this.statesAt = now;
      if (!horizontalIdle) {
         this.idleFlagLive = true;
      }

      if (crouching && !this.crouchHeld) {
         this.crouchSince = now;
      }

      this.crouchHeld = crouching;
   }

   public void onLook(float yaw) {
      if (Float.isFinite(yaw)) {
         this.lookYaw = yaw;
      }
   }

   public void onWish(double x, double z, long now) {
      double length = Math.hypot(x, z);
      if (Double.isFinite(length) && length > 1.0E-6) {
         this.wishX = x / length;
         this.wishZ = z / length;
         this.wishAt = now;
      } else {
         this.wishX = 0.0;
         this.wishZ = 0.0;
         this.wishAt = now;
      }
   }

   public boolean hasPosition() {
      return this.havePosition;
   }

   public double x() {
      return this.px;
   }

   public double y() {
      return this.py;
   }

   public double z() {
      return this.pz;
   }

   public long positionAt() {
      return this.positionAt;
   }

   public float lookYaw() {
      return this.lookYaw;
   }

   /** Сбрасывает опору после телепорта, чтобы прыжок не посчитался движением. */
   public void reset(double x, double y, double z, long now) {
      this.havePosition = true;
      this.px = x;
      this.py = y;
      this.pz = z;
      this.positionAt = now;
      this.resets++;
   }

   public void forgetPosition() {
      this.havePosition = false;
   }

   /** Забывает пробел и приседание, например сразу после посадки. */
   public void clearVertical() {
      this.upAt = 0L;
      this.downAt = 0L;
      this.descendSince = 0L;
      this.crouchHeld = false;
      this.jumping = false;
   }

   public Control evaluate(long now) {
      Settings s = this.settings;
      boolean statesFresh = this.statesKnown && now - this.statesAt < s.statesFreshNanos;
      boolean released = statesFresh && this.idleFlagLive && this.horizontalIdle;
      double x;
      double z;
      String source;
      if (this.wishAt != 0L && now - this.wishAt < s.holdNanos) {
         x = this.wishX;
         z = this.wishZ;
         source = "wish";
      } else if (!released && this.dirAt != 0L && now - this.dirAt < s.holdNanos) {
         x = this.dirX;
         z = this.dirZ;
         source = "move";
      } else {
         x = 0.0;
         z = 0.0;
         source = released ? "idle" : "none";
      }

      double forward;
      double right;
      if (s.lookRelative) {
         double sin = Math.sin(this.lookYaw);
         double cos = Math.cos(this.lookYaw);
         forward = -x * sin - z * cos;
         right = x * cos - z * sin;
      } else {
         forward = -z;
         right = x;
      }

      double throttle = axis(forward, s.deadzone);
      double steer = axis(right, s.deadzone);
      if (s.invertForward) {
         throttle = -throttle;
      }

      if (s.invertSteer) {
         steer = -steer;
      }

      boolean handbrake = this.upAt != 0L && now - this.upAt < s.verticalHoldNanos || statesFresh && this.jumping;
      double descend = this.descendSince != 0L && now - this.downAt < s.verticalHoldNanos ? (now - this.descendSince) / 1.0E9 : 0.0;
      if (statesFresh && this.crouchHeld) {
         descend = Math.max(descend, (now - this.crouchSince) / 1.0E9);
      }

      boolean active = throttle != 0.0 || steer != 0.0 || handbrake;
      return new Control(throttle, steer, handbrake, descend, active, source);
   }

   static double axis(double value, double deadzone) {
      if (value > deadzone) {
         return 1.0;
      } else {
         return value < -deadzone ? -1.0 : 0.0;
      }
   }
}
