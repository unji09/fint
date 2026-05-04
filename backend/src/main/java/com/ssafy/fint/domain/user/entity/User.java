package com.ssafy.fint.domain.user.entity;

import com.ssafy.fint.domain.tenant.entity.Team;
import com.ssafy.fint.domain.tenant.entity.Tenant;
import com.ssafy.fint.global.common.entity.BaseUpdatableEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/**
 * 사원(User).
 * 테넌트에 소속되며 선택적으로 팀에 속한다.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseUpdatableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(length = 200)
    private String email;

    @Column(length = 30)
    private String empNo;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 200)
    private String passwordHash;

    @Column(nullable = false)
    private boolean emailVerified;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notification_prefs", columnDefinition = "jsonb")
    private Map<String, Object> notificationPrefs;

    @Column(name = "login_fail_count", nullable = false)
    private int loginFailCount;

    @Column(nullable = false)
    private boolean isDeleted;
    @Builder
    private User(
            Tenant tenant,
            Team team,
            String role,
            String email,
            String empNo,
            String name,
            String passwordHash,
            Map<String, Object> notificationPrefs
    ) {
        this.tenant = tenant;
        this.team = team;
        this.role = role;
        this.email = email;
        this.empNo = empNo;
        this.name = name;
        this.passwordHash = passwordHash;
        this.emailVerified = false;
        this.notificationPrefs = notificationPrefs;
        this.loginFailCount = 0;
    }

    public void changeTeam(Team team) {
        this.team = team;
    }

    public void changeRole(String role) {
        this.role = role;
    }

    public void changeName(String name) {
        this.name = name;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void verifyEmail() {
        this.emailVerified = true;
    }

    public void incrementLoginFailCount() {
        this.loginFailCount++;
    }

    public void resetLoginFailCount() {
        this.loginFailCount = 0;
    }

    public void updateNotificationPrefs(Map<String, Object> notificationPrefs) {
        this.notificationPrefs = notificationPrefs;
    }
}
