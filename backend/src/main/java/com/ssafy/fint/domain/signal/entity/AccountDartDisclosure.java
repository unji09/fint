package com.ssafy.fint.domain.signal.entity;

import com.ssafy.fint.domain.account.entity.Account;
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
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "account_dart_disclosures", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"account_id", "dart_disclosure_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountDartDisclosure extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_dart_disclosure_id")
    private Long accountDartDisclosureId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dart_disclosure_id", nullable = false)
    private DartDisclosure dartDisclosure;

    public AccountDartDisclosure(Account account, DartDisclosure dartDisclosure) {
        this.account = account;
        this.dartDisclosure = dartDisclosure;
    }
}
