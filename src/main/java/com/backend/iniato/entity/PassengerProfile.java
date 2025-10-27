package com.backend.iniato.entity;


import com.backend.iniato.enums.Gender;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassengerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column
    private String fullName;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;



    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
