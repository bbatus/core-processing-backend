package com.vodafone.genaiops.cpb.enums;

/** EP'nin paylasilan {@code audit_log} tablosuna CPB'nin yazdigi kategoriler — EP'nin kendi
 * kategorileriyle (RULE_EVALUATION, DISPATCH_CREATED, ...) AYNI tabloda, birlikte okunur (bkz.
 * TASARIM_PLANI §11 — ep-frontend dashboard'inda ek gelistirme olmadan gorunur). */
public enum AuditCategory {
    CPB_DISPATCH_CLAIMED,
    CPB_AI_CALL_SUCCEEDED,
    CPB_AI_CALL_FAILED,
    CPB_INBOX_WRITTEN,
    CPB_SKIPPED_KILL_SWITCH,
    CPB_MAX_ITERATIONS_REACHED
}
