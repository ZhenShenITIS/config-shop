package tg.configshop.repositories;

import tg.configshop.constants.DialogStageName;

public interface UserStateRepository {
    DialogStageName get (Long userId);
    void put (Long userId, DialogStageName dialogStageName);
}
