package com.ssafy.fint.domain.account.entity;

import com.ssafy.fint.global.common.entity.BaseSoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 고객사(Account).
 * 영업 대상 회사 단위. 책임자(사원) 정보는 account_user_assignment 테이블로 분리됨.
 */
@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account extends BaseSoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "industry", nullable = false, length = 100)
    private String industry;

    @Column(name = "biz_no", length = 20)
    private String bizNo;

    @Builder
    private Account(String name, String industry, String bizNo) {
        this.name = name;
        this.industry = industry;
        this.bizNo = bizNo;
    }

    public void changeName(String name) {
        this.name = name;
    }

    public void changeIndustry(String industry) {
        this.industry = industry;
    }

    public void changeBizNo(String bizNo) {
        this.bizNo = bizNo;
    }
}
