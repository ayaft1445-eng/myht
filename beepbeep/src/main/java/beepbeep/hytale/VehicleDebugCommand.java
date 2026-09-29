package beepbeep.hytale;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.nio.file.Path;
import org.joml.Vector3d;

final class VehicleDebugCommand extends AbstractPlayerCommand {
   private final ComponentType<EntityStore, VehicleRuntimeComponent> type;
   private final Path folder;
   private final RequiredArg<String> action;

   VehicleDebugCommand(ComponentType<EntityStore, VehicleRuntimeComponent> var1, Path var2) {
      super("debug", "Record vehicle diagnostics: start/stop/status/mark");
      this.type = var1;
      this.folder = var2;
      this.requirePermission("beepbeep.vehicle.admin");
      this.action = this.withRequiredArg("action", "start/stop/status/mark", ArgTypes.STRING);
   }

   protected void execute(CommandContext var1, Store<EntityStore> var2, Ref<EntityStore> var3, PlayerRef var4, World var5) {
      Vector3d var6 = ((TransformComponent)var2.getComponent(var3, TransformComponent.getComponentType())).getPosition();
      VehicleRuntimeComponent[] var7 = new VehicleRuntimeComponent[]{null};
      double[] var8 = new double[]{Double.POSITIVE_INFINITY};
      var2.forEachChunk(this.type, (var5x, var6x) -> {
         for (int var7x = 0; var7x < var5x.size(); var7x++) {
            VehicleRuntimeComponent var8x = (VehicleRuntimeComponent)var5x.getComponent(var7x, this.type);
            double var9x = ((TransformComponent)var5x.getComponent(var7x, TransformComponent.getComponentType())).getPosition().distance(var6);
            if (var8x.driver == var3) {
               var9x = -2.0;
            } else if (var8x.debug != null && var8x.debug.observer == var3 && var8x.debug.trace.active()) {
               var9x = -1.0;
            }

            if (var9x < var8[0]) {
               var7[0] = var8x;
               var8[0] = var9x;
            }
         }
      });
      VehicleRuntimeComponent var9 = var7[0];
      if (var9 == null || var8[0] > 24.0) {
         var1.sendMessage(Message.raw("Для диагностики подойдите к машине или займите сиденье."));
      } else if (var9.driver != null && var9.driver.isValid() && var9.driver != var3) {
         var1.sendMessage(Message.raw("Диагностика доступна водителю этой машины."));
      } else {
         String var10 = (String)this.action.get(var1);
         switch (var10) {
            case "start":
               if (var9.debug != null && var9.debug.trace.active()) {
                  var1.sendMessage(Message.raw(var9.debug.trace.status()));
                  return;
               }

               var9.debug = new VehicleDebug(this.folder, var3);
               if (var9.mountInput != null) {
                  var9.mountInput.debug = var9.debug;
               }

               var1.sendMessage(
                  Message.raw("Запись диагностики: 90 секунд, без изменения управления. /vehicle debug stop — сохранить. Файл: " + var9.debug.trace.path)
               );
               break;
            case "stop":
               if (var9.debug != null) {
                  var9.debug.trace.stop("user-stop");
                  var1.sendMessage(Message.raw(var9.debug.trace.status()));
               } else {
                  this.off(var1);
               }
               break;
            case "status":
               if (var9.debug != null) {
                  var1.sendMessage(Message.raw(var9.debug.trace.status()));
               } else {
                  this.off(var1);
               }
               break;
            case "mark":
               if (var9.debug != null && var9.debug.trace.active()) {
                  var9.debug.trace.event("marker", "manual-marker");
                  var1.sendMessage(Message.raw("Метка записана. Открытие чата само влияет на ввод."));
               } else {
                  this.off(var1);
               }
               break;
            default:
               var1.sendMessage(Message.raw("/vehicle debug start | stop | status | mark"));
         }
      }
   }

   private void off(CommandContext var1) {
      var1.sendMessage(Message.raw("Сначала /vehicle debug start."));
   }
}
