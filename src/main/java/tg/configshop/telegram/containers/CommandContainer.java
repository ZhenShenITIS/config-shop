package tg.configshop.telegram.containers;

import com.google.common.collect.ImmutableMap;
import org.springframework.stereotype.Component;
import tg.configshop.telegram.commands.Command;
import tg.configshop.telegram.commands.impl.UnknownCommand;

import java.util.HashMap;
import java.util.List;

@Component
public class CommandContainer {
    private final ImmutableMap<String, Command> commands;
    private final Command unknownCommand;

    public CommandContainer(List<Command> commandList) {
        HashMap<String, Command> map = new HashMap<String, Command>();
        for (Command command : commandList) {
            map.put(command.getCommand().getCommandName(), command);
        }
        commands = ImmutableMap.copyOf(map);
        unknownCommand = new UnknownCommand();
    }

    public Command retrieveCommand(String commandIdentifier) {
        return commands.getOrDefault(commandIdentifier, unknownCommand);
    }
}
