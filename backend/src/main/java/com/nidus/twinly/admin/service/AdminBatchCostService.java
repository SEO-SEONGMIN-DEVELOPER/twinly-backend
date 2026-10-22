package com.nidus.twinly.admin.service;

import com.nidus.twinly.admin.dto.result.AdminBatchCostResult;
import com.nidus.twinly.simulation.client.SimulationBatchCostClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminBatchCostService {

    private final SimulationBatchCostClient simulationBatchCostClient;

    public AdminBatchCostResult batchCosts() {
        return AdminBatchCostResult.from(simulationBatchCostClient.fetch());
    }
}
