# CareChain - Hospital Resource Management System

CareChain is a Spring Boot application for managing hospital operations such as patient records, bed inventory, OPD queues, doctor schedules, and appointments.
It now includes live OPD workflow tracking, websocket-driven realtime updates, tamper-evident audit logging, a local ML-style symptom router with clinician override and admin retraining/reporting, discharge-summary PDF generation, billing previews, and Prometheus/Grafana monitoring.

![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-green?style=flat-square)
![MySQL](https://img.shields.io/badge/MySQL-8.0-blue?style=flat-square)
[![Live Demo](https://img.shields.io/badge/Live%20Demo-carechain.onrender.com-brightgreen?style=flat-square)](https://carechain.onrender.com)

🚀 **Live Deployment:** [https://carechain.onrender.com](https://carechain.onrender.com)

## Tech Stack

| Layer | Technology |
|-------|------------|
| Backend | Java 17, Spring Boot 3, Spring MVC |
| Security | Spring Security + JWT |
| Database | H2 for local development, MySQL 8 for production |
| Migrations | Flyway |
| Frontend | Thymeleaf + Bootstrap 5 + Vanilla JS + STOMP/WebSockets |
| Documents | OpenPDF |
| Observability | Spring Boot Actuator + Micrometer + Prometheus + Grafana |
| Testing | JUnit 5 + Mockito + MockMvc |
| Build | Maven + GitHub Actions |
| Deployment | Docker, Docker Compose |

## Modules

```text
com.carechain/
|- auth/         User registration, login, JWT auth
|- audit/        Hash-chained audit logging and integrity verification
|- triage/       AI-assisted symptom routing and human override workflow
|- patient/      Patient records and OPD queue
|- bed/          Wards, beds, admissions, discharge
|- appointment/  Doctors, schedules, appointment booking
|- discharge/    Discharge summaries, billing previews, PDF exports
|- admin/        Dashboard and operational setup
|- metrics/      Business metrics for triage and discharge workflows
`- config/       Security, JWT filter, CORS, web routes
```

## Local Development

The default profile is development-friendly:

- H2 in-memory database
- schema and demo data loaded through Flyway migrations in `src/main/resources/db/migration/h2`
- local `HttpOnly` auth cookie
- public bed availability page at `/beds/availability`
- live websocket updates for operational dashboards and public bed visibility

### Run locally

```bash
mvn clean test
mvn spring-boot:run
```

The app starts at `http://localhost:8080`.

### Default local users

| Role | Email | Password |
|------|-------|----------|
| Admin | `admin@carechain.com` | `password123` |
| Doctor | `dr.smith@carechain.com` | `password123` |
| Doctor | `dr.patel@carechain.com` | `password123` |
| Patient | `patient1@test.com` | `password123` |
| Patient | `patient2@test.com` | `password123` |

Patients can also self-register from `/register`.

## Production Readiness

The application now uses production-oriented defaults:

- Flyway manages all schema changes for H2 and MySQL
- direct `data.sql` and ad hoc schema bootstrapping are removed
- appointment booking, admissions, and OPD queueing have database-backed uniqueness guarantees
- JWT configuration is validated at startup
- production cookies are secure and can optionally use a configured domain
- Actuator health probes are enabled for liveness and readiness checks
- a container image and Compose deployment recipe are included
- operational views update in real time through Spring WebSockets instead of timed polling
- sensitive mutations are written into a SHA-256 hash chain so admins can detect audit trail tampering
- symptom submissions are auto-routed into departments and triage colors with staff override support
- admins can inspect triage model accuracy, corpus makeup, and retrain the in-memory router from the committed corpus or an uploaded labeled dataset
- discharge summaries and billing packets can be exported as PDFs for completed admissions
- Prometheus metrics and a pre-provisioned Grafana dashboard are included for runtime visibility
- GitHub Actions runs the test suite and builds the Docker image on each push or pull request

### Required environment variables

```bash
CARECHAIN_DB_URL=jdbc:mysql://<host>:3306/carechain?useSSL=true&serverTimezone=UTC
CARECHAIN_DB_USERNAME=<db-user>
CARECHAIN_DB_PASSWORD=<db-password>
CARECHAIN_JWT_SECRET=<base64-encoded-secret>
GRAFANA_ADMIN_USER=<grafana-admin-user>
GRAFANA_ADMIN_PASSWORD=<grafana-admin-password>
```

### Optional environment variables

```bash
PORT=8080
MANAGEMENT_PORT=8081
CARECHAIN_JWT_EXPIRATION=86400000
CARECHAIN_ALLOWED_ORIGINS=https://carechain.example.com
CARECHAIN_COOKIE_DOMAIN=carechain.example.com
CARECHAIN_BOOTSTRAP_ADMIN_EMAIL=admin@carechain.example.com
CARECHAIN_BOOTSTRAP_ADMIN_PASSWORD=<strong-password>
CARECHAIN_BOOTSTRAP_ADMIN_NAME=CareChain Admin
CARECHAIN_DB_MAX_POOL_SIZE=10
CARECHAIN_DB_MIN_IDLE=2
CARECHAIN_BILLING_CURRENCY=INR
CARECHAIN_BILLING_CONSULTATION_FEE=650
CARECHAIN_BILLING_DISCHARGE_FEE=250
CARECHAIN_BILLING_GENERAL_DAILY_RATE=2200
CARECHAIN_BILLING_ICU_DAILY_RATE=9500
CARECHAIN_BILLING_EMERGENCY_DAILY_RATE=4000
CARECHAIN_BILLING_MATERNITY_DAILY_RATE=3500
CARECHAIN_TRIAGE_MODEL_VERSION=local-triage-v2
CARECHAIN_TRIAGE_CORPUS_RESOURCE=triage/training-corpus.csv
CARECHAIN_TRIAGE_NEIGHBOR_COUNT=6
CARECHAIN_TRIAGE_WEAK_MATCH_THRESHOLD=0.14
CARECHAIN_TRIAGE_EMERGENCY_SIMILARITY_THRESHOLD=0.18
JAVA_OPTS=-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError
```

### Generate a JWT secret

PowerShell:

```powershell
[Convert]::ToBase64String([byte[]](1..48 | ForEach-Object { Get-Random -Maximum 256 }))
```

OpenSSL:

```bash
openssl rand -base64 48
```

### Build and run the jar

```bash
mvn clean verify
java -jar target/carechain-1.0.0.jar --spring.profiles.active=prod
```

### Build and run with Docker

```bash
docker build -t carechain:1.0.0 .
docker run --rm -p 8080:8080 \
  -e MANAGEMENT_PORT=8081 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e CARECHAIN_DB_URL=jdbc:mysql://host.docker.internal:3306/carechain?useSSL=true&serverTimezone=UTC \
  -e CARECHAIN_DB_USERNAME=carechain \
  -e CARECHAIN_DB_PASSWORD=change-me \
  -e CARECHAIN_JWT_SECRET=<base64-secret> \
  carechain:1.0.0
```

On Windows, Docker Desktop needs the Linux container backend available. If `docker desktop status` shows `stopped` and `wsl --status` reports that WSL is not installed, install WSL2 first and restart Docker Desktop before running the image.

### Run the full stack with Docker Compose

The repository includes `.env.example` as the variable template and `compose.yml` for a MySQL-backed deployment with Prometheus and Grafana. The compose file now expects explicit production secrets instead of falling back to demo defaults:

```bash
docker compose up --build
```

Grafana is exposed on `http://localhost:3000` and Prometheus on `http://localhost:9090`. The application metrics endpoint stays on the internal management port `8081` for container-to-container scraping.

### Database migrations

- Development migrations live in `src/main/resources/db/migration/h2`
- Production migrations live in `src/main/resources/db/migration/mysql`
- Both profiles run with `spring.jpa.hibernate.ddl-auto=validate`, so schema drift fails fast

### Health and metrics endpoints

```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8081/actuator/health/liveness
curl http://localhost:8081/actuator/health/readiness
curl http://localhost:8081/actuator/prometheus
```

## Pages

| URL | Description | Auth |
|-----|-------------|------|
| `/login` | Login page | No |
| `/register` | Patient registration | No |
| `/dashboard/patient` | Patient dashboard with queue, appointments, and AI triage | PATIENT |
| `/dashboard/doctor` | Doctor dashboard with schedule, discharge editor, and PDF export | DOCTOR |
| `/dashboard/admin` | Admin dashboard with beds, queue, triage oversight, and audit viewer | ADMIN |
| `/beds/availability` | Public bed availability | No |

## REST API

### Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register a patient |
| POST | `/api/auth/login` | Login and receive JWT response |
| POST | `/api/auth/logout` | Clear auth cookie |
| GET | `/api/auth/me` | Current authenticated user |

### Patient and Queue

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/patients/me` | Current patient profile |
| POST | `/api/queue/join/{department}` | Join OPD queue |
| GET | `/api/queue/token/{id}` | Read queue token |
| GET | `/api/queue/me/active` | Current patient's live queue status |

### AI Triage

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/triage/assess` | Submit symptom text for AI-assisted routing |
| GET | `/api/triage/me` | Patient triage history and override status |

### Beds

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/beds/availability` | Ward-level availability |
| GET | `/api/beds/all` | All beds with status |
| POST | `/api/beds/admit/{patientId}` | Admit patient |
| PUT | `/api/beds/discharge/{admissionId}` | Discharge patient |

### Appointments

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/doctors` | List doctors |
| GET | `/api/appointments/slots/{doctorId}/{date}` | Available slots |
| POST | `/api/appointments/book` | Book appointment |
| GET | `/api/appointments/me` | Patient appointments |
| GET | `/api/appointments/schedule` | Doctor schedule |
| PUT | `/api/appointments/{id}/complete` | Mark completed |
| DELETE | `/api/appointments/cancel/{id}` | Cancel appointment |

### Discharge and Billing

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/discharges/recent` | Recently discharged admissions |
| GET | `/api/discharges/{admissionId}` | Discharge summary + billing overview |
| POST | `/api/discharges/{admissionId}/summary` | Save or update discharge summary |
| GET | `/api/discharges/{admissionId}/billing` | Billing preview |
| GET | `/api/discharges/{admissionId}/pdf` | Download discharge PDF |

### Admin

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/dashboard` | Dashboard stats |
| GET | `/api/admin/beds` | Bed grid data |
| GET | `/api/admin/queue` | Active queue |
| POST | `/api/admin/queue/{queueId}/start` | Start the next patient visit |
| POST | `/api/admin/queue/{queueId}/complete` | Complete an in-progress visit |
| GET | `/api/admin/patients` | Patients for admit |
| POST | `/api/admin/admit/{patientId}` | Admit a patient |
| POST | `/api/admin/discharge/{bedId}` | Discharge active admission |
| POST | `/api/admin/doctors` | Create doctor account |
| POST | `/api/admin/wards` | Create ward and beds |
| GET | `/api/admin/triage` | Recent AI triage assessments |
| GET | `/api/admin/triage/model` | Current triage model metadata, corpus stats, and evaluation report |
| POST | `/api/admin/triage/model/retrain` | Retrain the in-memory triage model from the default corpus or a labeled request payload |
| PUT | `/api/admin/triage/{id}/override` | Override department and triage level |
| GET | `/api/admin/audit` | Recent audit events with chain verification status |

### Triage model operations

- The default training corpus lives at `src/main/resources/triage/training-corpus.csv`.
- `GET /api/admin/triage/model` returns model version, training time, example counts, department distribution, and leave-one-out evaluation metrics.
- `POST /api/admin/triage/model/retrain` with `{}` reloads the committed corpus into memory.
- `POST /api/admin/triage/model/retrain` can also accept a labeled dataset:

```json
{
  "modelVersion": "triage-runtime-v3",
  "corpusLabel": "ops-upload-2026-05-17",
  "examples": [
    {
      "department": "Cardiology",
      "triageLevel": "ORANGE",
      "symptoms": "chest pressure with shortness of breath after walking"
    },
    {
      "department": "Pediatrics",
      "triageLevel": "YELLOW",
      "symptoms": "child with fever, cough, sore throat, and fatigue"
    }
  ]
}
```

## Testing

```bash
mvn clean test
```

Current automated coverage includes:

- `AuthServiceTest`
- `PatientServiceTest`
- `BedServiceTest`
- `AppointmentServiceTest`
- `AdminProvisioningServiceTest`
- `AuthControllerIntegrationTest`
- `AppointmentControllerIntegrationTest`
- `AuditTrailIntegrationTest`
- `DischargeWorkflowIntegrationTest`
- `MonitoringIntegrationTest`
- `QueueWorkflowIntegrationTest`
- `RealtimeNotifierTest`
- `SymptomRouterServiceTest`
- `TriageModelTrainerTest`
- `AdminTriageModelIntegrationTest`
- `TriageWorkflowIntegrationTest`

## Project Layout

```text
src/main/java/com/carechain/
src/main/resources/
|- application.yml
|- application-prod.yml
|- db/migration/h2/
|- db/migration/mysql/
|- templates/
`- monitoring/

src/test/java/com/carechain/
Dockerfile
compose.yml
.env.example
.github/workflows/ci.yml
```

## Operational Notes

- Public registration always creates `PATIENT` users only.
- Admins can create doctors and wards from the admin dashboard.
- Admins can run the live OPD queue from the admin dashboard, and patients can monitor their token progress from the patient dashboard.
- Patients can submit free-text symptoms and receive a department recommendation, triage color, and staff-reviewed final route.
- Triage routing uses an offline weighted n-gram similarity model, and admins can inspect or retrain the live in-memory model without redeploying.
- Doctors can save discharge summaries, inspect billing previews, and export a combined discharge PDF for discharged admissions.
- Bed availability, admin operations, doctor schedules, and patient notifications now react to realtime websocket events.
- Prometheus exposes both platform metrics and business counters such as triage assessments and discharge PDF exports.
- Production disables `open-in-view`, so repository queries fetch what controllers need explicitly.
- Flyway is configured with `clean-disabled=true`.
- Health probes are intended for deployment platforms and load balancers.
