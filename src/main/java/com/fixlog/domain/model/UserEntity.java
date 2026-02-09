package com.fixlog.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

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

    @Column(name = "user_status", length = 10)
    private String userStatus;

    @Column(name = "last_login_time")
    private LocalDateTime lastLoginTime;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    protected UserEntity() {
    }

    public UserEntity(String userId, String userName, String email) {
        this.userId = userId;
        this.userName = userName;
        this.email = email;
        this.userStatus = "ACTIVE";
        this.createTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
        this.lastLoginTime = LocalDateTime.now();
    }

    public void updateLoginInfo(String userName) {
        this.userName = userName;
        this.lastLoginTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();

        if (!"ACTIVE".equals(this.userStatus)) {
            this.userStatus = "ACTIVE";
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

    public String getUserStatus() {
        return userStatus;
    }

    public LocalDateTime getLastLoginTime() {
        return lastLoginTime;
    }
}