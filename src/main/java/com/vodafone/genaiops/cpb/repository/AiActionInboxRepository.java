package com.vodafone.genaiops.cpb.repository;

import com.vodafone.genaiops.cpb.entity.AiActionInbox;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiActionInboxRepository extends JpaRepository<AiActionInbox, Long> {

    /** Idempotency kontrolu — ayni source_message_id ile ikinci kez INSERT denenirse bu, retry'i
     * ayirt etmek icin kullanilir (bkz. TASARIM_PLANI §6.6). */
    Optional<AiActionInbox> findBySourceMessageId(UUID sourceMessageId);
}
