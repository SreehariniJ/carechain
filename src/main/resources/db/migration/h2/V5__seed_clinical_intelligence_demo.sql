INSERT INTO admissions (patient_id, bed_id, admitted_at, discharged_at, active_record_key) VALUES
(1, 3, DATEADD('DAY', -4, CURRENT_TIMESTAMP), DATEADD('DAY', -1, CURRENT_TIMESTAMP), NULL);

INSERT INTO symptom_assessments (
    patient_id,
    symptom_text,
    suggested_department,
    triage_level,
    confidence_score,
    routing_rationale,
    matched_signals,
    review_status,
    reviewed_department,
    reviewed_triage_level,
    review_note,
    reviewed_by_email,
    submitted_at,
    reviewed_at
) VALUES (
    1,
    'Recurring chest discomfort with shortness of breath while climbing stairs for two days.',
    'Cardiology',
    'ORANGE',
    0.82,
    'Routing favored Cardiology because of urgent indicators such as chest discomfort and shortness of breath.',
    'chest discomfort, shortness of breath',
    'PENDING_REVIEW',
    NULL,
    NULL,
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
    NULL
);
