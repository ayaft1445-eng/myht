package beepbeep.hytale;

import com.hypixel.hytale.protocol.ToServerPacket;
import com.hypixel.hytale.protocol.packets.player.ClientMovement;
import com.hypixel.hytale.server.core.io.handlers.GenericPacketHandler;
import java.lang.reflect.Field;
import java.util.function.Consumer;

final class VehicleConnectionInput implements AutoCloseable {
   private static final int[] IDS = new int[]{108, 166, 294, 111};
   private static final Field HANDLERS;
   private final GenericPacketHandler handler;
   private final Consumer<ToServerPacket>[] previous;
   private final Consumer<ToServerPacket>[] wrappers;

   private VehicleConnectionInput(GenericPacketHandler var1, VehicleMountInput.Session var2) {
      this.handler = var1;
      this.previous = GenericPacketHandler.newHandlerArray(IDS.length);
      this.wrappers = GenericPacketHandler.newHandlerArray(IDS.length);
      Consumer[] var3 = handlers(var1);

      for (int var4 = 0; var4 < IDS.length; var4++) {
         int var5 = IDS[var4];
         if (var5 >= var3.length || var3[var5] == null) {
            throw new IllegalStateException("Missing vehicle input handler: " + var5);
         }

         this.previous[var4] = var3[var5];
         Consumer var6 = this.previous[var4];
         this.wrappers[var4] = var2x -> {
            boolean var3x = var2.accept(var2x);
            VehicleDebug.packet(var2, var2x, var3x);
            if (!var3x) {
               var6.accept(var2x instanceof ClientMovement var4x ? var2.forNative(var4x) : var2x);
            }
         };
      }

      for (int var7 = 0; var7 < IDS.length; var7++) {
         var1.registerHandler(IDS[var7], this.wrappers[var7]);
      }
   }

   private static Consumer<ToServerPacket>[] handlers(GenericPacketHandler var0) {
      try {
         return (Consumer<ToServerPacket>[])HANDLERS.get(var0);
      } catch (IllegalAccessException var2) {
         throw new IllegalStateException("Cannot attach vehicle input", var2);
      }
   }

   static VehicleConnectionInput install(GenericPacketHandler var0, VehicleMountInput.Session var1) {
      return new VehicleConnectionInput(var0, var1);
   }

   @Override
   public void close() {
      Consumer[] var1 = handlers(this.handler);

      for (int var2 = 0; var2 < IDS.length; var2++) {
         if (var1[IDS[var2]] == this.wrappers[var2]) {
            this.handler.registerHandler(IDS[var2], this.previous[var2]);
         }
      }
   }

   static {
      try {
         HANDLERS = GenericPacketHandler.class.getDeclaredField("handlers");
         HANDLERS.setAccessible(true);
      } catch (ReflectiveOperationException var1) {
         throw new ExceptionInInitializerError(var1);
      }
   }
}
