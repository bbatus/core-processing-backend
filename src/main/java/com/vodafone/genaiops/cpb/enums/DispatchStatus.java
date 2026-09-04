package com.vodafone.genaiops.cpb.enums;

/**
 * EP'nin ai_dispatch.status alaniyla birebir ayni degerler. CPB yalnizca PENDING -> CLAIMED ->
 * COMPLETED | FAILED gecislerini yazar; CANCELLED/OBSOLETE/ROLLBACK_REQUIRED/ROLLED_BACK EP
 * tarafindan yazilir, CPB bunlari yalnizca OKUR (claim ettigi bir is arada bu duruma dusmus
 * olabilir - bkz. TASARIM_PLANI §9.3).
 */
public enum DispatchStatus {
    PENDING,
    CLAIMED,
    COMPLETED,
    CANCELLED,
    OBSOLETE,
    ROLLBACK_REQUIRED,
    ROLLED_BACK,
    FAILED
}
