package com.mining.bletagtracker.dto;


    import lombok.*;
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public class PersonnelRequest {
    
        private String employeeNumber;
    
        private String idNumber;
    
        private String firstName;
    
        private String surname;
    
        private String phoneNumber;
    
        private String department;
    
        private String positionRole;
    
        private Boolean contractor;

        // Emergency contact

        private String emergencyContactName;

        private String emergencyRelationship;
    
        private String emergencyPhone;
    }
