package tg.configshop.repositories;

import java.util.List;

public interface AdministratorRepository {
    boolean isAdmin (Long userId);
    List<Long> getAdminIdList ();
}
