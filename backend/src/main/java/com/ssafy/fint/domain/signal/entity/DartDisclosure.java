package com.ssafy.fint.domain.signal.entity;

import com.ssafy.fint.global.common.entity.BaseEntity;
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

@Entity
@Table(name = "dart_disclosures")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DartDisclosure extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dart_disclosure_id")
    private Long dartDisclosureId;

    @Column(name = "corp_code", nullable = false, length = 8)
    private String corpCode;

    @Column(name = "corp_name", nullable = false, length = 200)
    private String corpName;

    @Column(name = "stock_code", length = 6)
    private String stockCode;

    @Column(name = "corp_cls", length = 1)
    private String corpCls;

    @Column(name = "report_nm", nullable = false, length = 500)
    private String reportNm;

    @Column(name = "rcept_no", nullable = false, length = 14)
    private String rceptNo;

    @Column(name = "flr_nm", length = 200)
    private String flrNm;

    @Column(name = "rcept_dt", nullable = false, length = 8)
    private String rceptDt;

    @Column(name = "rm", length = 500)
    private String rm;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "content_summary", columnDefinition = "TEXT")
    private String contentSummary;

    @Builder
    private DartDisclosure(String corpCode, String corpName, String stockCode, String corpCls,
                           String reportNm, String rceptNo, String flrNm, String rceptDt,
                           String rm, String content, String contentSummary) {
        this.corpCode = corpCode;
        this.corpName = corpName;
        this.stockCode = stockCode;
        this.corpCls = corpCls;
        this.reportNm = reportNm;
        this.rceptNo = rceptNo;
        this.flrNm = flrNm;
        this.rceptDt = rceptDt;
        this.rm = rm;
        this.content = content;
        this.contentSummary = contentSummary;
    }
}
