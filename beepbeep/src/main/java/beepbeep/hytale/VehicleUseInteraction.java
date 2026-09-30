package beepbeep.hytale;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Клавиша «использовать» (F) по машине. Устроено как ванильный UseNPCInteraction:
 * взаимодействие и корневое взаимодействие регистрируются кодом при запуске плагина.
 */
public final class VehicleUseInteraction extends SimpleInstantInteraction {
   public static final String ID = "BeepBeep_VehicleUse";
   public static final String TYPE = "BeepBeepVehicleUse";
   public static final BuilderCodec<VehicleUseInteraction> CODEC = BuilderCodec.builder(
         VehicleUseInteraction.class, VehicleUseInteraction::new, SimpleInstantInteraction.CODEC
      )
      .documentation("Enter or leave a BeepBeep vehicle.")
      .build();
   public static final RootInteraction ROOT = new RootInteraction(ID, ID);
   private static volatile boolean registered;

   public VehicleUseInteraction(String id) {
      super(id);
   }

   protected VehicleUseInteraction() {
   }

   static boolean registered() {
      return registered;
   }

   static void markRegistered() {
      registered = true;
   }

   @Override
   protected void firstRun(InteractionType type, InteractionContext context, CooldownHandler cooldownHandler) {
      Ref<EntityStore> player = context.getEntity();
      Ref<EntityStore> target = context.getTargetEntity();
      CommandBuffer<EntityStore> buffer = context.getCommandBuffer();
      if (player == null || target == null || buffer.getComponent(player, PlayerRef.getComponentType()) == null) {
         context.getState().state = InteractionState.Failed;
      } else if (buffer.getComponent(target, VehicleSeats.vehicleType) == null) {
         context.getState().state = InteractionState.Failed;
      } else {
         buffer.run(store -> VehicleSeats.use(store, player, target));
      }
   }

   @Override
   protected void simulateFirstRun(InteractionType type, InteractionContext context, CooldownHandler cooldownHandler) {
   }

   @Override
   public String toString() {
      return "VehicleUseInteraction{} " + super.toString();
   }
}
