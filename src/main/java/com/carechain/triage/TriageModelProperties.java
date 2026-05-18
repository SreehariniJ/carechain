package com.carechain.triage;

public class TriageModelProperties {

    private String version = "local-triage-v2";
    private String corpusResource = "triage/training-corpus.csv";
    private int neighborCount = 6;
    private double minimumSimilarity = 0.01;
    private double weakMatchThreshold = 0.14;
    private double emergencySimilarityThreshold = 0.18;
    private int pediatricAgeCutoff = 14;
    private double pediatricBoost = 0.18;
    private double pediatricContextBoost = 0.24;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getCorpusResource() {
        return corpusResource;
    }

    public void setCorpusResource(String corpusResource) {
        this.corpusResource = corpusResource;
    }

    public int getNeighborCount() {
        return neighborCount;
    }

    public void setNeighborCount(int neighborCount) {
        this.neighborCount = neighborCount;
    }

    public double getMinimumSimilarity() {
        return minimumSimilarity;
    }

    public void setMinimumSimilarity(double minimumSimilarity) {
        this.minimumSimilarity = minimumSimilarity;
    }

    public double getWeakMatchThreshold() {
        return weakMatchThreshold;
    }

    public void setWeakMatchThreshold(double weakMatchThreshold) {
        this.weakMatchThreshold = weakMatchThreshold;
    }

    public double getEmergencySimilarityThreshold() {
        return emergencySimilarityThreshold;
    }

    public void setEmergencySimilarityThreshold(double emergencySimilarityThreshold) {
        this.emergencySimilarityThreshold = emergencySimilarityThreshold;
    }

    public int getPediatricAgeCutoff() {
        return pediatricAgeCutoff;
    }

    public void setPediatricAgeCutoff(int pediatricAgeCutoff) {
        this.pediatricAgeCutoff = pediatricAgeCutoff;
    }

    public double getPediatricBoost() {
        return pediatricBoost;
    }

    public void setPediatricBoost(double pediatricBoost) {
        this.pediatricBoost = pediatricBoost;
    }

    public double getPediatricContextBoost() {
        return pediatricContextBoost;
    }

    public void setPediatricContextBoost(double pediatricContextBoost) {
        this.pediatricContextBoost = pediatricContextBoost;
    }
}
