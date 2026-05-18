package com.carechain.admin;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DashboardStats {
    private long totalBeds;
    private long occupiedBeds;
    private long availableBeds;
    private long maintenanceBeds;
    private long todayAppointments;
    private long waitingInQueue;
    private long totalPatients;
    private long totalDoctors;
}
