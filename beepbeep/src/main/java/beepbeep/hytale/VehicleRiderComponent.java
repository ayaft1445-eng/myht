package beepbeep.hytale;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.FlyMode;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashSet;
import java.util.Set;
import org.joml.Vector3d;

/**
 * Висит на игроке, пока он сидит в машине. Не сохраняется: после перезахода игрок
 * оказывается рядом с машиной, а не в ней.
 */
public final class VehicleRiderComponent implements Component<EntityStore> {
   /** То, что мы поменяли в настройках движения игрока, чтобы вернуть при высадке. */
   static final class SavedMovement {
      final MovementSettings settings;
      final FlyMode fly;
      final float horizontalFlySpeed;
      final float verticalFlySpeed;

      SavedMovement(MovementSettings settings) {
         this.settings = settings;
         this.fly = settings.fly;
         this.horizontalFlySpeed = settings.horizontalFlySpeed;
         this.verticalFlySpeed = settings.verticalFlySpeed;
      }

      void restoreInto(MovementSettings target) {
         target.fly = this.fly;
         target.horizontalFlySpeed = this.horizontalFlySpeed;
         target.verticalFlySpeed = this.verticalFlySpeed;
      }
   }

   public Ref<EntityStore> vehicle;
   public int seat = -1;
   public Ref<EntityStore> anchor;
   public int anchorNetworkId;
   /** Двойник, которого видит только сам седок (см. SeatDummyComponent). */
   public Ref<EntityStore> dummy;
   public int vehicleNetworkId;
   public PlayerRef playerRef;
   public SeatInput input;
   SeatCamera.View view = SeatCamera.View.CHASE;
   SavedMovement savedMovement;
   /** Кому из зрителей уже отправлено «седок пристёгнут к точке сиденья». */
   public final Set<Ref<EntityStore>> attachedViewers = new HashSet<>();
   public final Set<Ref<EntityStore>> dummyAttachedViewers = new HashSet<>();
   public boolean attachDirty = true;
   public final Vector3d puppetTarget = new Vector3d();
   public long enteredAt;
   public long lastRecenterAt;
   public long recenters;
   public float cameraYaw = Float.NaN;
   public float cameraPitch = Float.NaN;
   public float cameraRoll = Float.NaN;
   public long cameraSentAt;
   public long cameraPackets;
   public boolean releasing;

   public VehicleRiderComponent() {
   }

   public boolean driver(VehicleRuntimeComponent runtime) {
      return this.seat >= 0 && this.seat == runtime.seatLayout.driverIndex();
   }

   @Override
   public VehicleRiderComponent clone() {
      VehicleRiderComponent copy = new VehicleRiderComponent();
      copy.vehicle = this.vehicle;
      copy.seat = this.seat;
      copy.anchor = this.anchor;
      copy.anchorNetworkId = this.anchorNetworkId;
      copy.dummy = this.dummy;
      copy.vehicleNetworkId = this.vehicleNetworkId;
      copy.playerRef = this.playerRef;
      copy.input = this.input;
      copy.view = this.view;
      copy.savedMovement = this.savedMovement;
      copy.attachDirty = true;
      copy.puppetTarget.set(this.puppetTarget);
      copy.enteredAt = this.enteredAt;
      copy.lastRecenterAt = this.lastRecenterAt;
      copy.recenters = this.recenters;
      copy.releasing = this.releasing;
      return copy;
   }
}
