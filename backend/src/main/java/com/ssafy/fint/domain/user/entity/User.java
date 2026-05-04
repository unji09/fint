package com.ssafy.fint.domain.user.entity;

import com.ssafy.fint.domain.tenant.entity.Tenant;
import com.ssafy.fint.global.common.entity.BaseUpdatableEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@AttributeOverride(name = "id", column = @Column(name = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseUpdatableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    private Long teamId;

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

    @Column(nullable = false)
    private int loginFailCount;

    @Column(nullable = false)
    private boolean isDeleted;
}
