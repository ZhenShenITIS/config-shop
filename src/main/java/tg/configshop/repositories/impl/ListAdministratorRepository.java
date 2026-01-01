package tg.configshop.repositories.impl;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import tg.configshop.repositories.AdministratorRepository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class ListAdministratorRepository implements AdministratorRepository {
    @Value("${ADMIN_IDS}")
    private String admins;

    private List<Long> adminList;

    @PostConstruct
    private void init () {
        String[] adminsStrings = admins.split(",");
        ArrayList<Long> adminList = new ArrayList<>();
        for (String s : adminsStrings) {
            try {
                adminList.add(Long.parseLong(s));
            } catch (NumberFormatException e) {
                throw new RuntimeException("Ошибка парсинга ID администратора: " + s, e);
            }
        }
        this.adminList = adminList;
    }


    @Override
    public boolean isAdmin(Long userId) {
        return adminList.contains(userId);
    }
}
