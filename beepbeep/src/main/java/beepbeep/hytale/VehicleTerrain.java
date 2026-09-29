package beepbeep.hytale;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.modules.collision.BlockCollisionData;
import com.hypixel.hytale.server.core.modules.collision.CollisionConfig;
import com.hypixel.hytale.server.core.modules.collision.CollisionModule;
import com.hypixel.hytale.server.core.modules.collision.CollisionResult;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

public interface VehicleTerrain {
   VehicleTerrain.Hit sweep(Box var1, Vector3d var2, Vector3d var3);

   default VehicleTerrain.Hit sweep(VehicleShape var1, Vector3d var2, Vector3d var3) {
      return this.sweep(var1.bounds(), var2, var3);
   }

   static VehicleTerrain world(final ComponentAccessor<EntityStore> var0) {
      return new VehicleTerrain() {
         @Override
         public VehicleTerrain.Hit sweep(Box var1, Vector3d var2, Vector3d var3) {
            World var4 = ((EntityStore)var0.getExternalData()).getWorld();
            int var5 = (int)Math.floor(var2.x + var1.min.x + Math.min(0.0, var3.x));
            int var6 = (int)Math.floor(var2.x + var1.max.x + Math.max(0.0, var3.x));
            int var7 = (int)Math.floor(var2.z + var1.min.z + Math.min(0.0, var3.z));
            int var8 = (int)Math.floor(var2.z + var1.max.z + Math.max(0.0, var3.z));

            for (int var9 = Math.floorDiv(var5, 32); var9 <= Math.floorDiv(var6, 32); var9++) {
               for (int var10 = Math.floorDiv(var7, 32); var10 <= Math.floorDiv(var8, 32); var10++) {
                  if (var4.getChunkIfLoaded(ChunkUtil.indexChunk(var9, var10)) == null) {
                     return VehicleTerrain.Hit.unavailable();
                  }
               }
            }

            CollisionResult var15 = new CollisionResult();
            var15.setComputeOverlaps(true);
            var15.disableCharacterCollisions();
            var15.disableTriggerBlocks();
            var15.disableDamageBlocks();
            CollisionModule.findCollisions(var1, new Vector3d(var2), new Vector3d(var3), var15, var0);
            VehicleTerrain.Hit var16 = VehicleTerrain.Hit.clear();

            for (int var11 = 0; var11 < var15.getBlockCollisionCount(); var11++) {
               BlockCollisionData var12 = var15.getBlockCollision(var11);
               if (Double.isFinite(var12.collisionStart)
                  && !(var12.collisionStart > 1.0)
                  && (var12.overlapping || !(var12.collisionNormal.dot(var3) >= -1.0E-9))) {
                  double var13 = Math.max(0.0, var12.collisionStart);
                  if (!var16.blocked() || var13 < var16.fraction()) {
                     var16 = new VehicleTerrain.Hit(true, true, var13, new Vector3d(var12.collisionNormal));
                  }
               }
            }

            return var16;
         }

         @Override
         public VehicleTerrain.Hit sweep(VehicleShape var1, Vector3d var2, Vector3d var3) {
            World var4 = ((EntityStore)var0.getExternalData()).getWorld();
            Box var5 = var1.bounds();
            int var6 = (int)Math.floor(var2.x + var5.min.x + Math.min(0.0, var3.x));
            int var7 = (int)Math.floor(var2.x + var5.max.x + Math.max(0.0, var3.x));
            int var8 = (int)Math.floor(var2.z + var5.min.z + Math.min(0.0, var3.z));
            int var9 = (int)Math.floor(var2.z + var5.max.z + Math.max(0.0, var3.z));
            int var10 = Math.max(0, (int)Math.floor(var2.y + var5.min.y + Math.min(0.0, var3.y)));
            int var11 = Math.min(511, (int)Math.floor(var2.y + var5.max.y + Math.max(0.0, var3.y)));

            for (int var12 = Math.floorDiv(var6, 32); var12 <= Math.floorDiv(var7, 32); var12++) {
               for (int var13 = Math.floorDiv(var8, 32); var13 <= Math.floorDiv(var9, 32); var13++) {
                  if (var4.getChunkIfLoaded(ChunkUtil.indexChunk(var12, var13)) == null) {
                     return VehicleTerrain.Hit.unavailable();
                  }
               }
            }

            CollisionConfig var22 = new CollisionConfig();
            var22.setDefaultCollisionBehaviour();
            var22.setWorld(var4);
            var22.setCheckTriggerBlocks(false);
            var22.setCheckDamageBlocks(false);
            VehicleTerrain.Hit var23 = VehicleTerrain.Hit.clear();

            for (int var14 = var6; var14 <= var7; var14++) {
               for (int var15 = var10; var15 <= var11; var15++) {
                  for (int var16 = var8; var16 <= var9; var16++) {
                     if (var22.canCollide(var14, var15, var16) && var22.blockCanCollide) {
                        int var17 = var22.getDetailCount();

                        for (int var18 = 0; var18 < Math.max(1, var17); var18++) {
                           Box var19 = (var17 == 0 ? var22.getBoundingBox() : var22.getBoundingBox(var18)).clone();
                           Vector3d var20 = new Vector3d(
                              (double)(var14 + var22.getBoundingBoxOffsetX()),
                              (double)(var15 + var22.getBoundingBoxOffsetY()),
                              (double)(var16 + var22.getBoundingBoxOffsetZ())
                           );
                           var19.min.add(var20);
                           var19.max.add(var20);
                           VehicleTerrain.Hit var21 = var1.against(var19, var2, var3);
                           if (var21.blocked()
                              && (
                                 !var23.blocked()
                                    || var21.fraction() < var23.fraction()
                                    || var21.fraction() == var23.fraction() && var21.depth() > var23.depth()
                              )) {
                              var23 = var21;
                           }
                        }
                     }
                  }
               }
            }

            return var23;
         }
      };
   }

   public static record Hit(boolean loaded, boolean blocked, double fraction, Vector3d normal, double depth) {
      public Hit(boolean var1, boolean var2, double var3, Vector3d var5) {
         this(var1, var2, var3, var5, 0.0);
      }

      public static VehicleTerrain.Hit clear() {
         return new VehicleTerrain.Hit(true, false, 1.0, new Vector3d());
      }

      public static VehicleTerrain.Hit unavailable() {
         return new VehicleTerrain.Hit(false, true, 0.0, new Vector3d());
      }
   }
}
