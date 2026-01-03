package tg.configshop.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tg.configshop.constants.TopUpSource;
import tg.configshop.model.TopUp;

public interface TopUpRepository extends JpaRepository<TopUp, Long> {
    @Query("""
        SELECT COALESCE(SUM(t.value), 0) 
        FROM TopUp t 
        WHERE t.botUser.id = :userId 
          AND t.topUpSource = :source
    """)
    Long getSumByUserIdAndSource(@Param("userId") Long userId, @Param("source") TopUpSource source);
}
