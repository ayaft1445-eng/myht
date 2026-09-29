package beepbeep.hytale;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class VehiclePhysicsComponent implements Component<EntityStore> {
   public long ticks;
   public long inputEvents;
   public long wishEvents;
   public double elapsed;
   public double wishX;
   public double wishZ;
   public String lastInput = "none";
   public double lastInputTime = -1.0;
   public boolean animatedProbe;
   public double baseY;

   public VehiclePhysicsComponent clone() {
      VehiclePhysicsComponent var1 = new VehiclePhysicsComponent();
      var1.ticks = this.ticks;
      var1.inputEvents = this.inputEvents;
      var1.wishEvents = this.wishEvents;
      var1.elapsed = this.elapsed;
      var1.wishX = this.wishX;
      var1.wishZ = this.wishZ;
      var1.lastInput = this.lastInput;
      var1.lastInputTime = this.lastInputTime;
      var1.animatedProbe = this.animatedProbe;
      var1.baseY = this.baseY;
      return var1;
   }
}
