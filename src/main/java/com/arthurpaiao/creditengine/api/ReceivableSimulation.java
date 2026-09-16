package com.arthurpaiao.creditengine.api;

import com.arthurpaiao.creditengine.api.Contracts.*;

public record ReceivableSimulation(ReceivableView receivable, SimulationView simulation, ExpectedConditions expectedConditions) {}
