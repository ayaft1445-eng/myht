package beepbeep.hytale;

import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.entities.MountMovement;
import com.hypixel.hytale.protocol.packets.interaction.DismountNPC;
import com.hypixel.hytale.protocol.packets.player.ClientMovement;
import com.hypixel.hytale.protocol.packets.player.MouseInteraction;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VehicleMountInput {
   private static final ConcurrentHashMap<UUID, VehicleMountInput.Session> sessions = new ConcurrentHashMap<>();

   public static void start() {
   }

   public static VehicleMountInput.Session attach(UUID var0, int var1) {
      VehicleMountInput.Session var2 = new VehicleMountInput.Session(var1);
      sessions.put(var0, var2);
      return var2;
   }

   public static void detach(UUID var0, VehicleMountInput.Session var1) {
      if (var0 != null && var1 != null) {
         var1.closed = true;
         sessions.remove(var0, var1);
      }
   }

   public static void stop() {
      for (VehicleMountInput.Session var1 : sessions.values()) {
         var1.closed = true;
      }

      sessions.clear();
   }

   public static void apply(VehicleRuntimeComponent var0, VehicleMountInput.Session var1, long var2) {
      VehicleMountInput.Sample var4 = var1.sample;
      var0.inputEvents = var1.movementPackets;
      var0.wishEvents = var1.wishPackets;
      var0.positionEvents = var1.blockedMountPackets;
      var0.inputAge = var4.at() == 0L ? 1.0 : Math.max(0.0, (double)(var2 - var4.at()) / 1.0E9);
      double var5 = var4.right();
      double var7 = var4.forward();
      if (!var1.exitRequested && !(var0.inputAge > 0.2) && Double.isFinite(var5) && Double.isFinite(var7)) {
         var1.neutralize();
         double var9 = Math.max(1.0, Math.hypot(var5, var7));
         var0.steer = Math.abs(var5) > 0.02 ? var5 / var9 : 0.0;
         var0.throttle = Math.abs(var7) > 0.02 ? var7 / var9 : 0.0;
         var0.brake = Math.abs(var0.throttle) < 0.01 ? 1.0 : 0.0;
         var0.inputSource = "fixed-wasd-" + var4.source();
      } else {
         var1.neutralize();
         var0.throttle = 0.0;
         var0.steer = 0.0;
         var0.brake = 1.0;
         var0.inputSource = var4.at() == 0L ? "seat-no-input" : "seat-idle";
      }
   }

   public static record Sample(double right, double forward, long at, String source) {
   }

   public static final class Session {
      public volatile VehicleMountInput.Sample sample = new VehicleMountInput.Sample(0.0, 0.0, 0L, "none");
      public volatile long movementPackets;
      public volatile long blockedMountPackets;
      public volatile long wishPackets;
      public volatile boolean exitRequested;
      public volatile double lookYaw;
      public volatile boolean fixedControls;
      public volatile long mousePackets;
      public volatile long mouseAt;
      public volatile double mouseSteer;
      private long controlAt;
      private double pendingMouseX;
      public volatile VehicleDebug debug;
      public volatile Direction seatDirection = new Direction();
      public volatile boolean closed;
      public volatile boolean hookReady;
      public volatile String hookError;
      public volatile VehicleConnectionInput connectionInput;
      public volatile long velocityPackets;
      public volatile long mountStateAt;
      public volatile long lastMountAt;
      public volatile long mountPoseAt;
      public volatile double mountX;
      public volatile double mountY;
      public volatile double mountZ;
      public volatile double mountYaw;
      public volatile boolean mountIdle;
      public volatile VehicleMountInput.Sample lastActive = new VehicleMountInput.Sample(0.0, 0.0, 0L, "none");
      public volatile VehicleMountInput.Sample lastWish = new VehicleMountInput.Sample(0.0, 0.0, 0L, "none");
      public volatile VehicleMountInput.Sample lastVelocity = new VehicleMountInput.Sample(0.0, 0.0, 0L, "none");
      public volatile long lastWishAt;
      public final int networkId;

      public Session() {
         this(0);
      }

      public Session(int var1) {
         this.networkId = var1;
      }

      public synchronized boolean accept(Packet var1) {
         if (this.closed) {
            return false;
         } else if (var1 instanceof MouseInteraction var6) {
            if (this.fixedControls && var6.mouseMotion != null && var6.mouseMotion.relativeMotion != null) {
               this.mousePackets++;
               this.mouseAt = System.nanoTime();
               this.pendingMouseX = Math.clamp(this.pendingMouseX + (double)Math.clamp((long)var6.mouseMotion.relativeMotion.x, -300, 300), -600.0, 600.0);
            }

            return false;
         } else if (!(var1 instanceof DismountNPC var2)) {
            if (var1 instanceof MountMovement var5) {
               this.blockedMountPackets++;
               if (var5.movementStates != null) {
                  this.mountIdle = var5.movementStates.horizontalIdle;
                  this.mountStateAt = System.nanoTime();
               }

               this.captureMount(var5, System.nanoTime());
               return true;
            } else if (var1 instanceof ClientMovement var4) {
               this.movementPackets++;
               if (var4.lookOrientation != null && Float.isFinite(var4.lookOrientation.yaw)) {
                  this.lookYaw = (double)var4.lookOrientation.yaw;
               }

               if (var4.wishMovement != null) {
                  this.wishPackets++;
               }

               if (var4.wishMovement != null) {
                  VehicleMountInput.Sample var3 = this.decode(var4.wishMovement.x, var4.wishMovement.z, "wish");
                  this.lastWishAt = var3.at();
                  this.lastWish = var3;
                  if (var3.at() - this.lastMountAt > 200000000L) {
                     this.sample = var3;
                  }
               }

               if (var4.velocity != null) {
                  this.velocityPackets++;
                  this.lastVelocity = this.decode(var4.velocity.x, var4.velocity.z, "velocity");
                  if (var4.wishMovement == null && System.nanoTime() - this.lastWishAt > 200000000L && System.nanoTime() - this.lastMountAt > 200000000L) {
                     this.sample = this.lastVelocity;
                  }
               }

               if (!this.fixedControls
                  && this.mountIdle
                  && System.nanoTime() - this.mountStateAt < 200000000L
                  && System.nanoTime() - this.lastWishAt > 200000000L) {
                  this.sample = new VehicleMountInput.Sample(0.0, 0.0, System.nanoTime(), "idle");
               }

               if (Double.isFinite(this.sample.right())
                  && Double.isFinite(this.sample.forward())
                  && Math.hypot(this.sample.right(), this.sample.forward()) > 0.02) {
                  this.lastActive = this.sample;
               }

               return false;
            } else {
               return false;
            }
         } else {
            if (var2.mountEntityId == this.networkId || var2.mountEntityId == 0) {
               this.exitRequested = true;
            }

            return true;
         }
      }

      void updateMountPose(double var1, double var3, double var5, double var7) {
         this.mountX = var1;
         this.mountY = var3;
         this.mountZ = var5;
         this.mountYaw = var7;
         this.mountPoseAt = System.nanoTime();
      }

      private void captureMount(MountMovement var1, long var2) {
         if (var1.movementStates != null && var1.movementStates.horizontalIdle) {
            this.sample = new VehicleMountInput.Sample(0.0, 0.0, var2, "mount-idle");
            this.lastMountAt = var2;
         } else if (var1.absolutePosition != null && var1.bodyOrientation != null && this.mountPoseAt != 0L) {
            double var4 = var1.absolutePosition.x - this.mountX;
            double var6 = var1.absolutePosition.z - this.mountZ;
            double var8 = Math.hypot(var4, var6);
            if (Double.isFinite(var8) && !(var8 > 4.0)) {
               double var10 = Math.sin(this.mountYaw);
               double var12 = Math.cos(this.mountYaw);
               double var14 = var4 * -var10 + var6 * -var12;
               double var16 = var4 * var12 + var6 * -var10;
               double var18 = Math.atan2(Math.sin((double)var1.bodyOrientation.yaw - this.mountYaw), Math.cos((double)var1.bodyOrientation.yaw - this.mountYaw));
               double var20 = var8 > 0.004 ? Math.clamp(var14 / Math.max(var8, 0.004), -1.0, 1.0) : 0.0;
               double var22 = Math.clamp(-var18 / 0.12, -1.0, 1.0);
               if (Math.abs(var22) < 0.08 && var8 > 0.004) {
                  var22 = Math.abs(var16 / var8) > 0.25 ? Math.clamp(var16 / var8, -1.0, 1.0) : 0.0;
               }

               this.sample = new VehicleMountInput.Sample(var22, var20, var2, "mount-position");
               this.lastMountAt = var2;
               if (Math.hypot(var22, var20) > 0.02) {
                  this.lastActive = this.sample;
               }
            }
         }
      }

      synchronized double steering(long var1) {
         double var3 = this.controlAt == 0L ? 0.03333333333333333 : Math.clamp((double)(var1 - this.controlAt) / 1.0E9, 0.0, 0.1);
         this.controlAt = var1;
         this.mouseSteer = Math.clamp(this.mouseSteer * Math.exp(-2.8 * var3) + this.pendingMouseX / 180.0, -1.0, 1.0);
         this.pendingMouseX = 0.0;
         if (Math.abs(this.mouseSteer) < 0.005) {
            this.mouseSteer = 0.0;
         }

         return this.mouseSteer;
      }

      synchronized void neutralize() {
         this.pendingMouseX = 0.0;
         this.mouseSteer = 0.0;
         this.controlAt = 0L;
      }

      private VehicleMountInput.Sample decode(double var1, double var3, String var5) {
         double var6 = this.fixedControls ? 0.0 : this.lookYaw;
         double var8 = Math.cos(var6);
         double var10 = Math.sin(var6);
         return new VehicleMountInput.Sample(var1 * var8 - var3 * var10, -var1 * var10 - var3 * var8, System.nanoTime(), var5);
      }

      ClientMovement forNative(ClientMovement var1) {
         if (this.fixedControls && !this.closed) {
            ClientMovement var2 = new ClientMovement(var1);
            Direction var3 = this.seatDirection;
            var2.bodyOrientation = new Direction(var3);
            var2.lookOrientation = new Direction(var3);
            return var2;
         } else {
            return var1;
         }
      }
   }
}
