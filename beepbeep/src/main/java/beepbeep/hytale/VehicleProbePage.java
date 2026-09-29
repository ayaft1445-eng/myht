package beepbeep.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class VehicleProbePage extends CustomUIPage {
   public VehicleProbePage(PlayerRef var1) {
      super(var1, CustomPageLifetime.CanDismiss);
   }

   public void build(Ref<EntityStore> var1, UICommandBuilder var2, UIEventBuilder var3, Store<EntityStore> var4) {
      var2.append("Pages/BeepBeepProbe.ui");
   }
}
