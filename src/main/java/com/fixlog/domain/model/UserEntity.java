package com.fixlog.domain.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "`fixLog_user`")
public class UserEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "user_id", columnDefinition = "CHAR(36)", updatable = false, nullable = false)
	private UUID userId;

	@Column(name = "user_name", length = 50, nullable = false)
	private String userName;

	@Column(name = "email", length = 100, nullable = false, unique = true)
	private String email;

	@Enumerated(EnumType.STRING)
	@Column(name = "user_status", length = 20)
	private UserStatus userStatus;

	@Column(name = "last_login_at")
	private Instant lastLoginAt;

	@Column(name = "create_at", updatable = false)
	private Instant createAt;

	@Column(name = "update_at")
	private Instant updateAt;

	protected UserEntity() {
	}

	public UserEntity(String userName, String email) {
		this.userName = userName;
		this.email = email;
		this.userStatus = UserStatus.ACTIVE;
		this.lastLoginAt = Instant.now();
		this.createAt = Instant.now();
		this.updateAt = Instant.now();
	}

	public void updateLoginInfo(String userName, String email) {
		this.userName = userName;
		this.email = email;
		this.lastLoginAt = Instant.now();
		this.updateAt = Instant.now();

		if (this.userStatus != UserStatus.ACTIVE) {
			this.userStatus = UserStatus.ACTIVE;
		}
	}

	public UUID getUserId() {
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

	public Instant getLastLoginAt() {
		return lastLoginAt;
	}

	public boolean isDeleted() {
		return this.userStatus == UserStatus.WITHDRAW;
	}
}
