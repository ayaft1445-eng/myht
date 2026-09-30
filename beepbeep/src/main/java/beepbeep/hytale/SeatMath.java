package beepbeep.hytale;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Перевод координат между системой машины и миром.
 *
 * Та же договорённость, что у подвески: поворот машины — rotationYXZ(yaw, pitch, roll),
 * «вперёд» профиля (+z) в мире при yaw = 0 смотрит в -Z.
 */
public final class SeatMath {
   private SeatMath() {
   }

   public static Quaterniond rotation(double yaw, double pitch, double roll) {
      return new Quaterniond().rotationYXZ(yaw, pitch, roll);
   }

   /** Мировая точка для точки профиля (x вправо, y вверх, z вперёд). */
   public static Vector3d toWorld(Vector3dc origin, double yaw, double pitch, double roll, double x, double y, double z) {
      return rotation(yaw, pitch, roll).transform(new Vector3d(x, y, -z)).add(origin);
   }

   /** Точка мира в системе профиля машины (x вправо, y вверх, z вперёд). */
   public static Vector3d toLocal(Vector3dc origin, double yaw, double pitch, double roll, Vector3dc world) {
      Vector3d local = rotation(yaw, pitch, roll).transformInverse(new Vector3d(world).sub(origin));
      local.z = -local.z;
      return local;
   }

   /** Единичный вектор «вперёд» машины по горизонтали. */
   public static double forwardX(double yaw) {
      return -Math.sin(yaw);
   }

   public static double forwardZ(double yaw) {
      return -Math.cos(yaw);
   }

   /** Приводит угол к диапазону (-PI, PI]. */
   public static double wrap(double angle) {
      return Math.atan2(Math.sin(angle), Math.cos(angle));
   }
}
