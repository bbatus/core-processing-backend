package com.vodafone.genaiops.cpb.enums;

/** EP'nin ai_action_inbox.status alaniyla birebir ayni degerler. CPB yalnizca PENDING yazar; geri
 * kalanini EP'nin ActionApplyService'i (poller) yazar. */
public enum ActionInboxStatus {
    PENDING,
    APPLIED,
    DISCARDED,
    FAILED
}
