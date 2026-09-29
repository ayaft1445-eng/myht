package beepbeep.hytale;

import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.ModelTransform;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.protocol.TransformUpdate;
import com.hypixel.hytale.protocol.packets.entities.EntityUpdates;
import com.hypixel.hytale.protocol.packets.interaction.MountNPC;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.EntityViewer;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.LODCull;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.SendPackets;
import com.hypixel.hytale.server.core.receiver.IPacketReceiver;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import org.joml.Vector3d;

public final class VehicleProxySelfTest {
   public static void run(Store<EntityStore> var0) {
      VehicleRuntimeComponent var1 = new VehicleRuntimeComponent();
      Vector3d var2 = new Vector3d(10.0, 310.0, 10.0);
      Holder var3 = EntityStore.REGISTRY.newHolder();
      var3.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
      var3.addComponent(TransformComponent.getComponentType(), new TransformComponent(var2, new Rotation3f()));
      var3.addComponent(NetworkId.getComponentType(), new NetworkId(((EntityStore)var0.getExternalData()).takeNextNetworkId()));
      Ref var4 = var0.addEntity(var3, AddReason.SPAWN);
      Ref var5 = VehicleMount.spawnProxy(var0, var2, new Rotation3f());
      var1.proxy = var5;
      Holder var6 = EntityStore.REGISTRY.newHolder();
      var6.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
      var6.addComponent(TransformComponent.getComponentType(), new TransformComponent(var2, new Rotation3f()));
      var6.addComponent(HeadRotation.getComponentType(), new HeadRotation(new Rotation3f()));
      Ref var7 = var0.addEntity(var6, AddReason.SPAWN);
      var1.driver = var7;
      var1.mountInput = new VehicleMountInput.Session();

      try {
         if (var0.getComponent(var5, NetworkId.getComponentType()) == null) {
            throw new AssertionError("missing network id");
         }

         if (var0.getComponent(var5, NPCEntity.getComponentType()) == null) {
            throw new AssertionError("seat proxy must use vanilla NPC mounting");
         }

         if (var0.getComponent(var5, MountedComponent.getComponentType()) != null) {
            throw new AssertionError("NPC proxy must not be nested through ECS mounting");
         }

         if (var0.getComponent(var7, MountedComponent.getComponentType()) != null) {
            throw new AssertionError("legacy NPC rider must not mix ECS mounting");
         }

         testReplication(var0, var5, var2);
         var1.yaw = 0.6;
         var1.pitch = 0.2;
         var1.roll = -0.1;
         Vector3d var8 = new Vector3d(12.0, 311.0, 11.0);
         TransformComponent var9 = (TransformComponent)var0.getComponent(var7, TransformComponent.getComponentType());
         Vector3d var10 = new Vector3d(var9.getPosition());
         VehicleMount.follow(var0, var1, var8);
         TransformComponent var11 = (TransformComponent)var0.getComponent(var5, TransformComponent.getComponentType());
         if (var11.getPosition().distance(var8) > 1.0E-8) {
            throw new AssertionError("proxy not at chassis origin");
         }

         if (Math.abs((double)var11.getRotation().yaw() - var1.yaw) > 1.0E-6
            || Math.abs((double)var11.getRotation().pitch() - var1.pitch) > 1.0E-6
            || Math.abs((double)var11.getRotation().roll() - var1.roll) > 1.0E-6) {
            throw new AssertionError("proxy does not inherit chassis pose");
         }

         if (var9.getPosition().distance(var10) > 1.0E-8) {
            throw new AssertionError("server manually moved native rider");
         }

         if (Math.abs(var1.mountInput.mountX - var8.x) > 1.0E-8 || Math.abs(var1.mountInput.mountYaw - var1.yaw) > 1.0E-8) {
            throw new AssertionError("authoritative mount pose not published to packet decoder");
         }

         System.out.println("PASS: vanilla NPC proxy follows chassis without manual rider movement");
      } finally {
         VehicleMount.release(var0, var1, null);
         if (var7.isValid()) {
            var0.removeEntity(var7, RemoveReason.REMOVE);
         }

         if (var4.isValid()) {
            var0.removeEntity(var4, RemoveReason.REMOVE);
         }
      }

      if (var5.isValid()) {
         throw new AssertionError("orphan seat proxy");
      } else {
         System.out.println("PASS: vanilla NPC seat replication + follow + cleanup (no client attached)");
      }
   }

   private static void testReplication(Store<EntityStore> var0, Ref<EntityStore> var1, Vector3d var2) {
      ComponentType var3 = EntityViewer.getComponentType();
      final ArrayList var4 = new ArrayList();
      IPacketReceiver var5 = new IPacketReceiver() {
         public void write(ToClientPacket var1) {
            var4.add(var1);
         }

         public void writeNoCache(ToClientPacket var1) {
            var4.add(var1);
         }
      };
      EntityViewer var6 = new EntityViewer(128, var5);
      Holder var7 = EntityStore.REGISTRY.newHolder();
      var7.addComponent(var3, var6);
      var7.addComponent(TransformComponent.getComponentType(), new TransformComponent(new Vector3d(var2).add(24.0, 0.0, 0.0), new Rotation3f()));
      var7.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
      Ref var8 = var0.addEntity(var7, AddReason.SPAWN);

      try {
         var6.visible.add(var1);
         LODCull var9 = new LODCull(var3);
         var0.forEachChunk(var3, (var3x, var4x) -> {
            for (int var5x = 0; var5x < var3x.size(); var5x++) {
               if (var3x.getReferenceTo(var5x) == var8) {
                  var9.tick(0.033333335F, var5x, var3x, var0, var4x);
               }
            }
         });
         if (!var6.visible.contains(var1)) {
            throw new AssertionError("native LOD removed proxy at 24 blocks");
         }

         var6.queueUpdate(var1, new TransformUpdate(new ModelTransform(new Position(var2.x, var2.y, var2.z), new Direction(), new Direction())));
         SendPackets var10 = new SendPackets(var3);
         var0.forEachChunk(var3, (var3x, var4x) -> {
            for (int var5x = 0; var5x < var3x.size(); var5x++) {
               if (var3x.getReferenceTo(var5x) == var8) {
                  var10.tick(0.033333335F, var5x, var3x, var0, var4x);
               }
            }
         });
         if (!var6.sent.containsKey(var1) || var4.isEmpty()) {
            throw new AssertionError("seat not replicated");
         }

         if (!(var4.getFirst() instanceof EntityUpdates)) {
            throw new AssertionError("entity update not first");
         }

         int var11 = ((NetworkId)var0.getComponent(var1, NetworkId.getComponentType())).getId();
         Player var12 = new Player();
         VehicleMount.sendMount(var12, var5, var11);
         if (var12.getMountEntityId() != var11 || !(var4.getLast() instanceof MountNPC var13) || var13.entityId != var11) {
            throw new AssertionError("mount id mismatch");
         }

         System.out.println("PASS: NPC proxy is replicated before native mount binding");
      } finally {
         if (var8.isValid()) {
            var0.removeEntity(var8, RemoveReason.REMOVE);
         }
      }
   }
}
