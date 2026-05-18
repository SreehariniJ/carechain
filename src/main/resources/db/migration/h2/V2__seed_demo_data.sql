INSERT INTO users (email, password, role, created_at) VALUES
('admin@carechain.com', '$2b$12$ZGSDlOBLEeMEnG/mbrA9dOqn72BF944SIF.LcCRHWFDoykHWG3IRq', 'ADMIN', CURRENT_TIMESTAMP),
('dr.smith@carechain.com', '$2b$12$ZGSDlOBLEeMEnG/mbrA9dOqn72BF944SIF.LcCRHWFDoykHWG3IRq', 'DOCTOR', CURRENT_TIMESTAMP),
('dr.patel@carechain.com', '$2b$12$ZGSDlOBLEeMEnG/mbrA9dOqn72BF944SIF.LcCRHWFDoykHWG3IRq', 'DOCTOR', CURRENT_TIMESTAMP),
('dr.jones@carechain.com', '$2b$12$ZGSDlOBLEeMEnG/mbrA9dOqn72BF944SIF.LcCRHWFDoykHWG3IRq', 'DOCTOR', CURRENT_TIMESTAMP),
('patient1@test.com', '$2b$12$ZGSDlOBLEeMEnG/mbrA9dOqn72BF944SIF.LcCRHWFDoykHWG3IRq', 'PATIENT', CURRENT_TIMESTAMP),
('patient2@test.com', '$2b$12$ZGSDlOBLEeMEnG/mbrA9dOqn72BF944SIF.LcCRHWFDoykHWG3IRq', 'PATIENT', CURRENT_TIMESTAMP);

INSERT INTO doctors (user_id, name, specialization, available_days) VALUES
(2, 'Dr. Sarah Smith', 'Cardiology', 'MON,TUE,WED,THU,FRI'),
(3, 'Dr. Raj Patel', 'Orthopedics', 'MON,WED,FRI'),
(4, 'Dr. Emily Jones', 'Pediatrics', 'TUE,THU,SAT');

INSERT INTO patients (user_id, name, age, blood_group, phone) VALUES
(5, 'Alice Johnson', 28, 'O+', '9876543210'),
(6, 'Bob Williams', 45, 'A-', '9876543211');

INSERT INTO wards (name, type, total_beds, available_beds) VALUES
('Emergency Ward', 'EMERGENCY', 8, 5),
('General Ward A', 'GENERAL', 20, 15),
('General Ward B', 'GENERAL', 15, 10),
('ICU', 'ICU', 10, 4),
('Maternity Ward', 'MATERNITY', 12, 8);

INSERT INTO beds (ward_id, bed_number, status) VALUES
(2, 'GA-01', 'AVAILABLE'),
(2, 'GA-02', 'OCCUPIED'),
(2, 'GA-03', 'AVAILABLE'),
(2, 'GA-04', 'AVAILABLE'),
(2, 'GA-05', 'OCCUPIED'),
(2, 'GA-06', 'MAINTENANCE'),
(2, 'GA-07', 'AVAILABLE'),
(2, 'GA-08', 'OCCUPIED'),
(2, 'GA-09', 'AVAILABLE'),
(2, 'GA-10', 'AVAILABLE'),
(4, 'ICU-01', 'OCCUPIED'),
(4, 'ICU-02', 'OCCUPIED'),
(4, 'ICU-03', 'AVAILABLE'),
(4, 'ICU-04', 'OCCUPIED'),
(4, 'ICU-05', 'AVAILABLE'),
(1, 'ER-01', 'AVAILABLE'),
(1, 'ER-02', 'OCCUPIED'),
(1, 'ER-03', 'AVAILABLE'),
(1, 'ER-04', 'OCCUPIED'),
(5, 'MT-01', 'AVAILABLE'),
(5, 'MT-02', 'OCCUPIED'),
(5, 'MT-03', 'AVAILABLE'),
(5, 'MT-04', 'OCCUPIED');

INSERT INTO opd_queue (patient_id, department, token_number, status, joined_at, queue_date) VALUES
(1, 'General Medicine', 1, 'IN_PROGRESS', CURRENT_TIMESTAMP, CURRENT_DATE),
(2, 'General Medicine', 2, 'WAITING', CURRENT_TIMESTAMP, CURRENT_DATE),
(1, 'Cardiology', 1, 'DONE', DATEADD('DAY', -1, CURRENT_TIMESTAMP), DATEADD('DAY', -1, CURRENT_DATE));

INSERT INTO appointments (patient_id, doctor_id, appointment_date, slot, status) VALUES
(1, 1, CURRENT_DATE, '10:00', 'BOOKED'),
(2, 2, CURRENT_DATE, '14:00', 'BOOKED');
