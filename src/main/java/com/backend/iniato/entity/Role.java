package com.backend.iniato.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;

import java.util.*;

@Entity
    @Table(name = "roles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
    public class Role implements GrantedAuthority {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long role_id;

        @Column(unique = true, nullable = false,name = "role_name")
        private String roleName;
        // Getters and setters...



    @ManyToMany(mappedBy = "roles")
    private Set<User> users;

    @Override
    public String getAuthority() {
        return roleName;
    }


}

