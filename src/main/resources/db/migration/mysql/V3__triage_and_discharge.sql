CREATE TABLE symptom_assessments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    patient_id BIGINT NOT NULL,
    symptom_text VARCHAR(2000) NOT NULL,
    suggested_department VARCHAR(80) NOT NULL,
    triage_level VARCHAR(20) NOT NULL,
    confidence_score DECIMAL(5,2) NOT NULL,
    routing_rationale VARCHAR(500),
    matched_signals VARCHAR(500),
    review_status VARCHAR(20) NOT NULL,
    reviewed_department VARCHAR(80),
    reviewed_triage_level VARCHAR(20),
    review_note VARCHAR(500),
    reviewed_by_email VARCHAR(120),
    submitted_at DATETIME(6) NOT NULL,
    reviewed_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_symptom_assessments_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT chk_symptom_assessments_triage_level CHECK (triage_level IN ('RED', 'ORANGE', 'YELLOW', 'GREEN')),
    CONSTRAINT chk_symptom_assessments_review_status CHECK (review_status IN ('AUTO_APPROVED', 'PENDING_REVIEW', 'OVERRIDDEN')),
    CONSTRAINT chk_symptom_assessments_reviewed_triage_level CHECK (reviewed_triage_level IS NULL OR reviewed_triage_level IN ('RED', 'ORANGE', 'YELLOW', 'GREEN'))
) ENGINE=InnoDB;

CREATE INDEX idx_symptom_assessments_patient_submitted ON symptom_assessments (patient_id, submitted_at);
CREATE INDEX idx_symptom_assessments_review_status ON symptom_assessments (review_status, submitted_at);

CREATE TABLE discharge_summaries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    admission_id BIGINT NOT NULL,
    diagnosis VARCHAR(2000) NOT NULL,
    treatment_summary VARCHAR(2000),
    discharge_instructions VARCHAR(2000),
    medications VARCHAR(2000),
    follow_up_date DATE,
    attending_doctor_name VARCHAR(100),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_discharge_summaries_admission UNIQUE (admission_id),
    CONSTRAINT fk_discharge_summaries_admission FOREIGN KEY (admission_id) REFERENCES admissions (id)
) ENGINE=InnoDB;

CREATE INDEX idx_discharge_summaries_updated_at ON discharge_summaries (updated_at);
