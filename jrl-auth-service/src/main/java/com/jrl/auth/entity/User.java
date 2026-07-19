package com.jrl.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A registered account. `customerId` is the value every route/policy this
 * user creates (once the admin service exists) will be tagged with — it's
 * what the rate limiter's metrics group by, and it's deliberately a
 * separate value from the user's own id so it can be rotated or reused
 * independently of the login identity later if needed.
 */
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID customerId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public static User newUser(String name, String email, String passwordHash) {
        User user = new User();
        user.id = UUID.randomUUID();
        user.customerId = UUID.randomUUID();
        user.name = name;
        user.email = email;
        user.passwordHash = passwordHash;
        user.createdAt = Instant.now();
        return user;
    }
}
