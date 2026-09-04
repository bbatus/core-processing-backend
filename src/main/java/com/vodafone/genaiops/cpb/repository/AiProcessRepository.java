package com.vodafone.genaiops.cpb.repository;

import com.vodafone.genaiops.cpb.entity.AiProcess;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiProcessRepository extends JpaRepository<AiProcess, Long> {

    Optional<AiProcess> findByDispatchIdAndIteration(Long dispatchId, Integer iteration);
}
