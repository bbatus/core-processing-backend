package com.vodafone.genaiops.cpb.exception;

/** EP'nin ayni isimli istisnasiyla ayni amac — bir kill-switch kapaliyken bir islemin devam
 * etmesini engellemek icin firlatilir. */
public class FlowDisabledException extends RuntimeException {

    public FlowDisabledException(String message) {
        super(message);
    }
}
