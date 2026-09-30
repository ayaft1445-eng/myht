package beepbeep.hytale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SeatInputDecoderTest {
   private static final long TICK = 33_000_000L;

   private static SeatInputDecoder decoder() {
      return new SeatInputDecoder(new SeatInputDecoder.Settings());
   }

   /** Персонаж летит со скоростью ~0.8 блока/с: сдвиг ~0.027 за пакет. */
   private static long fly(SeatInputDecoder decoder, long start, double dx, double dy, double dz, int packets) {
      long now = start;

      for (int i = 0; i < packets; i++) {
         now += TICK;
         decoder.onPosition(false, dx, dy, dz, false, now);
      }

      return now;
   }

   @Test
   void forwardKeyGivesThrottle() {
      SeatInputDecoder d = decoder();
      long now = fly(d, 0L, 0.0, 0.0, -0.027, 5);
      SeatInputDecoder.Control c = d.evaluate(now);
      assertEquals(1.0, c.throttle);
      assertEquals(0.0, c.steer);
      assertTrue(c.active);
   }

   @Test
   void backwardKeyGivesReverse() {
      SeatInputDecoder d = decoder();
      long now = fly(d, 0L, 0.0, 0.0, 0.027, 5);
      assertEquals(-1.0, d.evaluate(now).throttle);
   }

   @Test
   void rightKeySteersRight() {
      SeatInputDecoder d = decoder();
      long now = fly(d, 0L, 0.027, 0.0, 0.0, 5);
      SeatInputDecoder.Control c = d.evaluate(now);
      assertEquals(0.0, c.throttle);
      assertEquals(1.0, c.steer);
   }

   @Test
   void diagonalPressesBothAxes() {
      SeatInputDecoder d = decoder();
      long now = fly(d, 0L, -0.019, 0.0, -0.019, 5);
      SeatInputDecoder.Control c = d.evaluate(now);
      assertEquals(1.0, c.throttle);
      assertEquals(-1.0, c.steer);
   }

   @Test
   void idleStatesReleaseImmediately() {
      SeatInputDecoder d = decoder();
      d.onStates(false, false, false, 0L);
      long now = fly(d, 0L, 0.0, 0.0, -0.027, 5);
      d.onStates(true, false, false, now);
      assertEquals(0.0, d.evaluate(now).throttle);
   }

   @Test
   void idleFlagThatNeverChangesIsIgnored() {
      SeatInputDecoder d = decoder();
      d.onStates(true, false, false, 0L);
      long now = fly(d, 0L, 0.0, 0.0, -0.027, 5);
      d.onStates(true, false, false, now);
      assertEquals(1.0, d.evaluate(now).throttle);
   }

   @Test
   void crouchFlagCountsAsExitHold() {
      SeatInputDecoder d = decoder();
      d.onStates(true, false, true, 0L);
      d.onStates(true, false, true, 500_000_000L);
      double seconds = d.evaluate(800_000_000L).descendSeconds;
      assertEquals(0.8, seconds, 1.0E-9);
      d.onStates(true, false, false, 900_000_000L);
      assertEquals(0.0, d.evaluate(900_000_000L).descendSeconds);
   }

   @Test
   void clearVerticalForgetsHeldKeys() {
      SeatInputDecoder d = decoder();
      long now = fly(d, 0L, 0.0, -0.02, 0.0, 10);
      d.clearVertical();
      assertEquals(0.0, d.evaluate(now).descendSeconds);
      assertFalse(d.evaluate(now).handbrake);
   }

   @Test
   void lastDirectionIsHeldBriefly() {
      SeatInputDecoder d = decoder();
      long now = fly(d, 0L, 0.0, 0.0, -0.027, 5);
      assertEquals(1.0, d.evaluate(now + 200_000_000L).throttle);
      assertEquals(0.0, d.evaluate(now + 300_000_000L).throttle);
   }

   /**
    * Полёт с разгоном и торможением, как у клиента: скорость экспоненциально тянется к
    * скорости клавиши. Возвращает время последнего пакета.
    */
   private static long glide(SeatInputDecoder d, long start, double[] velocity, double targetX, double targetZ, int packets) {
      long now = start;

      for (int i = 0; i < packets; i++) {
         now += TICK;
         velocity[0] += (targetX - velocity[0]) * 0.35;
         velocity[1] += (targetZ - velocity[1]) * 0.35;
         d.onPosition(false, velocity[0], 0.0, velocity[1], false, now);
      }

      return now;
   }

   @Test
   void releaseIsNoticedWhileStillGliding() {
      SeatInputDecoder d = decoder();
      double[] velocity = new double[2];
      long now = glide(d, 0L, velocity, 0.0, -0.027, 20);
      assertEquals(1.0, d.evaluate(now).throttle);
      now = glide(d, now, velocity, 0.0, 0.0, 2);
      assertEquals(0.0, d.evaluate(now).throttle, "после отпускания газа кукла ещё скользит, но газа нет");
      assertEquals("coast", d.evaluate(now).source);
   }

   @Test
   void steeringReleasesQuicklyWhileThrottleHeld() {
      SeatInputDecoder d = decoder();
      double[] velocity = new double[2];
      long now = glide(d, 0L, velocity, 0.019, -0.019, 20);
      assertEquals(1.0, d.evaluate(now).steer);
      now = glide(d, now, velocity, 0.0, -0.027, 6);
      SeatInputDecoder.Control c = d.evaluate(now);
      assertEquals(0.0, c.steer);
      assertEquals(1.0, c.throttle);
   }

   @Test
   void steadyJitterDoesNotReleaseKeys() {
      SeatInputDecoder d = decoder();
      long now = 0L;

      for (int i = 0; i < 60; i++) {
         now += TICK;
         double step = 0.027 * (i % 2 == 0 ? 1.03 : 0.97);
         d.onPosition(false, 0.0, 0.0, -step, false, now);
         assertEquals(1.0, d.evaluate(now).throttle, "пакет " + i);
      }
   }

   @Test
   void oneStillPacketDoesNotReleaseButTwoDo() {
      SeatInputDecoder d = decoder();
      long now = fly(d, 0L, 0.0, 0.0, -0.027, 5);
      now += TICK;
      d.onPosition(false, 0.0, 0.0, 0.0, false, now);
      assertEquals(1.0, d.evaluate(now).throttle);
      now += TICK;
      d.onPosition(false, 0.0, 0.0, 0.0, false, now);
      assertEquals(0.0, d.evaluate(now).throttle);
      now = fly(d, now, 0.0, 0.0, -0.027, 1);
      assertEquals(1.0, d.evaluate(now).throttle, "снова нажали — снова газ");
   }

   @Test
   void jumpUpMeansHandbrake() {
      SeatInputDecoder d = decoder();
      long now = fly(d, 0L, 0.0, 0.02, 0.0, 3);
      SeatInputDecoder.Control c = d.evaluate(now);
      assertTrue(c.handbrake);
      assertEquals(0.0, c.throttle);
   }

   @Test
   void holdingCrouchCountsSeconds() {
      SeatInputDecoder d = decoder();
      long now = fly(d, 0L, 0.0, -0.02, 0.0, 25);
      double seconds = d.evaluate(now).descendSeconds;
      assertTrue(seconds > 0.7 && seconds < 0.9, "seconds=" + seconds);
      assertEquals(0.0, d.evaluate(now + 400_000_000L).descendSeconds);
   }

   @Test
   void teleportAckDoesNotCountAsMovement() {
      SeatInputDecoder d = decoder();
      d.onPosition(true, 100.0, 80.0, 100.0, true, TICK);
      d.onPosition(true, 100.0, 120.0, 100.0, true, 2 * TICK);
      assertFalse(d.evaluate(2 * TICK).active);
      assertTrue(d.hasPosition());
      assertEquals(120.0, d.y());
   }

   @Test
   void absolutePositionsAreDifferenced() {
      SeatInputDecoder d = decoder();
      d.onPosition(true, 0.0, 50.0, 0.0, true, TICK);
      d.onPosition(true, 0.0, 50.0, -0.03, false, 2 * TICK);
      assertEquals(1.0, d.evaluate(2 * TICK).throttle);
      d.onPosition(false, 0.03, 0.0, 0.0, false, 3 * TICK);
      assertEquals(0.03, d.x(), 1.0E-9);
      assertEquals(1.0, d.evaluate(3 * TICK).steer);
   }

   @Test
   void hugeJumpResetsInsteadOfSteering() {
      SeatInputDecoder d = decoder();
      d.onPosition(true, 0.0, 50.0, 0.0, true, TICK);
      d.onPosition(true, 40.0, 50.0, 0.0, false, 2 * TICK);
      assertFalse(d.evaluate(2 * TICK).active);
   }

   @Test
   void lookRelativeModeUsesYaw() {
      SeatInputDecoder.Settings settings = new SeatInputDecoder.Settings();
      settings.lookRelative = true;
      SeatInputDecoder d = new SeatInputDecoder(settings);
      d.onLook((float)(Math.PI / 2.0));
      long now = fly(d, 0L, -0.027, 0.0, 0.0, 5);
      SeatInputDecoder.Control c = d.evaluate(now);
      assertEquals(1.0, c.throttle);
      assertEquals(0.0, c.steer);
   }

   @Test
   void invertFlagsFlipAxes() {
      SeatInputDecoder.Settings settings = new SeatInputDecoder.Settings();
      settings.invertForward = true;
      settings.invertSteer = true;
      SeatInputDecoder d = new SeatInputDecoder(settings);
      long now = fly(d, 0L, 0.019, 0.0, -0.019, 5);
      SeatInputDecoder.Control c = d.evaluate(now);
      assertEquals(-1.0, c.throttle);
      assertEquals(-1.0, c.steer);
   }

   @Test
   void wishMovementWinsWhenPresent() {
      SeatInputDecoder d = decoder();
      long now = fly(d, 0L, 0.0, 0.0, -0.027, 5);
      d.onWish(1.0, 0.0, now);
      SeatInputDecoder.Control c = d.evaluate(now);
      assertEquals("wish", c.source);
      assertEquals(1.0, c.steer);
      assertEquals(0.0, c.throttle);
   }
}
