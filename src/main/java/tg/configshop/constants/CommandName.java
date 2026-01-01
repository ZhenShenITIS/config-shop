package tg.configshop.constants;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum CommandName {
    UNKNOWN("null"),
    START("/start");


    private final String commandName;

}
