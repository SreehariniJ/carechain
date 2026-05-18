CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('PATIENT', 'DOCTOR', 'ADMIN'))
) ENGINE=InnoDB;

CREATE TABLE wards (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    type VARCHAR(20) NOT NULL,
    total_beds INT NOT NULL,
    available_beds INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_wards_name UNIQUE (name),
    CONSTRAINT chk_wards_type CHECK (type IN ('GENERAL', 'ICU', 'EMERGENCY', 'MATERNITY'))
) ENGINE=InnoDB;

CREATE TABLE patients (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    name VARCHAR(100),
    age INT,
    blood_group VARCHAR(5),
    phone VARCHAR(15),
    PRIMARY KEY (id),
    CONSTRAINT uk_patients_user_id UNIQUE (user_id),
    CONSTRAINT fk_patients_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE doctors (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    name VARCHAR(100),
    specialization VARCHAR(100),
    available_days VARCHAR(100),
    PRIMARY KEY (id),
    CONSTRAINT uk_doctors_user_id UNIQUE (user_id),
    CONSTRAINT fk_doctors_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE beds (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ward_id BIGINT NOT NULL,
    bed_number VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_beds_bed_number UNIQUE (bed_number),
    CONSTRAINT fk_beds_ward FOREIGN KEY (ward_id) REFERENCES wards (id),
    CONSTRAINT chk_beds_status CHECK (status IN ('AVAILABLE', 'OCCUPIED', 'MAINTENANCE'))
) ENGINE=InnoDB;

CREATE INDEX idx_beds_status ON beds (status);

CREATE TABLE admissions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    patient_id BIGINT NOT NULL,
    bed_id BIGINT NOT NULL,
    admitted_at DATETIME(6),
    discharged_at DATETIME(6),
    active_record_key VARCHAR(20),
    PRIMARY KEY (id),
    CONSTRAINT fk_admissions_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_admissions_bed FOREIGN KEY (bed_id) REFERENCES beds (id),
    CONSTRAINT uk_admissions_patient_active UNIQUE (patient_id, active_record_key),
    CONSTRAINT uk_admissions_bed_active UNIQUE (bed_id, active_record_key)
) ENGINE=InnoDB;

CREATE INDEX idx_admissions_patient_discharged ON admissions (patient_id, discharged_at);
CREATE INDEX idx_admissions_bed_discharged ON admissions (bed_id, discharged_at);

CREATE TABLE opd_queue (
    id BIGINT NOT NULL AUTO_INCREMENT,
    patient_id BIGINT NOT NULL,
    department VARCHAR(50) NOT NULL,
    token_number INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    joined_at DATETIME(6),
    queue_date DATE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_opd_queue_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT chk_opd_queue_status CHECK (status IN ('WAITING', 'IN_PROGRESS', 'DONE')),
    CONSTRAINT uk_opd_queue_department_date_token UNIQUE (department, queue_date, token_number)
) ENGINE=InnoDB;

CREATE INDEX idx_opd_queue_patient_joined_at ON opd_queue (patient_id, joined_at);
CREATE INDEX idx_opd_queue_status_department ON opd_queue (status, department, token_number);

CREATE TABLE appointments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    patient_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    appointment_date DATE NOT NULL,
    slot VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_appointments_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_appointments_doctor FOREIGN KEY (doctor_id) REFERENCES doctors (id),
    CONSTRAINT chk_appointments_status CHECK (status IN ('BOOKED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT uk_appointments_doctor_date_slot UNIQUE (doctor_id, appointment_date, slot)
) ENGINE=InnoDB;

CREATE INDEX idx_appointments_patient_date ON appointments (patient_id, appointment_date);
CREATE INDEX idx_appointments_doctor_date ON appointments (doctor_id, appointment_date, status);
