package com.arthurpaiao.creditengine.application;

import com.arthurpaiao.creditengine.api.ReceivableSimulation;

public class ConditionsChangedException extends RuntimeException {
    private final ReceivableSimulation current;
    public ConditionsChangedException(ReceivableSimulation current) {
        super("Condições alteradas; confira os novos valores e confirme novamente");
        this.current = current;
    }
    public ReceivableSimulation getCurrent() { return current; }
}
