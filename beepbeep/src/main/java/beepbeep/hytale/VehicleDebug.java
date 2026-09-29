package beepbeep.hytale;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.packets.entities.MountMovement;
import com.hypixel.hytale.protocol.packets.interaction.DismountNPC;
import com.hypixel.hytale.protocol.packets.player.ClientMovement;
import com.hypixel.hytale.protocol.packets.player.MouseInteraction;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput.InputUpdate;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.nio.file.Path;
import org.joml.Vector3d;
import org.joml.Vector3dc;

final class VehicleDebug {
   final VehicleDebugTrace trace;
   final Ref<EntityStore> observer;
   private long nextFrame;
   boolean frame;

   VehicleDebug(Path var1, Ref<EntityStore> var2) {
      this.trace = new VehicleDebugTrace(var1);
      this.observer = var2;
   }

   void begin(float var1, long var2) {
      long var4 = System.nanoTime();
      this.frame = false;
      if (this.trace.active()) {
         if (this.trace.expired()) {
            this.trace.stop("90-second-limit");
         } else if (var4 >= this.nextFrame) {
            this.nextFrame = var4 + 100000000L;
            this.frame = true;
            JsonObject var6 = new JsonObject();
            number(var6, "dt", (double)var1);
            var6.addProperty("tick", var2);
            this.trace.event("frame", var6);
         }
      }
   }

   static void packet(VehicleMountInput.Session var0, Packet var1, boolean var2) {
      VehicleDebug var3 = var0.debug;
      if (var3 != null && var3.trace.active()) {
         JsonObject var4 = new JsonObject();
         var4.addProperty("packet", var1.getClass().getSimpleName());
         var4.addProperty("consumed", var2);
         var4.addProperty("mountIdle", var0.mountIdle);
         var4.addProperty("fixedControls", var0.fixedControls);
         var4.addProperty("hookReady", var0.hookReady);
         number(var4, "lookYaw", var0.lookYaw);
         var4.add("decoded", sample(var0.sample));
         var4.add("seatDirection", direction(var0.seatDirection));
         number(var4, "mouseSteer", var0.mouseSteer);
         var4.addProperty("mousePackets", var0.mousePackets);
         if (var1 instanceof ClientMovement var5) {
            var4.add("wish", position(var5.wishMovement));
            var4.add("velocity", (JsonElement)(var5.velocity == null ? JsonNull.INSTANCE : xyz(var5.velocity.x, var5.velocity.y, var5.velocity.z)));
            var4.add("position", position(var5.absolutePosition));
            var4.addProperty("hasRelativePosition", var5.relativePosition != null);
            if (var5.relativePosition != null) {
               var4.add(
                  "relativePosition",
                  xyz(
                     (double)Float.float16ToFloat(var5.relativePosition.x),
                     (double)Float.float16ToFloat(var5.relativePosition.y),
                     (double)Float.float16ToFloat(var5.relativePosition.z)
                  )
               );
            }

            var4.add("body", direction(var5.bodyOrientation));
            var4.add("look", direction(var5.lookOrientation));
            var4.add("states", states(var5.movementStates));
            var4.add("riderStates", states(var5.riderMovementStates));
            var4.addProperty("mountedTo", var5.mountedTo);
            var4.addProperty("teleportAck", var5.teleportAck != null);
         } else if (var1 instanceof MountMovement var6) {
            var4.add("position", position(var6.absolutePosition));
            var4.add("body", direction(var6.bodyOrientation));
            var4.add("states", states(var6.movementStates));
         } else if (var1 instanceof DismountNPC var7) {
            var4.addProperty("mountId", var7.mountEntityId);
         } else if (var1 instanceof MouseInteraction var8 && var8.mouseMotion != null && var8.mouseMotion.relativeMotion != null) {
            var4.addProperty("mouseDx", var8.mouseMotion.relativeMotion.x);
            var4.addProperty("mouseDy", var8.mouseMotion.relativeMotion.y);
         }

         var3.trace.event("packet", var4);
      }
   }

