package tg.configshop.telegram.containers;

import com.google.common.collect.ImmutableMap;
import org.springframework.stereotype.Component;
import tg.configshop.telegram.dialogstages.DialogStage;

import java.util.HashMap;
import java.util.List;


@Component
public class DialogStateContainer {
    private final ImmutableMap<String, DialogStage> dialogStages;

    public DialogStateContainer(List<DialogStage> dialogStageList) {
        HashMap<String, DialogStage> map = new HashMap<>();
        for (DialogStage dialogStage : dialogStageList) {
            map.put(dialogStage.getDialogStage().getDialogStageName(), dialogStage);
        }
        dialogStages = ImmutableMap.copyOf(map);
    }

    public DialogStage retrieveDialogStage (String dialogStageIdentifier) {
        return dialogStages.get(dialogStageIdentifier);
    }

}
