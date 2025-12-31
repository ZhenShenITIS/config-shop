package tg.configshop.repositories.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import tg.configshop.repositories.UserStateRepository;
import tg.configshop.telegram.constants.DialogStageName;
import tg.configshop.telegram.dialogstages.impl.NoneDialogStage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
@Repository
public class MapUserStateRepository implements UserStateRepository {
    private final Map<Long, DialogStageName> userStateMap = new ConcurrentHashMap<>();
    private final NoneDialogStage noneDialogStage;

    @Override
    public DialogStageName get(Long userId) {
        return userStateMap.getOrDefault(userId, noneDialogStage.getDialogStage());
    }

    @Override
    public void put(Long userId, DialogStageName dialogStageName) {
        userStateMap.put(userId, dialogStageName);
    }
}