   void snapshot(ComponentAccessor<EntityStore> var1, VehicleRuntimeComponent var2, TransformComponent var3, String var4) {
      if (this.frame && this.trace.active()) {
         JsonObject var5 = new JsonObject();
         var5.addProperty("phase", var4);
         var5.addProperty("tick", var2.ticks);
         var5.add("chassis", transform(var3));
         var5.addProperty("seated", var2.seated);
         var5.addProperty("pending", var2.mountPending);
         var5.addProperty("terrain", var2.terrainState);
         var5.addProperty("bodySupported", var2.bodySupported);
         number(var5, "throttle", var2.throttle);
         number(var5, "steer", var2.steer);
         number(var5, "brake", var2.brake);
         number(var5, "speed", var2.velocityForward);
         number(var5, "verticalSpeed", var2.verticalVelocity);
         number(var5, "engineForce", var2.engineForce);
         number(var5, "rpm", var2.engineRpm);
         var5.addProperty("gear", var2.gear);
         var5.addProperty("inputSource", var2.inputSource);
         number(var5, "inputAge", var2.inputAge);
         Ref var6 = var2.driver != null ? var2.driver : this.observer;
         var5.add("rider", entity(var1, var6));
         var5.add("proxy", entity(var1, var2.proxy));
         var5.add("floor", entity(var1, var2.seatFloor));
         if (var6 != null && var6.isValid() && var6.getStore() == ((EntityStore)var1.getExternalData()).getStore()) {
            MountedComponent var7 = (MountedComponent)var1.getComponent(var6, MountedComponent.getComponentType());
            Player var8 = (Player)var1.getComponent(var6, Player.getComponentType());
            var5.addProperty("nativeRiderMount", var8 != null && var8.getMountEntityId() == var2.proxyNetworkId);
         }

         Vector3d var14 = VehicleMount.seatPosition(var2, var3.getPosition());
         var5.add("seat", xyz(var14.x, var14.y, var14.z));
         if (var6 != null && var6.isValid() && var6.getStore() == ((EntityStore)var1.getExternalData()).getStore()) {
            TransformComponent var15 = (TransformComponent)var1.getComponent(var6, TransformComponent.getComponentType());
            if (var15 != null) {
               number(var5, "riderSeatError", var15.getPosition().distance(var14));
               number(var5, "riderSeatDy", var15.getPosition().y() - var14.y);
            }

            PlayerInput var9 = (PlayerInput)var1.getComponent(var6, PlayerInput.getComponentType());
            JsonObject var10 = new JsonObject();
            if (var9 != null) {
               for (InputUpdate var12 : var9.getMovementUpdateQueue()) {
                  String var13 = var12.getClass().getSimpleName();
                  var10.addProperty(var13, var10.has(var13) ? var10.get(var13).getAsInt() + 1 : 1);
               }
            }

            var5.add("inputQueue", var10);
         }

         if (var2.mountInput != null) {
            var5.add("decoded", sample(var2.mountInput.sample));
            var5.addProperty("packetCount", var2.mountInput.movementPackets);
            var5.addProperty("wishCount", var2.mountInput.wishPackets);
            var5.addProperty("mountPacketCount", var2.mountInput.blockedMountPackets);
            var5.addProperty("mousePackets", var2.mountInput.mousePackets);
            number(var5, "mouseSteer", var2.mountInput.mouseSteer);
         }

         JsonArray var16 = new JsonArray();

         for (int var17 = 0; var17 < var2.wheelX.length; var17++) {
            JsonObject var18 = new JsonObject();
            var18.addProperty("contact", var2.contact[var17]);
            number(var18, "groundY", var2.contactY[var17]);
            number(var18, "compression", var17 < var2.debugCompression.length ? var2.debugCompression[var17] : Double.NaN);
            number(var18, "load", var17 < var2.debugLoad.length ? var2.debugLoad[var17] : Double.NaN);
            var16.add(var18);
         }

         var5.add("wheels", var16);
         this.trace.event("snapshot", var5);
      }
   }

