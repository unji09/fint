package com.ssafy.fint.domain.tenant.entity;

import com.ssafy.fint.global.common.entity.BaseSoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 팀(테넌트 하위의 조직 단위).
 */
@Entity
@Table(name = "teams")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Team extends BaseSoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "team_id")
    private Long teamId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Builder
    private Team(Tenant tenant, String name) {
        this.tenant = tenant;
        this.name = name;
    }

    public void changeName(String name) {
        this.name = name;
    }
}
