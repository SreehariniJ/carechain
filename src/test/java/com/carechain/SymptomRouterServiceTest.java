package com.carechain;

import com.carechain.patient.model.Patient;
import com.carechain.triage.TriageModelProperties;
import com.carechain.triage.TriageModelRetrainRequest;
import com.carechain.triage.TriageModelTrainer;
import com.carechain.triage.TriageTrainingExampleRequest;
import com.carechain.triage.SymptomRouterService;
import com.carechain.triage.TriageLevel;
import com.carechain.triage.TriageRecommendation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SymptomRouterServiceTest {

    private final SymptomRouterService symptomRouterService = new SymptomRouterService(
            new TriageModelTrainer(),
            new TriageModelProperties()
    );

    @Test
    void route_shouldPreferPediatricsForChildRespiratorySymptoms() {
        Patient patient = Patient.builder().age(10).build();

        TriageRecommendation recommendation = symptomRouterService.route(
                patient,
                "My child has fever, cough, sore throat, and fatigue for the last two days."
        );

        assertEquals("Pediatrics", recommendation.department());
        assertEquals(TriageLevel.YELLOW, recommendation.triageLevel());
        assertTrue(recommendation.confidenceScore().doubleValue() >= 0.70);
    }

    @Test
    void route_shouldEscalateCantBreathePhraseToEmergency() {
        Patient patient = Patient.builder().age(35).build();

        TriageRecommendation recommendation = symptomRouterService.route(
                patient,
                "I can't breathe properly and I have severe chest pain with dizziness."
        );

        assertEquals("Emergency", recommendation.department());
        assertEquals(TriageLevel.RED, recommendation.triageLevel());
        assertTrue(recommendation.matchedSignals().stream().anyMatch(signal -> signal.contains("unable to breathe")));
    }

    @Test
    void route_shouldRouteDrugRashToDermatology() {
        Patient patient = Patient.builder().age(27).build();

        TriageRecommendation recommendation = symptomRouterService.route(
                patient,
                "I developed an itchy rash with hives on my arms after starting a new medicine."
        );

        assertEquals("Dermatology", recommendation.department());
        assertEquals(TriageLevel.YELLOW, recommendation.triageLevel());
    }

    @Test
    void route_shouldFallbackToGeneralMedicineWhenSimilarityIsWeak() {
        Patient patient = Patient.builder().age(42).build();

        TriageRecommendation recommendation = symptomRouterService.route(
                patient,
                "I have been feeling low on energy with poor appetite and I just feel generally unwell."
        );

        assertEquals("General Medicine", recommendation.department());
        assertTrue(recommendation.confidenceScore().doubleValue() <= 0.62);
        assertTrue(recommendation.routingRationale().contains("weak similarity"));
    }

    @Test
    void retrain_shouldHotSwapActiveModel() {
        TriageModelRetrainRequest request = new TriageModelRetrainRequest();
        request.setModelVersion("triage-runtime-test");
        request.setCorpusLabel("unit-test-upload");
        request.setExamples(List.of(
                new TriageTrainingExampleRequest("Gastroenterology", TriageLevel.YELLOW,
                        "stomach cramps and diarrhea after eating outside food"),
                new TriageTrainingExampleRequest("Gastroenterology", TriageLevel.GREEN,
                        "bloating and stomach discomfort after dairy meals"),
                new TriageTrainingExampleRequest("Cardiology", TriageLevel.ORANGE,
                        "crushing chest pressure with shortness of breath"),
                new TriageTrainingExampleRequest("Cardiology", TriageLevel.YELLOW,
                        "palpitations with dizziness after climbing stairs")
        ));

        symptomRouterService.retrain(request);

        TriageRecommendation recommendation = symptomRouterService.route(
                Patient.builder().age(33).build(),
                "I have stomach cramps and diarrhea after street food."
        );

        assertEquals("Gastroenterology", recommendation.department());
        assertEquals("triage-runtime-test", symptomRouterService.getModelReport().modelVersion());
        assertEquals("unit-test-upload", symptomRouterService.getModelReport().corpusLabel());
    }
}