   private static JsonElement entity(ComponentAccessor<EntityStore> var0, Ref<EntityStore> var1) {
      if (var1 != null && var1.isValid() && var1.getStore() == ((EntityStore)var0.getExternalData()).getStore()) {
         JsonObject var2 = new JsonObject();
         var2.add("transform", transform((TransformComponent)var0.getComponent(var1, TransformComponent.getComponentType())));
         NetworkId var3 = (NetworkId)var0.getComponent(var1, NetworkId.getComponentType());
         if (var3 != null) {
            var2.addProperty("networkId", var3.getId());
         }

         HeadRotation var4 = (HeadRotation)var0.getComponent(var1, HeadRotation.getComponentType());
         if (var4 != null) {
            Rotation3f var5 = var4.getRotation();
            var2.add("head", direction(new Direction(var5.yaw(), var5.pitch(), var5.roll())));
         }

         Velocity var6 = (Velocity)var0.getComponent(var1, Velocity.getComponentType());
         if (var6 != null) {
            var2.add("velocity", vector(var6.getVelocity()));
            var2.add("clientVelocity", vector(var6.getClientVelocity()));
         }

         return var2;
      } else {
         return JsonNull.INSTANCE;
      }
   }

   private static JsonElement transform(TransformComponent var0) {
      if (var0 == null) {
         return JsonNull.INSTANCE;
      } else {
         JsonObject var1 = new JsonObject();
         var1.add("position", vector(var0.getPosition()));
         Rotation3f var2 = var0.getRotation();
         var1.add("rotation", direction(new Direction(var2.yaw(), var2.pitch(), var2.roll())));
         return var1;
      }
   }

   static JsonObject sample(VehicleMountInput.Sample var0) {
      JsonObject var1 = new JsonObject();
      number(var1, "right", var0.right());
      number(var1, "forward", var0.forward());
      var1.addProperty("source", var0.source());
      number(var1, "age", var0.at() == 0L ? -1.0 : (double)(System.nanoTime() - var0.at()) / 1.0E9);
      return var1;
   }

   static JsonElement direction(Direction var0) {
      if (var0 == null) {
         return JsonNull.INSTANCE;
      } else {
         JsonObject var1 = new JsonObject();
         number(var1, "yaw", (double)var0.yaw);
         number(var1, "pitch", (double)var0.pitch);
         number(var1, "roll", (double)var0.roll);
         return var1;
      }
   }

   static JsonElement states(MovementStates var0) {
      if (var0 == null) {
         return JsonNull.INSTANCE;
      } else {
         JsonObject var1 = new JsonObject();
         var1.addProperty("horizontalIdle", var0.horizontalIdle);
         var1.addProperty("onGround", var0.onGround);
         var1.addProperty("falling", var0.falling);
         var1.addProperty("flying", var0.flying);
         var1.addProperty("sitting", var0.sitting);
         var1.addProperty("walking", var0.walking);
         var1.addProperty("running", var0.running);
         var1.addProperty("jumping", var0.jumping);
         return var1;
      }
   }

   static JsonElement position(Position var0) {
      return (JsonElement)(var0 == null ? JsonNull.INSTANCE : xyz(var0.x, var0.y, var0.z));
   }

   static JsonElement vector(Vector3dc var0) {
      return xyz(var0.x(), var0.y(), var0.z());
   }

   static JsonArray xyz(double var0, double var2, double var4) {
      JsonArray var6 = new JsonArray();

      for (double var10 : new double[]{var0, var2, var4}) {
         var6.add((JsonElement)(Double.isFinite(var10) ? new JsonPrimitive(var10) : JsonNull.INSTANCE));
      }

      return var6;
   }

   static void number(JsonObject var0, String var1, double var2) {
      if (Double.isFinite(var2)) {
         var0.addProperty(var1, var2);
      } else {
         var0.add(var1, JsonNull.INSTANCE);
      }
   }
}
