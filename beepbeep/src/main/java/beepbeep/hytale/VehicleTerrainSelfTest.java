package beepbeep.hytale;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

final class VehicleTerrainSelfTest {
   static void run(Store<EntityStore> var0) {
      WorldChunk var1 = ((EntityStore)var0.getExternalData()).getWorld().getChunkIfLoaded(0L);

      for (int var2 = 6; var2 <= 26; var2++) {
         for (int var3 = 6; var3 <= 26; var3++) {
            for (int var4 = 300; var4 <= 307; var4++) {
               var1.setBlock(var2, var4, var3, "Empty");
            }

            var1.setBlock(var2, 300, var3, "Rock_Stone");
         }
      }

      VehicleTerrain var8 = VehicleTerrain.world(var0);
      VehicleTerrain.Hit var9 = var8.sweep(new Box(-0.025, -0.025, -0.025, 0.025, 0.025, 0.025), new Vector3d(16.0, 302.0, 16.0), new Vector3d(0.0, -2.0, 0.0));
      require(var9.loaded() && var9.blocked() && var9.normal().y > 0.5 && Math.abs(var9.fraction() - 0.4875) < 0.001, "real block sweep " + var9);
      VehicleRuntimeComponent var10 = new VehicleRuntimeComponent();
      var10.brake = 1.0;
      Vector3d var5 = new Vector3d(16.0, 303.0, 20.0);

      for (int var6 = 0; var6 < 600; var6++) {
         var5 = VehicleMotion.step(var10, var5, var8);
      }

      require(var5.y > 301.2 && var5.y < 301.5, "real floor support " + var5);

      for (int var12 = 6; var12 <= 26; var12++) {
         for (int var7 = 0; var7 <= 15; var7++) {
            var1.setBlock(var12, 301, var7, "Rock_Stone");
         }
      }

      var10.brake = 0.0;
      var10.throttle = 1.0;
      var10.peakTorqueNm = 120.0;

      for (int var13 = 0; var13 < 270; var13++) {
         var5 = VehicleMotion.step(var10, var5, var8);
      }

      var10.throttle = 0.0;
      var10.brake = 1.0;

      for (int var14 = 0; var14 < 600; var14++) {
         var5 = VehicleMotion.step(var10, var5, var8);
      }

      require(var5.z < 16.0 && var5.y > 301.2, "real one-block ascent " + var5);

      for (int var15 = 6; var15 <= 26; var15++) {
         for (int var17 = 302; var17 <= 306; var17++) {
            var1.setBlock(var15, var17, 9, "Rock_Stone");
         }
      }

      var5.set(16.0, 302.4, 14.0);
      var10 = new VehicleRuntimeComponent();
      var10.throttle = 1.0;

      for (int var16 = 0; var16 < 180; var16++) {
         var5 = VehicleMotion.step(var10, var5, var8);
      }

      require(var5.z >= 12.04 && var5.z < 13.0 && var10.velocityForward == 0.0, "real wall stop " + var5);
      System.out.println("PASS: real terrain adapter + spring support + one-block ascent + wall stop");
   }

   static void require(boolean var0, String var1) {
      if (!var0) {
         throw new IllegalStateException("FAIL: " + var1);
      } else {
         System.out.println("PASS: " + var1);
      }
   }
}
