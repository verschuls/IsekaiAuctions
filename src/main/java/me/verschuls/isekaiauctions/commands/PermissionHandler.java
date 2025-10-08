package me.verschuls.isekaiauctions.commands;

import dev.rollczi.litecommands.handler.result.ResultHandlerChain;
import dev.rollczi.litecommands.invocation.Invocation;
import dev.rollczi.litecommands.permission.MissingPermissions;
import dev.rollczi.litecommands.permission.MissingPermissionsHandler;
import me.verschuls.isekaiauctions.others.Utils;
import org.bukkit.command.CommandSender;

public class PermissionHandler implements MissingPermissionsHandler<CommandSender> {

    @Override
    public void handle(Invocation<CommandSender> invocation, MissingPermissions missingPermissions, ResultHandlerChain<CommandSender> chain) {
        Utils.sendMessage(invocation.sender(), "no_permission");
    }
}
