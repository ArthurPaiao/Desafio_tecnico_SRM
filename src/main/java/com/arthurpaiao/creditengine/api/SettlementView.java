package com.arthurpaiao.creditengine.api;

import com.arthurpaiao.creditengine.api.Contracts.SimulationView;
import java.time.Instant;
import java.util.UUID;

public record SettlementView(UUID id, UUID receivableId, UUID idempotencyKey, UUID assignorId,
                             String assignorName, String titleCode, Instant settledAt, SimulationView calculation) {}
