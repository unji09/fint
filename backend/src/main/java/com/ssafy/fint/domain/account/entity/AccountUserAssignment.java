package com.ssafy.fint.domain.account.entity;

import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 고객사-사원 책임자 매핑 (account ↔ user 다대다).
 * 같은 (account, user) 쌍 중복 방지를 위해 UNIQUE 제약 적용.
 */
@Entity
@Table(
        name = "account_user_assignment",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_aua_account_user",
                columnNames = {"account_id", "user_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountUserAssignment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assignment_id")
    private Long assignmentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder
    private AccountUserAssignment(Account account, User user) {
        this.account = account;
        this.user = user;
    }
}
