package com.vodafone.genaiops.cpb.repository;

import com.vodafone.genaiops.cpb.entity.AiDispatch;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiDispatchRepository extends JpaRepository<AiDispatch, Long> {

    /**
     * PENDING isleri claim etmek icin: {@code FOR UPDATE SKIP LOCKED} sayesinde birden fazla CPB
     * replikasi ayni satiri almaz — ek bir dagitik kilide (Redis) gerek yok. Cagiran metod
     * {@code @Transactional} olmali (lock, transaction bitene kadar tutulur).
     */
    @Query(value = "SELECT * FROM ai_dispatch WHERE status = 'PENDING' ORDER BY created_at "
            + "LIMIT :batchSize FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<AiDispatch> findPendingForUpdateSkipLocked(@Param("batchSize") int batchSize);

    /** Claim timeout reaper: uzun sure CLAIMED'de kalmis (worker cokmus olabilir) isleri bulur. */
    @Query("SELECT d FROM AiDispatch d WHERE d.status = com.vodafone.genaiops.cpb.enums.DispatchStatus.CLAIMED "
            + "AND d.claimedAt < :threshold")
    List<AiDispatch> findStaleClaimed(@Param("threshold") LocalDateTime threshold);
}
