package beepbeep.hytale;

import com.google.gson.JsonObject;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.builtin.mounts.NPCMountComponent;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.packets.interaction.DismountNPC;
import com.hypixel.hytale.protocol.packets.interaction.MountNPC;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.io.handlers.GenericPacketHandler;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.hitboxcollision.HitboxCollision;
import com.hypixel.hytale.server.core.modules.entity.hitboxcollision.HitboxCollisionConfig;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.EntityViewer;
import com.hypixel.hytale.server.core.receiver.IPacketReceiver;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import it.unimi.dsi.fastutil.Pair;
import java.util.concurrent.ConcurrentHashMap;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class VehicleMount {
   private static final ConcurrentHashMap<Ref<EntityStore>, VehicleRuntimeComponent> riders = new ConcurrentHashMap<>();

   private VehicleMount() {
   }

   public static Vector3d seatPosition(VehicleRuntimeComponent var0, Vector3dc var1) {
      return new Quaterniond().rotationYXZ(var0.yaw, var0.pitch, var0.roll).transform(new Vector3d(var0.seatX, var0.seatY, -var0.seatZ)).add(var1);
   }

   public static Ref<EntityStore> spawnProxy(Store<EntityStore> var0, Vector3dc var1, Rotation3f var2) {
      ModelAsset var3 = (ModelAsset)ModelAsset.getAssetMap().getAsset("BeepBeep_SeatProxy");
      if (var3 == null) {
         throw new IllegalStateException("Seat proxy model is not loaded");
      } else {
         Model var4 = Model.createScaledModel(var3, 0.001F);
         NPCPlugin var5 = NPCPlugin.get();
         Pair var6 = var5.spawnEntity(var0, var5.getIndex("Empty_Role"), var1, var2, var4, (var0x, var1x, var2x) -> {
            var1x.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
            var1x.addComponent(Frozen.getComponentType(), Frozen.get());
            var1x.addComponent(Invulnerable.getComponentType(), Invulnerable.INSTANCE);
         }, null);
         if (var6 != null && var6.first() != null && ((Ref)var6.first()).isValid()) {
            return (Ref<EntityStore>)var6.first();
         } else {
            throw new IllegalStateException("Seat proxy spawn failed");
         }
      }
   }

   static Ref<EntityStore> spawnFloor(Store<EntityStore> var0, Vector3dc var1) {
      ModelAsset var2 = (ModelAsset)ModelAsset.getAssetMap().getAsset("BeepBeep_SeatFloor");
      HitboxCollisionConfig var3 = (HitboxCollisionConfig)HitboxCollisionConfig.getAssetMap().getAsset("HardCollision");
      if (var2 != null && var3 != null) {
         Model var4 = Model.createUnitScaleModel(var2);
         Holder var5 = EntityStore.REGISTRY.newHolder();
         var5.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
         var5.addComponent(UUIDComponent.getComponentType(), UUIDComponent.randomUUID());
         var5.addComponent(NetworkId.getComponentType(), new NetworkId(((EntityStore)var0.getExternalData()).takeNextNetworkId()));
         var5.addComponent(TransformComponent.getComponentType(), new TransformComponent(new Vector3d(var1), new Rotation3f()));
         var5.addComponent(ModelComponent.getComponentType(), new ModelComponent(var4));
         var5.addComponent(BoundingBox.getComponentType(), new BoundingBox(var4.getBoundingBox()));
         var5.addComponent(HitboxCollision.getComponentType(), new HitboxCollision(var3));
         var5.addComponent(Invulnerable.getComponentType(), Invulnerable.INSTANCE);
         return var0.addEntity(var5, AddReason.SPAWN);
      } else {
         throw new IllegalStateException("Seat floor assets missing");
      }
   }

   public static void mount(Store<EntityStore> var0, VehicleRuntimeComponent var1, Ref<EntityStore> var2, PlayerRef var3, Ref<EntityStore> var4) {
      Player var5 = (Player)var0.getComponent(var2, Player.getComponentType());
      if (var5.getMountEntityId() == 0 && var0.getComponent(var2, MountedComponent.getComponentType()) == null) {
         Vector3d var6 = ((TransformComponent)var0.getComponent(var4, TransformComponent.getComponentType())).getPosition();
         Ref var7 = spawnProxy(var0, var6, new Rotation3f((float)var1.pitch, (float)var1.yaw, (float)var1.roll));
         var1.seatFloor = null;
         var1.proxy = var7;
         var1.driver = var2;
         var1.driverUuid = var3.getUuid();
         var1.proxyNetworkId = ((NetworkId)var0.getComponent(var7, NetworkId.getComponentType())).getId();
         var1.mountInput = VehicleMountInput.attach(var1.driverUuid, var1.proxyNetworkId);
         var1.mountInput.debug = var1.debug;
         if (var1.debug != null) {
            var1.debug.trace.event("mount", "seat-created");
         }

         var1.mountInput.seatDirection = new Direction((float)var1.yaw, (float)var1.pitch, (float)var1.roll);
         HeadRotation var8 = (HeadRotation)var0.getComponent(var2, HeadRotation.getComponentType());
         if (var8 != null) {
            var1.mountInput.lookYaw = (double)var8.getRotation().yaw();
         }

         var1.seated = true;
         var1.driven = true;
         var1.throttle = 0.0;
         var1.steer = 0.0;
         var1.brake = 1.0;
         var1.mountPending = true;
         var1.mountWait = 0.0;
         var1.inputSource = "seat-pending";
         riders.put(var2, var1);
         VehicleMountInput.Session var9 = var1.mountInput;
         PacketHandler var10 = var3.getPacketHandler();
         var10.getChannel().execute(() -> {
            if (!var9.closed) {
               try {
                  if (!(var10 instanceof GenericPacketHandler var2x)) {
                     throw new IllegalStateException("Unsupported connection handler");
                  }

                  var9.connectionInput = VehicleConnectionInput.install(var2x, var9);
                  var9.hookReady = true;
               } catch (RuntimeException var3x) {
                  var9.hookError = var3x.toString();
                  var9.exitRequested = true;
               }
            }
         });
      } else {
         throw new IllegalStateException("Leave your current mount first");
      }
   }

   public static void sync(Store<EntityStore> var0, VehicleRuntimeComponent var1, CommandBuffer<EntityStore> var2) {
      if (var1.seated
         && var1.driver != null
         && var1.driver.isValid()
         && var1.driver.getStore() == var0
         && var1.proxy != null
         && var1.proxy.isValid()
         && var1.mountInput != null
         && !var1.mountInput.exitRequested
         && var1.mountInput.hookReady) {
         EntityViewer var3 = (EntityViewer)var0.getComponent(var1.driver, EntityViewer.getComponentType());
         Player var4 = (Player)var0.getComponent(var1.driver, Player.getComponentType());
         PlayerRef var5 = (PlayerRef)var0.getComponent(var1.driver, PlayerRef.getComponentType());
         if (var3 != null && var4 != null && var5 != null && var3.sent.containsKey(var1.proxy)) {
            if (var4.getMountEntityId() == 0 || var4.getMountEntityId() == var1.proxyNetworkId) {
               if (var0.getComponent(var1.driver, Teleport.getComponentType()) == null) {
                  if (var1.mountPending) {
                     sendMount(var4, var5.getPacketHandler(), var1.proxyNetworkId, (float)var1.seatX, (float)var1.seatY, (float)(-var1.seatZ));
                     var1.mountInput.fixedControls = false;
                     var1.mountPending = false;
                  }

                  if (var4.getMountEntityId() == var1.proxyNetworkId) {
                     if (var1.debug != null && var1.debug.frame && var1.debug.trace.active()) {
                        JsonObject var6 = new JsonObject();
                        var6.addProperty("camera", "vanilla-npc-mount");
                        var6.addProperty("yaw", var1.yaw);
                        var6.addProperty("mountId", var1.proxyNetworkId);
                        var6.addProperty("proxySent", var3.sent.containsKey(var1.proxy));
                        var6.addProperty("floorSent", false);
                        var6.addProperty("firstBinding", false);
                        var1.debug.trace.event("outgoing", var6);
                     }

                     if (var1.debug != null && var1.debug.frame && var1.debug.trace.active()) {
                        JsonObject var7 = new JsonObject();
                        var7.addProperty("playerMountedComponent", var0.getComponent(var1.driver, MountedComponent.getComponentType()) != null);
                        var7.addProperty("nativeNpcMount", var0.getComponent(var1.proxy, NPCMountComponent.getComponentType()) != null);
                        var1.debug.trace.event("outgoing-seat", var7);
                     }
                  }
               }
            }
         }
      }
   }

   static void sendMount(Player var0, IPacketReceiver var1, int var2) {
      sendMount(var0, var1, var2, 0.0F, 0.0F, 0.0F);
   }

   static void sendMount(Player var0, IPacketReceiver var1, int var2, float var3, float var4, float var5) {
      var0.setMountEntityId(var2);
      var1.write(new MountNPC(var3, var4, var5, var2));
   }

   public static void follow(ComponentAccessor<EntityStore> var0, VehicleRuntimeComponent var1, Vector3dc var2) {
      if (var1.proxy != null && var1.proxy.isValid()) {
         Rotation3f var3 = new Rotation3f((float)var1.pitch, (float)var1.yaw, (float)var1.roll);
         if (var1.mountInput != null) {
            var1.mountInput.seatDirection = new Direction((float)var1.yaw, (float)var1.pitch, (float)var1.roll);
         }

         TransformComponent var4 = (TransformComponent)var0.getComponent(var1.proxy, TransformComponent.getComponentType());
         var4.setPosition(var2);
         var4.setRotation(var3);
         HeadRotation var5 = (HeadRotation)var0.getComponent(var1.proxy, HeadRotation.getComponentType());
         if (var5 != null) {
            var5.setRotation(var3);
         }

         if (var1.mountInput != null) {
            var1.mountInput.updateMountPose(var2.x(), var2.y(), var2.z(), var1.yaw);
         }
      }
   }

   static void lifecycleRelease(Ref<EntityStore> var0, CommandBuffer<EntityStore> var1) {
      VehicleRuntimeComponent var2 = riders.get(var0);
      if (var2 != null) {
         Player var3 = (Player)var1.getComponent(var0, Player.getComponentType());
         if (var3 != null && var3.getMountEntityId() == var2.proxyNetworkId) {
            var3.setMountEntityId(0);
         }

         MountedComponent var4 = (MountedComponent)var1.getComponent(var0, MountedComponent.getComponentType());
         if (var4 != null && var4.getMountedToEntity() == var2.proxy) {
            var1.tryRemoveComponent(var0, MountedComponent.getComponentType());
         }

         if (var2.mountInput != null) {
            var2.mountInput.exitRequested = true;
         }

         var1.run(var1x -> release(var1x, var2, null));
      }
   }

   public static void release(Store<EntityStore> var0, VehicleRuntimeComponent var1, Vector3dc var2) {
      if (var1.debug != null) {
         var1.debug.trace.event("dismount", var2 == null ? "cleanup" : "release-at-current-chassis");
      }

      Ref var3 = var1.proxy;
      Ref var4 = var1.seatFloor;
      Ref var5 = var1.driver;
      boolean var6 = var1.seated && !var1.mountPending;
      int var7 = var1.proxyNetworkId;
      var1.proxy = null;
      var1.seatFloor = null;
      if (var5 != null) {
         riders.remove(var5, var1);
      }

      VehicleMountInput.Session var8 = var1.mountInput;
      VehicleMountInput.detach(var1.driverUuid, var8);
      var1.driverUuid = null;
      var1.mountInput = null;
      if (var8 != null && var5 != null && var5.isValid() && var5.getStore() == var0) {
         PlayerRef var9 = (PlayerRef)var0.getComponent(var5, PlayerRef.getComponentType());
         if (var9 != null) {
            var9.getPacketHandler().getChannel().execute(() -> {
               if (var8.connectionInput != null) {
                  var8.connectionInput.close();
               }
            });
         }
      }

      if (var5 != null && var5.isValid() && var5.getStore() == var0) {
         Player var15 = (Player)var0.getComponent(var5, Player.getComponentType());
         boolean var10 = var15 != null && (var15.getMountEntityId() == var7 || var6 && var15.getMountEntityId() == 0);
         if (var10) {
            var15.setMountEntityId(0);
            MountedComponent var11 = (MountedComponent)var0.getComponent(var5, MountedComponent.getComponentType());
            if (var11 != null && var11.getMountedToEntity() == var3) {
               var0.tryRemoveComponent(var5, MountedComponent.getComponentType());
            }

            PlayerRef var12 = (PlayerRef)var0.getComponent(var5, PlayerRef.getComponentType());
            if (var12 != null && var6) {
               var12.getPacketHandler().write(new DismountNPC(var7));
               VehicleSeatView.reset(var12.getPacketHandler());
               MovementManager var13 = (MovementManager)var0.getComponent(var5, MovementManager.getComponentType());
               if (var13 != null) {
                  var13.update(var12.getPacketHandler());
               }
            }

            PlayerInput var17 = (PlayerInput)var0.getComponent(var5, PlayerInput.getComponentType());
            if (var17 != null) {
               var17.getMovementUpdateQueue().clear();
            }

            if (var2 != null && var6 && var0.getComponent(var5, Teleport.getComponentType()) == null) {
               Vector3d var14 = new Vector3d(var2).add(Math.cos(var1.yaw) * (var1.halfX + 0.8), 0.5, -Math.sin(var1.yaw) * (var1.halfX + 0.8));
               var0.putComponent(var5, Teleport.getComponentType(), Teleport.createForPlayer(var14, new Rotation3f(0.0F, (float)var1.yaw, 0.0F)));
            }
         }
      }

      if (var3 != null && var3.isValid() && var3.getStore() == var0) {
         NPCMountComponent var16 = (NPCMountComponent)var0.getComponent(var3, NPCMountComponent.getComponentType());
         if (var16 != null) {
            var16.setOwnerPlayerRef(null);
         }

         var0.removeEntity(var3, RemoveReason.REMOVE);
      }

      if (var4 != null && var4.isValid() && var4.getStore() == var0) {
         var0.removeEntity(var4, RemoveReason.REMOVE);
      }

      var1.proxyNetworkId = 0;
      var1.mountPending = false;
      var1.mountWait = 0.0;
      VehicleControl.reset(var1);
      var1.velocityForward = 0.0;
   }
}
