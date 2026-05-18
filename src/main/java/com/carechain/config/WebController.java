package com.carechain.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {

    @GetMapping("/")
    public String home() {
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @GetMapping("/dashboard/patient")
    public String patientDashboard() {
        return "dashboard-patient";
    }

    @GetMapping("/dashboard/doctor")
    public String doctorDashboard() {
        return "dashboard-doctor";
    }

    @GetMapping("/dashboard/admin")
    public String adminDashboard() {
        return "dashboard-admin";
    }

    @GetMapping("/beds/availability")
    public String bedAvailability() {
        return "bed-availability";
    }
}
