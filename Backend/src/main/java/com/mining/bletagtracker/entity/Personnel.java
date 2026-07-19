package com.mining.bletagtracker.entity;


import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "personnel")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Personnel {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;



    @Column(unique = true, nullable = false)
    private String employeeNumber;


    @Column(unique = true, nullable = false)
    private String idNumber;

    private String firstName;

    private String surname;

    private String phoneNumber;

    private String department;

    private String positionRole;

    private Boolean contractor;


    @Builder.Default
    private Boolean active = true;


    @OneToOne
    @JoinColumn(name = "tag_serial_number")
    private BleTag bleTag;

    private String emergencyContactName;

    private String emergencyRelationship;

    private String emergencyPhone;
    
}