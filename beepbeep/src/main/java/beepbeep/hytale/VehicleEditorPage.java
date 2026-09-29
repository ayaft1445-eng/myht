package beepbeep.hytale;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class VehicleEditorPage extends CustomUIPage {
   private final VehicleEditorSession session;
   private final Ref<EntityStore> vehicle;
   private final ComponentType<EntityStore, VehicleRuntimeComponent> runtimeType;
   private int generation;

   public VehicleEditorPage(PlayerRef var1, VehicleProfiles var2, Ref<EntityStore> var3, ComponentType<EntityStore, VehicleRuntimeComponent> var4) throws IOException {
      super(var1, CustomPageLifetime.CanDismiss);
      this.session = new VehicleEditorSession(var2);
      this.vehicle = var3;
      this.runtimeType = var4;
   }

   public void build(Ref<EntityStore> var1, UICommandBuilder var2, UIEventBuilder var3, Store<EntityStore> var4) {
      this.generation++;
      var2.append("Pages/BeepBeepEditor.ui");
      var2.set("#Location.Text", this.session.location());
      var2.set("#Target.Text", "Привязано к ближайшей машине: изменения вступят в силу после «Сохранить».");
      var2.set("#Status.Text", this.session.status());
      List var5 = this.session.fields();

      for (int var6 = 0; var6 < var5.size(); var6++) {
         VehicleEditorSession.Field var7 = (VehicleEditorSession.Field)var5.get(var6);
         var2.append("#Fields", "Pages/BeepBeepEditorRow.ui");
         var2.set("#Fields[" + var6 + "] #FieldLabel.Text", var7.label());
         var2.set("#Fields[" + var6 + "] #FieldInput.Value", this.session.value(var7));
      }

      for (String var11 : List.of("body", "wheels", "engine", "seats", "prev", "next", "add", "remove", "save")) {
         EventData var8 = EventData.of("Action", var11).append("Generation", Integer.toString(this.generation));

         for (int var9 = 0; var9 < var5.size(); var9++) {
            var8.append("@F" + var9, "#Fields[" + var9 + "] #FieldInput.Value");
         }

         var3.addEventBinding(CustomUIEventBindingType.Activating, "#" + var11, var8);
      }
   }

   public void handleDataEvent(Ref<EntityStore> var1, Store<EntityStore> var2, String var3) {
      if (!this.playerRef.hasPermission("beepbeep.vehicle.admin")) {
         this.close();
      } else {
         try {
            if (var3 == null || var3.length() > 16384) {
               throw new IllegalArgumentException("Слишком большой запрос формы");
            }

            JsonObject var4 = JsonParser.parseString(var3).getAsJsonObject();
            if (var4.get("Generation").getAsInt() != this.generation) {
               this.sendUpdate();
               return;
            }

            ArrayList var11 = new ArrayList();

            for (int var6 = 0; var6 < this.session.fields().size(); var6++) {
               JsonElement var7 = var4.get("@F" + var6);
               if (var7 == null || !var7.isJsonPrimitive() || !var7.getAsJsonPrimitive().isString()) {
                  throw new IllegalArgumentException("Неполные данные формы");
               }

               String var8 = var7.getAsString();
               if (var8.length() > 1024) {
                  throw new IllegalArgumentException("Слишком длинное поле");
               }

               var11.add(var8);
            }

            boolean var12 = this.session.accept(var4.get("Action").getAsString(), var11);
            if (var12) {
               VehicleProfileApplier.apply(var2, this.vehicle, this.runtimeType, this.session.draft());
               this.session.setStatus("Сохранено и применено к этой машине.");
            }

            this.rebuild();
         } catch (IOException var9) {
            this.sendUpdate(new UICommandBuilder().set("#Status.Text", "Не удалось записать профиль. Проверьте доступ к папке данных плагина."));
         } catch (RuntimeException var10) {
            String var5 = var10 instanceof IllegalArgumentException ? var10.getMessage() : "Некорректные данные формы";
            this.sendUpdate(new UICommandBuilder().set("#Status.Text", var5 == null ? "Ошибка данных" : var5));
         }
      }
   }
}
