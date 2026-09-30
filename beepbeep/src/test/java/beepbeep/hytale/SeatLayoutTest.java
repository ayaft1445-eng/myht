package beepbeep.hytale;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonParser;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class SeatLayoutTest {
   private static final String PROFILE_SEATS = "{\"seats\":["
      + "{\"id\":\"driver\",\"position\":{\"x\":-0.45,\"y\":1.05,\"z\":0.55},\"yaw\":0,\"driver\":true},"
      + "{\"id\":\"front\",\"position\":{\"x\":0.45,\"y\":1.05,\"z\":0.55},\"yaw\":0,\"driver\":false},"
      + "{\"id\":\"rear_left\",\"position\":{\"x\":-0.45,\"y\":1.05,\"z\":-0.65},\"yaw\":180,\"driver\":false},"
      + "{\"id\":\"rear_right\",\"position\":{\"x\":0.45,\"y\":1.05,\"z\":-0.65},\"yaw\":0,\"driver\":false}]}";

   @Test
   void readsAllSeatsFromProfile() {
      SeatLayout layout = SeatLayout.fromProfile(JsonParser.parseString(PROFILE_SEATS).getAsJsonObject());
      assertEquals(4, layout.size());
      assertEquals(0, layout.driverIndex());
      assertEquals(Math.PI, layout.get(2).yaw, 1.0E-9);
      assertEquals(-1, layout.exitSide(0));
      assertEquals(1, layout.exitSide(1));
   }

   @Test
   void nearestFreeSkipsOccupiedSeats() {
      SeatLayout layout = SeatLayout.fromProfile(JsonParser.parseString(PROFILE_SEATS).getAsJsonObject());
      boolean[] occupied = new boolean[4];
      assertEquals(0, layout.nearestFree(-1.5, 1.0, 0.6, occupied));
      occupied[0] = true;
      assertEquals(2, layout.nearestFree(-1.5, 1.0, 0.0, occupied));
      assertEquals(1, layout.nearestFree(1.5, 1.0, 0.6, occupied));
      occupied[1] = occupied[2] = occupied[3] = true;
      assertEquals(-1, layout.nearestFree(0.0, 0.0, 0.0, occupied));
   }

   @Test
   void seatMathRoundTripsThroughWorld() {
      Vector3d origin = new Vector3d(100.0, 64.0, -20.0);
      double yaw = 0.7;
      double pitch = 0.12;
      double roll = -0.08;
      Vector3d world = SeatMath.toWorld(origin, yaw, pitch, roll, -0.45, 1.05, 0.55);
      Vector3d local = SeatMath.toLocal(origin, yaw, pitch, roll, world);
      assertEquals(-0.45, local.x, 1.0E-9);
      assertEquals(1.05, local.y, 1.0E-9);
      assertEquals(0.55, local.z, 1.0E-9);
   }

   @Test
   void forwardSeatIsInFrontAtYawZero() {
      Vector3d world = SeatMath.toWorld(new Vector3d(), 0.0, 0.0, 0.0, 0.0, 0.0, 1.0);
      assertEquals(-1.0, world.z, 1.0E-9);
      assertEquals(SeatMath.forwardZ(0.0), world.z, 1.0E-9);
      Vector3d right = SeatMath.toWorld(new Vector3d(), 0.0, 0.0, 0.0, 1.0, 0.0, 0.0);
      assertEquals(1.0, right.x, 1.0E-9);
   }
}
