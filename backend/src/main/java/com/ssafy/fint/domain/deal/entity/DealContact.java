package com.ssafy.fint.domain.deal.entity;

import com.ssafy.fint.domain.account.entity.Contact;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 딜-담당자 연결(append-only).
 * 영업건과 고객사 담당자, 그리고 연결을 만든 사원을 묶는다.
 */
@Entity
@Table(name = "deal_contacts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DealContact extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "deal_contact_id")
    private Long dealContactId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deal_id", nullable = false)
    private Deal deal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contact_id", nullable = false)
    private Contact contact;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder
    private DealContact(Deal deal, Contact contact, User user) {
        this.deal = deal;
        this.contact = contact;
        this.user = user;
    }
}
