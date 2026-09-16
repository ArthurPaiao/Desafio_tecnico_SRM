package com.arthurpaiao.creditengine.api;

import com.arthurpaiao.creditengine.application.*;
import com.arthurpaiao.creditengine.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SettlementStatementTest {
    private final SettlementRepository settlements = mock(SettlementRepository.class);
    private final CreditService credit = mock(CreditService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        var service = new SettlementService(settlements, mock(ReceivableRepository.class), mock(AssignorRepository.class), credit);
        mvc = MockMvcBuilders.standaloneSetup(new SettlementController(service)).setControllerAdvice(new ApiErrors()).build();
        when(settlements.findAll(org.mockito.ArgumentMatchers.<Specification<Settlement>>any(), any(Pageable.class)))
                .thenAnswer(call -> new PageImpl<Settlement>(List.of(), call.getArgument(1), 0));
    }

    @Test
    void emptyStatementUsesExistingPageContractAndStableOrder() throws Exception {
        mvc.perform(get("/settlements")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.totalElements").value(0));
        verify(settlements).findAll(org.mockito.ArgumentMatchers.<Specification<Settlement>>any(), argThat((Pageable page) ->
                page.getSort().getOrderFor("settledAt").isDescending() && page.getSort().getOrderFor("id").isDescending()));
        verifyNoInteractions(credit);
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101", "page=2147483647&size=100", "page=abc",
            "currency=EUR", "assignorId=invalid", "from=2026-09-14", "from=2026-09-14T12:00:00",
            "from=2026-02-30T00:00:00Z", "from=2026-09-14T15:00:00.0000001Z",
            "from=2026-09-15T00:00:00Z&to=2026-09-14T00:00:00Z",
            "from=2026-09-14T00:00:00Z&to=2026-09-14T00:00:00Z"})
    void invalidFiltersReturn400WithoutQuerying(String query) throws Exception {
        mvc.perform(get("/settlements?" + query)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists()).andExpect(jsonPath("$.trace").doesNotExist());
        verifyNoInteractions(settlements, credit);
    }

    @Test
    void filtersCanBeCombinedAndOffsetsAreAccepted() throws Exception {
        mvc.perform(get("/settlements").param("from", "2026-09-14T00:00:00-03:00")
                .param("to", "2026-09-15T00:00:00-03:00").param("currency", "USD")
                .param("assignorId", "aebc49b1-1c4a-4f86-bf4e-f0224a9aa007").param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));
    }
}
