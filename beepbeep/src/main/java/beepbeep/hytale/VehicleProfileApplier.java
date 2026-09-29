package beepbeep.hytale;

import com.google.gson.JsonObject;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;

public final class VehicleProfileApplier {
   private static final float MODEL_SCALE = 2.5F;

   private VehicleProfileApplier() {
   }

   public static void apply(Store<EntityStore> var0, Ref<EntityStore> var1, ComponentType<EntityStore, VehicleRuntimeComponent> var2, JsonObject var3) {
      if (var1 != null && var1.isValid() && var1.getStore() == var0) {
         VehicleRuntimeComponent var4 = (VehicleRuntimeComponent)var0.getComponent(var1, var2);
         if (var4 == null) {
            throw new IllegalArgumentException("Выбранная сущность больше не является машиной.");
         } else {
            ModelAsset var5 = (ModelAsset)ModelAsset.getAssetMap().getAsset(var3.get("modelAsset").getAsString());
            if (var5 == null) {
               throw new IllegalArgumentException("Модель не загружена: " + var3.get("modelAsset").getAsString());
            } else {
               VehicleRuntimeComponent var6 = VehicleCalibration.runtime(var3);
               copyConfiguration(var4, var6);
               Box var7 = VehicleBody.shape(var4, 0.0, 0.0, 0.0).bounds();
               Model var8 = Model.createScaledModel(var5, 2.5F, Map.of(), var7);
               var0.putComponent(var1, ModelComponent.getComponentType(), new ModelComponent(var8));
               BoundingBox var9 = new BoundingBox(var7);
               var9.setBaseModelBox(var7);
               var9.applyRotation(0.0F, (float)var4.yaw, 0.0F);
               var0.putComponent(var1, BoundingBox.getComponentType(), var9);
            }
         }
      } else {
         throw new IllegalArgumentException("Выбранная машина больше не существует.");
      }
   }

   private static void copyConfiguration(VehicleRuntimeComponent var0, VehicleRuntimeComponent var1) {
      var0.profileId = var1.profileId;
      var0.mass = var1.mass;
      var0.halfX = var1.halfX;
      var0.halfY = var1.halfY;
      var0.halfZ = var1.halfZ;
      var0.bodyX = var1.bodyX;
      var0.bodyY = var1.bodyY;
      var0.bodyZ = var1.bodyZ;
      var0.idleRpm = var1.idleRpm;
      var0.redlineRpm = var1.redlineRpm;
      var0.peakTorqueNm = var1.peakTorqueNm;
      var0.reverseRatio = var1.reverseRatio;
      var0.finalDrive = var1.finalDrive;
      var0.gearRatios = var1.gearRatios;
      var0.shiftUpRpm = var1.shiftUpRpm;
      var0.shiftDownRpm = var1.shiftDownRpm;
      var0.maxBrakeForce = var1.maxBrakeForce;
      var0.handbrakeForce = var1.handbrakeForce;
      var0.wheelX = var1.wheelX;
      var0.wheelY = var1.wheelY;
      var0.wheelZ = var1.wheelZ;
      var0.radius = var1.radius;
      var0.rest = var1.rest;
      var0.travel = var1.travel;
      var0.spring = var1.spring;
      var0.damping = var1.damping;
      var0.rebound = var1.rebound;
      var0.contactY = var1.contactY;
      var0.contact = var1.contact;
      var0.seatX = var1.seatX;
      var0.seatY = var1.seatY;
      var0.seatZ = var1.seatZ;
      var0.engineRpm = Math.max(var0.idleRpm, Math.min(var0.redlineRpm, var0.engineRpm));
   }
}
