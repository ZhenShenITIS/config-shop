package tg.configshop.constants;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum CommandName {
    UNKNOWN("null"),
    FIND_USER("/find_user"),
    BROADCAST("/broadcast"),
    SCHEDULE_NOTIFICATION("/schedule_notification"),
    START("/start");


    private final String commandName;

}
