package beepbeep.hytale;

import com.hypixel.hytale.protocol.MouseButtonState;
import com.hypixel.hytale.protocol.MouseButtonType;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.DismountNPC;
import com.hypixel.hytale.protocol.packets.player.ClientMovement;
import com.hypixel.hytale.protocol.packets.player.MouseInteraction;
import java.util.Locale;
import java.util.UUID;

/**
 * Ввод одного седока.
 *
 * Пакеты приходят в сетевом потоке (через SeatPacketFilter), а читает их мир в своём
 * тике, поэтому всё состояние под одной блокировкой. Пакеты движения седока сервер
 * дальше не обрабатывает (иначе он бы двигал персонажа вслед за «куклой»), кроме
 * подтверждений телепорта — без них сервер отключит игрока.
 */
public final class SeatInput {
   /** Сколько ждать подтверждения первого телепорта куклы, прежде чем принимать ввод без него. */
   private static final long READY_TIMEOUT_NANOS = 3_000_000_000L;

   private final UUID player;
   private final SeatInputDecoder decoder;
   private final boolean useWish;
   private final long createdAt;
   private boolean consuming = true;
   private boolean ready;
   private String exitRequest;
   private int viewToggles;
   private long lastPacketAt;
   private MovementStates lastStates;
   private double lastWishX;
   private double lastWishZ;
   private double lastLookYaw;
   private volatile VehicleDebug debug;
   public long packets;
   public long consumed;
   public long positions;
   public long acks;
   public long wishes;
   public long clicks;

   public SeatInput(UUID player, SeatInputDecoder.Settings settings, boolean useWish, long now) {
      this.player = player;
      this.decoder = new SeatInputDecoder(settings);
      this.useWish = useWish;
      this.createdAt = now;
   }

   public UUID player() {
      return this.player;
   }

   public void debug(VehicleDebug debug) {
      this.debug = debug;
   }

   /**
    * Разбирает пакет игрока. Возвращает true, если пакет нужно проглотить и не отдавать
    * серверу.
    */
   public boolean accept(Packet packet, long now) {
      boolean swallow;
      synchronized (this) {
         swallow = this.acceptLocked(packet, now);
      }

      VehicleDebug trace = this.debug;
      if (trace != null) {
         trace.packet(this, packet, swallow);
      }

      return swallow;
   }

   private boolean acceptLocked(Packet packet, long now) {
      if (packet instanceof ClientMovement movement) {
         this.packets++;
         this.lastPacketAt = now;
         boolean ack = movement.teleportAck != null;
         if (movement.movementStates != null) {
            MovementStates states = movement.movementStates;
            this.lastStates = states;
            this.decoder.onStates(states.horizontalIdle, states.jumping, states.crouching, now);
         }

         if (movement.lookOrientation != null) {
            this.lastLookYaw = movement.lookOrientation.yaw;
            this.decoder.onLook(movement.lookOrientation.yaw);
         }

         if (movement.wishMovement != null) {
            this.wishes++;
            this.lastWishX = movement.wishMovement.x;
            this.lastWishZ = movement.wishMovement.z;
            if (this.useWish) {
               this.decoder.onWish(movement.wishMovement.x, movement.wishMovement.z, now);
            }
         }

         if (movement.absolutePosition != null) {
            this.positions++;
            this.decoder.onPosition(true, movement.absolutePosition.x, movement.absolutePosition.y, movement.absolutePosition.z, ack, now);
         } else if (movement.relativePosition != null) {
            this.positions++;
            this.decoder.onPosition(
               false, movement.relativePosition.x / 10000.0, movement.relativePosition.y / 10000.0, movement.relativePosition.z / 10000.0, false, now
            );
         }

         if (ack) {
            this.acks++;
            this.ready = true;
            return false;
         } else {
            if (this.consuming) {
               this.consumed++;
            }

            return this.consuming;
         }
      } else if (packet instanceof MouseInteraction mouse) {
         if (mouse.mouseButton != null
            && mouse.mouseButton.mouseButtonType == MouseButtonType.Middle
            && mouse.mouseButton.state == MouseButtonState.Pressed) {
            this.clicks++;
            this.viewToggles++;
         }

         return false;
      } else {
         if (packet instanceof DismountNPC && this.exitRequest == null) {
            this.exitRequest = "dismount-key";
         }

         return false;
      }
   }

   /** Перестать глотать пакеты (идёт высадка). */
   public synchronized void release() {
      this.consuming = false;
   }

   public synchronized boolean ready(long now) {
      if (!this.ready && now - this.createdAt > READY_TIMEOUT_NANOS) {
         this.ready = true;
      }

      return this.ready;
   }

   public synchronized SeatInputDecoder.Control control(long now) {
      return this.decoder.evaluate(now);
   }

   public synchronized void requestExit(String reason) {
      if (this.exitRequest == null) {
         this.exitRequest = reason;
      }
   }

   public synchronized String pollExit() {
      String reason = this.exitRequest;
      this.exitRequest = null;
      return reason;
   }

   public synchronized int pollViewToggles() {
      int toggles = this.viewToggles;
      this.viewToggles = 0;
      return toggles;
   }

   /** Сбрасывает накопленное удержание кнопок, например сразу после посадки. */
   public synchronized void clearHolds() {
      this.decoder.clearVertical();
   }

   public synchronized boolean hasPuppetPosition() {
      return this.decoder.hasPosition();
   }

   public synchronized double puppetX() {
      return this.decoder.x();
   }

   public synchronized double puppetY() {
      return this.decoder.y();
   }

   public synchronized double puppetZ() {
      return this.decoder.z();
   }

   public synchronized double ageSeconds(long now) {
      return this.lastPacketAt == 0L ? Double.POSITIVE_INFINITY : (now - this.lastPacketAt) / 1.0E9;
   }

   public synchronized String describe(long now) {
      SeatInputDecoder.Control control = this.decoder.evaluate(now);
      MovementStates states = this.lastStates;
      return String.format(
         Locale.ROOT,
         "ввод: газ=%.0f руль=%.0f ручник=%s выход=%.1fс источник=%s | пакеты=%d проглочено=%d позиции=%d подтверждения=%d wish=%d(%.2f,%.2f) клики=%d возраст=%.2fс"
            + " | кукла=%s сдвиг=(%.3f,%.3f,%.3f) сбросы=%d | состояния: idle=%s летит=%s присел=%s прыжок=%s | взгляд=%.0f° готов=%s",
         control.throttle,
         control.steer,
         control.handbrake,
         control.descendSeconds,
         control.source,
         this.packets,
         this.consumed,
         this.positions,
         this.acks,
         this.wishes,
         this.lastWishX,
         this.lastWishZ,
         this.clicks,
         this.lastPacketAt == 0L ? -1.0 : (now - this.lastPacketAt) / 1.0E9,
         this.decoder.hasPosition() ? String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", this.decoder.x(), this.decoder.y(), this.decoder.z()) : "нет",
         this.decoder.lastDx,
         this.decoder.lastDy,
         this.decoder.lastDz,
         this.decoder.resets,
         states == null ? "?" : states.horizontalIdle,
         states == null ? "?" : states.flying,
         states == null ? "?" : states.crouching,
         states == null ? "?" : states.jumping,
         Math.toDegrees(this.lastLookYaw),
         this.ready
      );
   }
}
