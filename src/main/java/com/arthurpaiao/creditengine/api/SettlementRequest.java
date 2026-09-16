package com.arthurpaiao.creditengine.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SettlementRequest(@NotNull UUID receivableId, @NotNull @Valid ExpectedConditions expectedConditions) {}
