package beepbeep.hytale;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Места машины из профиля: сколько их, где стоят и какое из них водительское.
 *
 * Координаты заданы в системе машины так же, как в профиле: x — вправо,
 * y — вверх, z — вперёд. Позиция — точка, куда встают ноги сидящего игрока.
 */
public final class SeatLayout {
   public static final class Seat {
      public final String id;
      public final double x;
      public final double y;
      public final double z;
      /** Поворот места относительно машины, радианы. */
      public final double yaw;
      public final boolean driver;

      public Seat(String id, double x, double y, double z, double yaw, boolean driver) {
         this.id = id;
         this.x = x;
         this.y = y;
         this.z = z;
         this.yaw = yaw;
         this.driver = driver;
      }
   }

   private final Seat[] seats;
   private final int driverIndex;

   private SeatLayout(Seat[] seats) {
      if (seats.length == 0) {
         throw new IllegalArgumentException("Нужно хотя бы одно место");
      }

      this.seats = seats;
      int driver = 0;

      for (int i = 0; i < seats.length; i++) {
         if (seats[i].driver) {
            driver = i;
            break;
         }
      }

      this.driverIndex = driver;
   }

   public static SeatLayout single(double x, double y, double z) {
      return new SeatLayout(new Seat[]{new Seat("driver", x, y, z, 0.0, true)});
   }

   public static SeatLayout of(List<Seat> seats) {
      return new SeatLayout(seats.toArray(new Seat[0]));
   }

   /** Читает массив "seats" профиля. Профиль уже проверен VehicleProfiles.validate. */
   public static SeatLayout fromProfile(JsonObject profile) {
      JsonArray array = profile.getAsJsonArray("seats");
      List<Seat> seats = new ArrayList<>();

      for (JsonElement element : array) {
         JsonObject seat = element.getAsJsonObject();
         JsonObject position = seat.getAsJsonObject("position");
         seats.add(
            new Seat(
               seat.get("id").getAsString(),
               position.get("x").getAsDouble(),
               position.get("y").getAsDouble(),
               position.get("z").getAsDouble(),
               Math.toRadians(seat.has("yaw") ? seat.get("yaw").getAsDouble() : 0.0),
               seat.get("driver").getAsBoolean()
            )
         );
      }

      return of(seats);
   }

   public int size() {
      return this.seats.length;
   }

   public Seat get(int index) {
      return this.seats[index];
   }

   public int driverIndex() {
      return this.driverIndex;
   }

   /**
    * Ближайшее к точке свободное место. Точка — в системе машины (x вправо, z вперёд).
    * Возвращает -1, если всё занято.
    */
   public int nearestFree(double localX, double localY, double localZ, boolean[] occupied) {
      int best = -1;
      double bestDistance = Double.POSITIVE_INFINITY;

      for (int i = 0; i < this.seats.length; i++) {
         if (i < occupied.length && occupied[i]) {
            continue;
         }

         Seat seat = this.seats[i];
         double dx = seat.x - localX;
         double dy = (seat.y - localY) * 0.25;
         double dz = seat.z - localZ;
         double distance = dx * dx + dy * dy + dz * dz;
         if (distance < bestDistance) {
            bestDistance = distance;
            best = i;
         }
      }

      return best;
   }

   /** Сторона выхода: -1 — слева по ходу, +1 — справа. */
   public int exitSide(int index) {
      return this.seats[index].x < 0.0 ? -1 : 1;
   }
}
