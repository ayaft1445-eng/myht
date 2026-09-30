package beepbeep.hytale;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.EnumMap;
import java.util.Map;

/** Каждой машине — клавиша «использовать» (F): сесть или выйти. */
public final class VehicleInteractableSystem extends HolderSystem<EntityStore> {
   private final ComponentType<EntityStore, VehicleRuntimeComponent> type;

   public VehicleInteractableSystem(ComponentType<EntityStore, VehicleRuntimeComponent> type) {
      this.type = type;
   }

   @Override
   public Query<EntityStore> getQuery() {
      return this.type;
   }

   @Override
   public void onEntityAdd(Holder<EntityStore> holder, AddReason reason, Store<EntityStore> store) {
      if (!VehicleUseInteraction.registered()) {
         return;
      }

      holder.putComponent(Interactable.getComponentType(), Interactable.INSTANCE);
      Interactions interactions = holder.getComponent(Interactions.getComponentType());
      if (interactions == null) {
         Map<InteractionType, String> map = new EnumMap<>(InteractionType.class);
         map.put(InteractionType.Use, VehicleUseInteraction.ID);
         holder.addComponent(Interactions.getComponentType(), new Interactions(map));
      } else if (interactions.getInteractionId(InteractionType.Use) == null) {
         interactions.setInteractionId(InteractionType.Use, VehicleUseInteraction.ID);
      }
   }

   @Override
   public void onEntityRemoved(Holder<EntityStore> holder, RemoveReason reason, Store<EntityStore> store) {
   }
}
