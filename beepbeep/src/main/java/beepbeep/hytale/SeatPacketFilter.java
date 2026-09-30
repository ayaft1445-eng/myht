package beepbeep.hytale;

import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.DismountNPC;
import com.hypixel.hytale.protocol.packets.player.ClientMovement;
import com.hypixel.hytale.protocol.packets.player.MouseInteraction;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;

/**
 * Официальный фильтр входящих пакетов сервера (PacketAdapters). Пакеты седоков отдаёт
 * их SeatInput, остальных игроков не трогает. Работает в сетевом потоке.
 */
final class SeatPacketFilter implements PlayerPacketFilter {
   @Override
   public boolean test(PlayerRef player, Packet packet) {
      if (!(packet instanceof ClientMovement) && !(packet instanceof MouseInteraction) && !(packet instanceof DismountNPC)) {
         return false;
      }

      SeatInput input = VehicleSeats.input(player.getUuid());
      return input != null && input.accept(packet, System.nanoTime());
   }
}
