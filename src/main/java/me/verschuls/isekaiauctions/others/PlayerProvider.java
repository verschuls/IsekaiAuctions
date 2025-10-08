package me.verschuls.isekaiauctions.others;

import dev.rollczi.litecommands.context.ContextProvider;
import dev.rollczi.litecommands.context.ContextResult;
import dev.rollczi.litecommands.invocation.Invocation;
import me.verschuls.isekaiauctions.IsekaiAuctions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import static me.verschuls.isekaiauctions.others.Utils.colorize;

public class PlayerProvider implements ContextProvider<CommandSender, Player> {

    @Override
    public ContextResult<Player> provide(Invocation<CommandSender> invocation) {
        if (!(invocation.sender() instanceof Player p))
            return ContextResult.error(colorize(IsekaiAuctions.getInstance().messagesFile.getString("only_player")));
        return ContextResult.ok(()->p);
    }
}
