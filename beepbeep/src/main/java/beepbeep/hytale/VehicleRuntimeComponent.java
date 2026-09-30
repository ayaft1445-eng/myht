package beepbeep.hytale;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;

public final class VehicleRuntimeComponent implements Component<EntityStore> {
   public double mass = 1450.0;
   public double verticalVelocity;
   public double pitch;
   public double roll;
   public double yaw;
   public double halfX = 0.95;
   public double halfY = 0.55;
   public double halfZ = 2.05;
   public double bodyX;
   public double bodyY = 0.63;
   public double bodyZ;
   public double pitchVelocity;
   public double rollVelocity;
   public double accumulator;
   public String terrainState = "new";
   public double velocityForward;
   public double velocityLateral;
   public double throttle;
   public double steer;
   public double brake;
   public double idleRpm = 850.0;
   public double redlineRpm = 6500.0;
   public double peakTorqueNm = 320.0;
   public double reverseRatio = 3.2;
   public double finalDrive = 3.7;
   public double[] gearRatios = new double[]{3.4, 2.1, 1.45, 1.0, 0.78};
   public double shiftUpRpm = 6100.0;
   public double shiftDownRpm = 1800.0;
   public double maxBrakeForce = 12000.0;
   public double handbrakeForce = 9000.0;
   public int gear = 1;
   public double engineRpm = 850.0;
   public double engineForce;
   public boolean driven;
   public boolean bodySupported;
   public double seatX = -0.66;
   public double seatY = 1.4;
   public double seatZ = 0.3;
   /** Места из профиля. Водительское управляет машиной, остальные — пассажирские. */
   public SeatLayout seatLayout = SeatLayout.single(-0.66, 1.4, 0.3);
   private Ref<EntityStore>[] occupants = newOccupants(1);
   public double driverDistance = Double.POSITIVE_INFINITY;
   /** Игрок, управляющий машиной снаружи (/vehicle drive), не путать с водителем на сиденье. */
   public Ref<EntityStore> driver;
   public VehicleDebug debug;
   public double[] debugCompression = new double[4];
   public double[] debugLoad = new double[4];
   public long inputEvents;
   public long wishEvents;
   public long positionEvents;
   public double inputAge = 1.0;
   public String inputSource = "off";
   public long ticks;
   public double authoritativeX;
   public double authoritativeY;
   public double authoritativeZ;
   public double authoritativeYaw;
   public String profileId = "beepbeep_suv";
   public double[] wheelX = new double[]{-0.82, 0.82, -0.82, 0.82};
   public double[] wheelY = new double[]{0.42, 0.42, 0.42, 0.42};
   public double[] wheelZ = new double[]{1.25, 1.25, -1.25, -1.25};
   public double[] radius = new double[]{0.38, 0.38, 0.38, 0.38};
   public double[] rest = new double[]{0.48, 0.48, 0.48, 0.48};
   public double[] travel = new double[]{0.3, 0.3, 0.3, 0.3};
   public double[] spring = new double[]{42000.0, 42000.0, 44000.0, 44000.0};
   public double[] damping = new double[]{5400.0, 5400.0, 5600.0, 5600.0};
   public double[] rebound = new double[]{6200.0, 6200.0, 6400.0, 6400.0};
   public double[] contactY = new double[]{Double.NaN, Double.NaN, Double.NaN, Double.NaN};
   public boolean[] contact = new boolean[]{false, false, false, false};

   @SuppressWarnings("unchecked")
   private static Ref<EntityStore>[] newOccupants(int size) {
      return (Ref<EntityStore>[])new Ref[size];
   }

   /** Подгоняет массив седоков под раскладку мест. Седоки на пропавших местах возвращаются. */
   public List<Ref<EntityStore>> fitOccupants() {
      List<Ref<EntityStore>> dropped = new ArrayList<>();
      int size = this.seatLayout.size();
      if (this.occupants.length != size) {
         Ref<EntityStore>[] resized = newOccupants(size);

         for (int i = 0; i < this.occupants.length; i++) {
            if (i < size) {
               resized[i] = this.occupants[i];
            } else if (this.occupants[i] != null) {
               dropped.add(this.occupants[i]);
            }
         }

         this.occupants = resized;
      }

      return dropped;
   }

   public int seatCount() {
      return this.seatLayout.size();
   }

   public Ref<EntityStore> occupant(int seat) {
      this.fitOccupants();
      return seat >= 0 && seat < this.occupants.length ? this.occupants[seat] : null;
   }

   public void setOccupant(int seat, Ref<EntityStore> rider) {
      this.fitOccupants();
      if (seat >= 0 && seat < this.occupants.length) {
         this.occupants[seat] = rider;
      }
   }

