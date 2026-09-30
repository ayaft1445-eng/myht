package beepbeep.hytale;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Метка «двойника» седока: копия его модели, сидящая на месте. Видна только самому
 * седоку (его настоящий персонаж висит над машиной), остальные видят настоящего.
 */
public final class SeatDummyComponent implements Component<EntityStore> {
   public Ref<EntityStore> owner;

   public SeatDummyComponent() {
   }

   public SeatDummyComponent(Ref<EntityStore> owner) {
      this.owner = owner;
   }

   @Override
   public SeatDummyComponent clone() {
      return new SeatDummyComponent(this.owner);
   }
}
