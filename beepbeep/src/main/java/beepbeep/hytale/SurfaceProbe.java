package beepbeep.hytale;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.modules.collision.BlockCollisionData;
import com.hypixel.hytale.server.core.modules.collision.CollisionModule;
import com.hypixel.hytale.server.core.modules.collision.CollisionResult;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

public final class SurfaceProbe {
   private SurfaceProbe() {
   }

   public static String below(ComponentAccessor<EntityStore> var0, Vector3d var1) {
      World var2 = ((EntityStore)var0.getExternalData()).getWorld();

      for (int var3 = -1; var3 <= 1; var3++) {
         for (int var4 = -1; var4 <= 1; var4++) {
            if (var2.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(var1.x + (double)var3 * 0.02, var1.z + (double)var4 * 0.02)) == null) {
               return "terrain unavailable (chunk not loaded)";
            }
         }
      }

      CollisionResult var5 = new CollisionResult();
      var5.disableCharacterCollisions();
      var5.disableTriggerBlocks();
      var5.disableDamageBlocks();
      CollisionModule.findCollisions(new Box(-0.01, -0.01, -0.01, 0.01, 0.01, 0.01), new Vector3d(var1), new Vector3d(0.0, -4.0, 0.0), var5, var0);
      if (var5.getBlockCollisionCount() == 0) {
         return "no contact within 4 blocks";
      } else {
         BlockCollisionData var6 = var5.getFirstBlockCollision();
         return "block=" + var6.blockId + " at " + var6.x + "," + var6.y + "," + var6.z + " t=" + var6.collisionStart + " normal=" + var6.collisionNormal;
      }
   }
}
