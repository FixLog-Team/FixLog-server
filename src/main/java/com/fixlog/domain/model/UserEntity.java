package com.fixlog.domain.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "apj_user", schema = "public")
public class UserEntity {

    @Id
    @Column(name = "user_id", length = 100)
    private String userId;

    @Column(name = "user_name", length = 50)
    private String userName;

    @Column(name = "email", length = 100, nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_status", length = 20)
    private UserStatus userStatus;

    @Column(name = "last_login_time")
    private Instant lastLoginTime;

    @Column(name = "create_time", updatable = false)
    private Instant createTime;

    @Column(name = "update_time")
    private Instant updateTime;

    protected UserEntity() {
    }

    public UserEntity(String userId, String userName, String email) {
        this.userId = userId;
        this.userName = userName;
        this.email = email;
        this.userStatus = UserStatus.ACTIVE;
        this.createTime = Instant.now();
        this.updateTime = Instant.now();
        this.lastLoginTime = Instant.now();
    }

    public void updateLoginInfo(String userName) {
        this.userName = userName;
        this.lastLoginTime = Instant.now();
        this.updateTime = Instant.now();

        if (this.userStatus != UserStatus.ACTIVE) {
            this.userStatus = UserStatus.ACTIVE;
        }
    }

    public String getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public String getEmail() {
        return email;
    }

    public UserStatus getUserStatus() {
        return userStatus;
    }

    public Instant getLastLoginTime() {
        return lastLoginTime;
    }
}