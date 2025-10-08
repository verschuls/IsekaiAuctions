package me.verschuls.isekaiauctions.commands;

import dev.rollczi.litecommands.cooldown.CooldownState;
import dev.rollczi.litecommands.invocation.Invocation;
import dev.rollczi.litecommands.message.InvokedMessage;
import dev.rollczi.litecommands.time.DurationParser;
import me.verschuls.isekaiauctions.IsekaiAuctions;
import me.verschuls.isekaiauctions.others.PlaceholderUtil;
import me.verschuls.isekaiauctions.others.Utils;
import org.bukkit.command.CommandSender;

import static me.verschuls.isekaiauctions.others.Utils.colorize;
import static me.verschuls.isekaiauctions.others.Utils.replacePlaceholders;

public class CommandCooldown implements InvokedMessage<CommandSender, String, CooldownState> {

    @Override
    public String get(Invocation<CommandSender> invocation, CooldownState cooldownState) {
        return colorize(replacePlaceholders(IsekaiAuctions.getInstance().messagesFile.getString("command_cooldown"),
                new PlaceholderUtil().addPlaceholder("%delay%",  DurationParser.DATE_TIME_UNITS.format(cooldownState.getRemainingDuration()))));
    }

}

