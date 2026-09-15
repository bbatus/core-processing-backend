package com.vodafone.genaiops.cpb.repository;

import com.vodafone.genaiops.cpb.entity.AiProcess;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiProcessRepository extends JpaRepository<AiProcess, Long> {

    Optional<AiProcess> findByDispatchIdAndIteration(Long dispatchId, Integer iteration);

    /**
     * R5/R6 turunda AI'a geri gonderilecek {@code aiSolutionId}'yi bulur — ayni ticket icin AI'in
     * en son URETTIGI oneri kimligi (rehber §3 adim 6: "ayni ticket, ayni aiSolutionId").
     *
     * <p>Neden dispatch bazli degil ticket bazli: R5 EP tarafinda YENI bir dispatch (yeni version)
     * acar, dolayisiyla onceki turun kaydi farkli bir {@code dispatch_id} altindadir. Ticket bazli
     * en guncel kayda bakmak, R4→R5 zincirini dogru esler.</p>
     *
     * <p>{@code solution_uniqueid IS NOT NULL} filtresi, basarisiz (FAILED) turlari atlar — AI hic
     * oneri uretmeden hata dondurduyse geri gonderilecek bir kimlik de yoktur.</p>
     */
    @Query("SELECT p FROM AiProcess p WHERE p.ticketId = :ticketId AND p.solutionUniqueid IS NOT NULL "
            + "ORDER BY p.version DESC, p.iteration DESC, p.id DESC")
    List<AiProcess> findLatestWithSolution(@Param("ticketId") Long ticketId, Limit limit);

    /** {@link #findLatestWithSolution} icin tek-sonuclu kolaylik sarmalayicisi. */
    default Optional<String> findLatestAiSolutionId(Long ticketId) {
        return findLatestWithSolution(ticketId, Limit.of(1)).stream()
                .findFirst()
                .map(AiProcess::getSolutionUniqueid);
    }
}
