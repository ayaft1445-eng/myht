package beepbeep.hytale;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSystems.ProcessPlayerInput;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;

/**
 * Каждый тик до обработки ввода игроков: высадка по кнопке, смена вида, возврат
 * «куклы» к машине и чистка очереди движения седока, чтобы сервер не двигал его сам.
 */
public final class SeatInputSystem extends EntityTickingSystem<EntityStore> {
   private final ComponentType<EntityStore, VehicleRiderComponent> type;

   public SeatInputSystem(ComponentType<EntityStore, VehicleRiderComponent> type) {
      this.type = type;
   }

   @Override
   public Query<EntityStore> getQuery() {
      return Query.and(new Query[]{this.type, Player.getComponentType()});
   }

   @Override
   public Set<Dependency<EntityStore>> getDependencies() {
      return Set.of(new SystemDependency(Order.BEFORE, ProcessPlayerInput.class), new SystemDependency(Order.BEFORE, VehiclePhysicsSystem.class));
   }

   @Override
   public boolean isParallel(int archetypeChunkSize, int taskCount) {
      return false;
   }

   @Override
   public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
      VehicleRiderComponent rider = chunk.getComponent(index, this.type);
      if (rider != null) {
         VehicleSeats.tickRider(chunk.getReferenceTo(index), rider, store, buffer, System.nanoTime());
      }
   }
}
