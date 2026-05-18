package com.carechain;

import com.carechain.triage.TriageLevel;
import com.carechain.triage.TriageModelProperties;
import com.carechain.triage.TriageModelState;
import com.carechain.triage.TriageModelTrainer;
import com.carechain.triage.TriageTrainingExampleRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriageModelTrainerTest {

    private final TriageModelTrainer triageModelTrainer = new TriageModelTrainer();
    private final TriageModelProperties triageModelProperties = new TriageModelProperties();

    @Test
    void trainFromResource_shouldProduceEvaluationReport() {
        TriageModelState modelState = triageModelTrainer.trainFromResource(triageModelProperties);

        assertEquals("local-triage-v2", modelState.report().modelVersion());
        assertEquals(36, modelState.report().exampleCount());
        assertTrue(modelState.report().featureCount() > 0);
        assertEquals(modelState.report().exampleCount(), modelState.report().evaluation().sampleCount());
        assertTrue(modelState.report().evaluation().departmentMatches() >= modelState.report().evaluation().exactMatches());
        assertTrue(modelState.report().evaluation().triageMatches() >= modelState.report().evaluation().exactMatches());
        assertTrue(modelState.report().evaluation().departmentAccuracy().doubleValue() >= 0.0);
        assertTrue(modelState.report().evaluation().departmentAccuracy().doubleValue() <= 1.0);
        assertTrue(modelState.report().evaluation().exactMatchAccuracy().doubleValue() >= 0.0);
        assertTrue(modelState.report().evaluation().exactMatchAccuracy().doubleValue() <= 1.0);
    }

    @Test
    void trainFromExamples_shouldRejectTinyCorpus() {
        List<TriageTrainingExampleRequest> examples = List.of(
                new TriageTrainingExampleRequest("Cardiology", TriageLevel.YELLOW, "palpitations and mild dizziness"),
                new TriageTrainingExampleRequest("Cardiology", TriageLevel.ORANGE, "severe chest pressure and sweating"),
                new TriageTrainingExampleRequest("Cardiology", TriageLevel.GREEN, "routine blood pressure follow up")
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> triageModelTrainer.trainFromExamples(
                        triageModelProperties,
                        "invalid-model",
                        "tiny-upload",
                        examples));

        assertTrue(exception.getMessage().contains("at least 4 labeled examples"));
    }
}
