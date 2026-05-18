package com.carechain.triage;

import com.carechain.config.ApiErrorException;
import com.carechain.patient.model.Patient;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

@Service
public class SymptomRouterService {

    private final TriageModelTrainer triageModelTrainer;
    private final TriageModelProperties triageModelProperties;
    private final TriagePredictionEngine predictionEngine;
    private final AtomicReference<TriageModelState> activeModel;

    public SymptomRouterService(TriageModelTrainer triageModelTrainer,
                                TriageModelProperties triageModelProperties) {
        this.triageModelTrainer = triageModelTrainer;
        this.triageModelProperties = triageModelProperties;
        this.predictionEngine = new TriagePredictionEngine(new TriageFeatureExtractor(), triageModelProperties);
        this.activeModel = new AtomicReference<>(triageModelTrainer.trainFromResource(triageModelProperties));
    }

    public TriageRecommendation route(Patient patient, String symptoms) {
        Integer age = patient == null ? null : patient.getAge();
        return predictionEngine.predict(activeModel.get().snapshot(), age, symptoms).recommendation();
    }

    public TriageModelReport getModelReport() {
        return activeModel.get().report();
    }

    public TriageModelReport retrain(TriageModelRetrainRequest request) {
        TriageModelRetrainRequest safeRequest = request == null ? new TriageModelRetrainRequest() : request;
        try {
            TriageModelState retrainedModel = hasExamples(safeRequest)
                    ? triageModelTrainer.trainFromExamples(
                    triageModelProperties,
                    safeRequest.getModelVersion(),
                    safeRequest.getCorpusLabel(),
                    safeRequest.getExamples())
                    : triageModelTrainer.trainFromResource(
                    triageModelProperties,
                    safeRequest.getModelVersion(),
                    safeRequest.getCorpusLabel());
            activeModel.set(retrainedModel);
            return retrainedModel.report();
        } catch (IllegalArgumentException exception) {
            throw ApiErrorException.badRequest(exception.getMessage());
        }
    }

    private boolean hasExamples(TriageModelRetrainRequest request) {
        return request.getExamples() != null && !request.getExamples().isEmpty();
    }
}
