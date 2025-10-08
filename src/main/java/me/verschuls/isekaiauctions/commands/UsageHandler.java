package me.verschuls.isekaiauctions.commands;

import dev.rollczi.litecommands.handler.result.ResultHandlerChain;
import dev.rollczi.litecommands.invalidusage.InvalidUsage;
import dev.rollczi.litecommands.invalidusage.InvalidUsageHandler;
import dev.rollczi.litecommands.invocation.Invocation;
import me.verschuls.isekaiauctions.others.PlaceholderUtil;
import me.verschuls.isekaiauctions.others.Utils;
import org.bukkit.command.CommandSender;

import java.util.List;

public class UsageHandler implements InvalidUsageHandler<CommandSender> {

    @Override
    public void handle(Invocation<CommandSender> invocation, InvalidUsage<CommandSender> result, ResultHandlerChain<CommandSender> chain) {
        CommandSender sender = invocation.sender();
        if (List.of(InvalidUsage.Cause.UNKNOWN_COMMAND, InvalidUsage.Cause.TOO_MANY_ARGUMENTS).contains(result.getCause())) {
            Utils.sendMessage(sender, "invalid_cmd");
            return;
        }
        Utils.sendMessage(sender, "usage", new PlaceholderUtil().addPlaceholder("%usage%", result.getSchematic().first()));
    }
}
