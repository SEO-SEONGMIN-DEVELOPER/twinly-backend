package com.nidus.twinly.admin.service;

import com.nidus.twinly.admin.dto.result.AdminBatchCostResult;
import com.nidus.twinly.admin.reader.AdminSimulationGroupReader;
import com.nidus.twinly.legal.domain.PolicyKind;
import com.nidus.twinly.legal.service.PolicyCatalog;
import com.nidus.twinly.simulation.client.SimulationBatchCostClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminBatchCostService {

    private final SimulationBatchCostClient simulationBatchCostClient;
    private final AdminSimulationGroupReader adminSimulationGroupReader;
    private final PolicyCatalog policyCatalog;

    public AdminBatchCostResult batchCosts() {
        return AdminBatchCostResult.from(
                simulationBatchCostClient.fetch(),
                adminSimulationGroupReader.participantGenderCounts(policyCatalog.loadRequiredPolicyIds(PolicyKind.PARALLEL_ENTRY))
        );
    }
}
