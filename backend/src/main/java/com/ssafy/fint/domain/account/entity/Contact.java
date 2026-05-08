package com.ssafy.fint.domain.account.entity;

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
 * 고객사 담당자(Contact).
 * 고객사 측 인물 정보(이름, 직책, 연락처, 성향 등).
 */
@Entity
@Table(name = "contacts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Contact extends BaseSoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contact_id")
    private Long contactId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "title", length = 100)
    private String title;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "email", length = 200)
    private String email;

    @Column(name = "personality", columnDefinition = "TEXT")
    private String personality;

    @Builder
    private Contact(
            Account account,
            String name,
            String title,
            String phone,
            String email,
            String personality
    ) {
        this.account = account;
        this.name = name;
        this.title = title;
        this.phone = phone;
        this.email = email;
        this.personality = personality;
    }

    public void changeName(String name) {
        this.name = name;
    }

    public void changeTitle(String title) {
        this.title = title;
    }

    public void changePhone(String phone) {
        this.phone = phone;
    }

    public void changeEmail(String email) {
        this.email = email;
    }

    public void changePersonality(String personality) {
        this.personality = personality;
    }

    public void update(String name, String title, String phone,
                       String email, String personality) {
        if (name != null)        changeName(name);
        if (title != null)       changeTitle(title);
        if (phone != null)       changePhone(phone);
        if (email != null)       changeEmail(email);
        if (personality != null) changePersonality(personality);
    }
}