   /** Освобождает место, если на нём сидит именно этот игрок. */
   public void clearOccupant(int seat, Ref<EntityStore> rider) {
      if (seat >= 0 && seat < this.occupants.length && this.occupants[seat] == rider) {
         this.occupants[seat] = null;
      }
   }

   public int seatOf(Ref<EntityStore> rider) {
      if (rider != null) {
         for (int i = 0; i < this.occupants.length; i++) {
            if (this.occupants[i] == rider) {
               return i;
            }
         }
      }

      return -1;
   }

   public boolean[] occupiedMask() {
      this.fitOccupants();
      boolean[] mask = new boolean[this.occupants.length];

      for (int i = 0; i < this.occupants.length; i++) {
         mask[i] = this.occupants[i] != null && this.occupants[i].isValid();
      }

      return mask;
   }

   public int occupiedCount() {
      int count = 0;

      for (boolean occupied : this.occupiedMask()) {
         if (occupied) {
            count++;
         }
      }

      return count;
   }

   /** Копия списка седоков (без пустых мест). */
   public List<Ref<EntityStore>> riders() {
      List<Ref<EntityStore>> list = new ArrayList<>();

      for (Ref<EntityStore> rider : this.occupants) {
         if (rider != null) {
            list.add(rider);
         }
      }

      return list;
   }

   public VehicleRuntimeComponent clone() {
      VehicleRuntimeComponent var1 = new VehicleRuntimeComponent();
      var1.bodyX = this.bodyX;
      var1.bodyY = this.bodyY;
      var1.bodyZ = this.bodyZ;
      var1.bodySupported = this.bodySupported;
      var1.seatX = this.seatX;
      var1.seatY = this.seatY;
      var1.seatZ = this.seatZ;
      var1.seatLayout = this.seatLayout;
      var1.occupants = newOccupants(this.seatLayout.size());
      var1.mass = this.mass;
      var1.verticalVelocity = this.verticalVelocity;
      var1.pitch = this.pitch;
      var1.roll = this.roll;
      var1.yaw = this.yaw;
      var1.halfX = this.halfX;
      var1.halfY = this.halfY;
      var1.halfZ = this.halfZ;
      var1.pitchVelocity = this.pitchVelocity;
      var1.rollVelocity = this.rollVelocity;
      var1.accumulator = this.accumulator;
      var1.terrainState = this.terrainState;
      var1.velocityForward = this.velocityForward;
      var1.velocityLateral = this.velocityLateral;
      var1.throttle = this.throttle;
      var1.steer = this.steer;
      var1.brake = this.brake;
      var1.driven = this.driven;
      var1.idleRpm = this.idleRpm;
      var1.redlineRpm = this.redlineRpm;
      var1.peakTorqueNm = this.peakTorqueNm;
      var1.reverseRatio = this.reverseRatio;
      var1.finalDrive = this.finalDrive;
      var1.gearRatios = (double[])this.gearRatios.clone();
      var1.shiftUpRpm = this.shiftUpRpm;
      var1.shiftDownRpm = this.shiftDownRpm;
      var1.maxBrakeForce = this.maxBrakeForce;
      var1.handbrakeForce = this.handbrakeForce;
      var1.gear = this.gear;
      var1.engineRpm = this.engineRpm;
      var1.engineForce = this.engineForce;
      var1.driverDistance = this.driverDistance;
      var1.driver = this.driver;
      var1.inputEvents = this.inputEvents;
      var1.wishEvents = this.wishEvents;
      var1.positionEvents = this.positionEvents;
      var1.inputAge = this.inputAge;
      var1.inputSource = this.inputSource;
      var1.ticks = this.ticks;
      var1.profileId = this.profileId;
      var1.authoritativeX = this.authoritativeX;
      var1.authoritativeY = this.authoritativeY;
      var1.authoritativeZ = this.authoritativeZ;
      var1.authoritativeYaw = this.authoritativeYaw;
      var1.wheelX = (double[])this.wheelX.clone();
      var1.wheelY = (double[])this.wheelY.clone();
      var1.wheelZ = (double[])this.wheelZ.clone();
      var1.radius = (double[])this.radius.clone();
      var1.rest = (double[])this.rest.clone();
      var1.travel = (double[])this.travel.clone();
      var1.spring = (double[])this.spring.clone();
      var1.damping = (double[])this.damping.clone();
      var1.contactY = (double[])this.contactY.clone();
      var1.contact = (boolean[])this.contact.clone();
      var1.rebound = (double[])this.rebound.clone();
      return var1;
   }
}
