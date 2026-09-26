package dev.hytalemodding.deathmatch.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.deathmatch.match.Fighter;
import dev.hytalemodding.deathmatch.match.MatchService;

import javax.annotation.Nonnull;
import java.util.List;

/** /dmtop — таблица текущего матча. */
public class DmTopCommand extends DmCommandBase {

    private static final int ROWS = 10;

    public DmTopCommand(MatchService service) {
        super("dmtop", "Таблица дезматча", service);
    }

    @Override
    protected void run(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        List<Fighter> board = this.service.leaderboard();
        if (board.isEmpty()) {
            say(context, "на арене пока никого.");
            return;
        }

        say(context, "таблица матча:");
        int place = 1;
        for (Fighter fighter : board) {
            if (place > ROWS) {
                break;
            }
            say(context, place + ". " + fighter.getUsername()
                    + " — " + fighter.getKills() + " убийств, смертей " + fighter.getDeaths());
            place++;
        }
    }
}
