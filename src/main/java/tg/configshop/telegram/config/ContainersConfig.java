package tg.configshop.telegram.config;

import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tg.configshop.telegram.callbacks.Callback;
import tg.configshop.telegram.callbacks.impl.NoneCallback;
import tg.configshop.telegram.commands.Command;
import tg.configshop.telegram.commands.impl.StartCommand;
import tg.configshop.telegram.containers.CallbackContainer;
import tg.configshop.telegram.containers.CommandContainer;
import tg.configshop.telegram.containers.DialogStateContainer;
import tg.configshop.telegram.dialogstages.DialogStage;
import tg.configshop.telegram.dialogstages.impl.NoneDialogStage;

@AllArgsConstructor
@Configuration
public class ContainersConfig {
    private StartCommand startCommand;

    @Bean
    public CommandContainer commandContainer() {

        return new CommandContainer(new Command[]{
                startCommand
        });
    }

    private NoneCallback noneCallback;
    @Bean
    public CallbackContainer callbackContainer() {
        return new CallbackContainer(new Callback[]{
            noneCallback
        });
    }

    private NoneDialogStage noneDialogStage;
    @Bean
    public DialogStateContainer dialogStateContainer() {
        return new DialogStateContainer(new DialogStage[]{
                noneDialogStage
        });
    }
}
