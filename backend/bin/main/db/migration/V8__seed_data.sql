-- =====================================================
-- F!NT B2B Sales CRM - V8 Seed Data
-- V6: dashboard_widgets nullable 반영
-- V7: account_user_assignment, mood enum 반영
-- =====================================================

-- ── 1. tenants ───────────────────────────────────────────────────
INSERT INTO tenants (name, company_code, biz_no, plan)
VALUES ('F!NT', 'FINT301', '123-45-67890', 'ENTERPRISE');

-- ── 2. teams ─────────────────────────────────────────────────────
INSERT INTO teams (tenant_id, name)
VALUES
    (1, '영업 1팀'),
    (1, '영업 2팀');

-- ── 3. users ─────────────────────────────────────────────────────
-- password: test1234 (bcrypt $2a$10$, Spring Security 호환)
INSERT INTO users (team_id, tenant_id, role, emp_no, name, password_hash, email_verified)
VALUES
    (1, 1, 'MEMBER',  'EMP001', '김싸피', '$2a$10$mgJ1oB1oqJaQJa4c9MIVRefeeXrnCIf7q1H6zh1wmng/luOgiY2fy', true),
    (1, 1, 'MANAGER', 'EMP002', '이서연', '$2a$10$mgJ1oB1oqJaQJa4c9MIVRefeeXrnCIf7q1H6zh1wmng/luOgiY2fy', true),
    (2, 1, 'MEMBER',  'EMP003', '박지훈', '$2a$10$mgJ1oB1oqJaQJa4c9MIVRefeeXrnCIf7q1H6zh1wmng/luOgiY2fy', true);

-- ── 4. pipeline_stages ───────────────────────────────────────────
INSERT INTO pipeline_stages (tenant_id, name, sort_order)
VALUES
    (1, '발굴',        1),
    (1, '가치 제안',   2),
    (1, '솔루션 설계', 3),
    (1, '제안 제출',   4),
    (1, '협상',        5),
    (1, '계약 대기',   6),
    (1, '수주',        7);

-- ── 5. accounts (V7: user_id 컬럼 제거됨) ────────────────────────
INSERT INTO accounts (name, industry, biz_no)
VALUES
    ('삼성SDS',    'IT 서비스',     '123-81-00001'),
    ('LG CNS',     'IT 서비스',     '123-81-00002'),
    ('SK텔레콤',   '통신',          '123-81-00003'),
    ('현대자동차', '제조/자동차',   '123-81-00004'),
    ('롯데쇼핑',   '유통/리테일',   '123-81-00005'),
    ('카카오',     '인터넷/플랫폼', '123-81-00006');

-- ── 6. account_user_assignment (V7 신설) ─────────────────────────
-- 삼성SDS, LG CNS, 롯데쇼핑 → EMP001(user_id=1)
-- SK텔레콤, 현대자동차 → EMP002(user_id=2)
-- 카카오 → EMP003(user_id=3)
INSERT INTO account_user_assignment (account_id, user_id)
VALUES
    (1, 1), -- 삼성SDS ← 김싸피
    (2, 1), -- LG CNS ← 김싸피
    (3, 1), -- SK텔레콤 ← 김싸피
    (4, 1), -- 현대자동차 ← 김싸피
    (5, 1), -- 롯데쇼핑 ← 김싸피
    (6, 1); -- 카카오 ← 김싸피

-- ── 7. contacts ──────────────────────────────────────────────────
INSERT INTO contacts (account_id, name, title, phone, email, personality)
VALUES
    (1, '이영희', '구매팀장',     '02-1234-5678', 'lee.yh@samsung.com',  '꼼꼼한 성격, 가격에 민감하며 데이터 기반 의사결정 선호. ROI 수치 제시 필수.'),
    (1, '김민준', 'IT인프라담당', '02-2345-6789', 'kim.mj@samsung.com',  NULL),
    (1, '박지수', 'CFO실',        '02-3456-7890', 'park.js@samsung.com', NULL),
    (2, '최현우', '기술이사',     '02-4567-8901', 'choi.hw@lgcns.com',   NULL),
    (2, '윤서연', '프로젝트PM',   '02-5678-9012', 'yoon.sy@lgcns.com',   NULL),
    (3, '강도현', '플랫폼운영팀', '010-1111-2222','kang.dh@skt.com',     NULL),
    (4, '임채원', '전략기획팀',   '010-2222-3333','lim.cw@hyundai.com',  '장기적 파트너십 선호. 기술 트렌드에 민감하며 사전 리서치 충분히 함.'),
    (4, '송민아', '디지털혁신팀', '010-3333-4444','song.ma@hyundai.com', NULL),
    (5, '정태양', '물류IT팀장',   '010-4444-5555','jung.ty@lotte.com',   NULL),
    (6, '한승우', '인프라팀',     '010-5555-6666','han.sw@kakao.com',    NULL),
    (6, '오지현', '게임개발팀',   '010-6666-7777','oh.jh@kakao.com',     NULL);

-- ── 8. account_external_info (시그널 + 뉴스 더미 데이터) ─────────
INSERT INTO account_external_info (account_id, source, title, content, url, occurred_at)
VALUES
    -- 삼성SDS
    (1, 'DART', '삼성SDS 2025년 4분기 실적 공시',
     '영업이익 전년 동기 대비 12% 증가. IT 서비스 부문 클라우드 전환 수요 지속 증가. 매출 3조 2천억원 달성으로 역대 최고 분기 실적 기록.',
     'https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20250201000001', NOW() - INTERVAL '3 days'),

    (1, 'NEWS', '삼성SDS, 생성형 AI 기반 공급망 최적화 솔루션 출시',
     '삼성SDS가 생성형 AI를 활용한 물류 최적화 솔루션을 출시하며 B2B 시장 공략을 강화한다. 해당 솔루션은 실시간 수요 예측과 자동 재고 조정 기능을 제공하며, 국내 대형 유통사 3곳에 도입 예정이다.',
     'https://news.example.com/samsung-sds-ai-scm', NOW() - INTERVAL '1 day'),

    (1, 'NEWS', '삼성SDS, 공공 클라우드 전환 프로젝트 수주 확대',
     '삼성SDS가 정부24 포털 클라우드 전환 사업을 포함해 총 5건의 공공 클라우드 프로젝트를 신규 수주했다. 공공 부문 클라우드 전환 가속화에 따른 수혜가 지속될 전망이다.',
     'https://news.example.com/samsung-sds-cloud-gov', NOW() - INTERVAL '5 days'),

    -- LG CNS
    (2, 'DART', 'LG CNS 2025년 반기 보고서',
     '상반기 매출 1조 8천억원 달성. 금융·제조 부문 AI 솔루션 수요 증가로 수익성 개선. DX 컨설팅 부문 성장률 전년 대비 23% 상승.',
     'https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20250801000002', NOW() - INTERVAL '5 days'),

    (2, 'NEWS', 'LG CNS, 공공 클라우드 수주 연속 달성',
     'LG CNS가 정부 디지털 전환 프로젝트에서 연속 수주를 달성하며 실적 성장세를 이어가고 있다. 특히 AI 기반 행정 자동화 솔루션이 중앙부처에서 높은 평가를 받고 있다.',
     'https://news.example.com/lgcns-cloud-win', NOW() - INTERVAL '2 days'),

    (2, 'NEWS', 'LG CNS, 스마트팩토리 플랫폼 글로벌 진출 선언',
     'LG CNS가 자사 스마트팩토리 플랫폼 "팩토바"의 동남아시아 시장 진출을 공식 선언했다. 베트남, 태국, 인도네시아 현지 제조사를 우선 타겟으로 2025년 내 10개 레퍼런스 확보를 목표로 한다.',
     'https://news.example.com/lgcns-smartfactory-global', NOW() - INTERVAL '7 days'),

    -- SK텔레콤
    (3, 'NEWS', 'SK텔레콤, B2B IoT 플랫폼 고도화 1000억 투자 발표',
     '5G 기반 산업용 IoT 플랫폼에 1000억 규모 투자 계획을 발표했다. 스마트 팩토리·물류 자동화 수요를 공략하며 B2B 사업 비중을 현재 28%에서 40%로 확대할 예정이다.',
     'https://news.example.com/skt-iot-invest', NOW() - INTERVAL '1 day'),

    (3, 'DART', 'SK텔레콤 2025년 1분기 실적',
     '영업이익 4,870억원으로 시장 기대치 상회. 엔터프라이즈 사업 매출 전년 동기 대비 18% 성장. AI 기반 네트워크 최적화 솔루션 수요 급증.',
     'https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20250501000003', NOW() - INTERVAL '4 days'),

    -- 현대자동차
    (4, 'DART', '현대자동차 2025년 1분기 실적',
     '글로벌 판매량 호조로 영업이익 4조 2천억원 달성. 소프트웨어 정의 차량(SDV) 전환 가속화에 따른 IT 인프라 투자 확대 예정. 전동화 부문 흑자 전환 달성.',
     'https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20250501000004', NOW() - INTERVAL '4 days'),

    (4, 'NEWS', '현대차, 스마트 제조 혁신 로드맵 2030 발표',
     '2025년까지 국내 전 공장 스마트팩토리 전환 목표를 공식 발표했다. MES·ERP 통합 솔루션 발주가 예정되어 있으며, 협력사 포털 시스템 고도화도 함께 추진된다. 총 투자 규모 5,000억원.',
     'https://news.example.com/hyundai-smartfactory', NOW() - INTERVAL '2 days'),

    (4, 'NEWS', '현대차그룹, AI 기반 품질관리 시스템 도입 검토',
     '현대자동차그룹이 AI 기반 실시간 품질 이상 감지 시스템 도입을 검토 중인 것으로 알려졌다. 외부 솔루션 벤더 3곳과 PoC를 진행 중이며, 하반기 내 도입 업체를 확정할 계획이다.',
     'https://news.example.com/hyundai-ai-quality', NOW() - INTERVAL '9 days'),

    -- 카카오
    (6, 'NEWS', '카카오, 엔터프라이즈 AI 데이터 분석 플랫폼 출시 예고',
     '카카오가 B2B 데이터 분석 플랫폼 출시를 앞두고 파트너사 모집에 나섰다. 클라우드 연계 분석, 자연어 기반 리포트 생성 기능을 핵심으로 중소·중견 기업 시장을 집중 공략할 방침이다.',
     'https://news.example.com/kakao-enterprise-ai', NOW() - INTERVAL '1 day'),

    (6, 'DART', '카카오 2025년 1분기 실적 공시',
     '광고 매출 정체에도 엔터프라이즈·커머스 부문 성장으로 영업이익 전분기 대비 8% 개선. B2B SaaS 구독 매출 분기 최초 500억원 돌파.',
     'https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20250501000006', NOW() - INTERVAL '6 days');

-- ── 9. temperature_history (V7: mood enum 사용) ───────────────────
-- mood: RAINBOW(최상) / SUNNY(좋음) / CLOUDY(보통) / RAINY(나쁨) / THUNDER(최악)
INSERT INTO temperature_history (account_id, mood, reason, created_at)
VALUES
    -- 삼성SDS: CLOUDY → CLOUDY → SUNNY → SUNNY → RAINBOW
    (1, 'CLOUDY',  '초기 접촉 단계, 니즈 파악 중',                    NOW() - INTERVAL '90 days'),
    (1, 'CLOUDY',  '첫 미팅 진행, 예산 확인 요청',                    NOW() - INTERVAL '60 days'),
    (1, 'SUNNY',   '제안서 긍정적 검토 의사 표명',                    NOW() - INTERVAL '30 days'),
    (1, 'SUNNY',   '협상 단계 진입, 의사결정권자 참여',               NOW() - INTERVAL '10 days'),
    (1, 'RAINBOW', '계약 조건 최종 조율 중, 계약 체결 임박',         NOW() - INTERVAL '2 days'),
    -- LG CNS: RAINY → CLOUDY → SUNNY
    (2, 'RAINY',   '초기 접촉, 경쟁사 선호 경향 있음',               NOW() - INTERVAL '60 days'),
    (2, 'CLOUDY',  'RFP 수령 및 기술 검토 긍정적',                   NOW() - INTERVAL '20 days'),
    (2, 'SUNNY',   '솔루션 설계 완료, 고객사 반응 긍정적',           NOW() - INTERVAL '5 days'),
    -- SK텔레콤: THUNDER → RAINY → CLOUDY
    (3, 'THUNDER', '이전 사업 실패 경험으로 부정적 인식',            NOW() - INTERVAL '45 days'),
    (3, 'RAINY',   '기술 검토 미팅 완료, 신뢰 회복 중',              NOW() - INTERVAL '15 days'),
    (3, 'CLOUDY',  '담당자 교체 후 관계 재정립',                     NOW() - INTERVAL '3 days'),
    -- 현대자동차: RAINY → RAINY → CLOUDY
    (4, 'RAINY',   '잠재 고객 발굴, 내부 검토 단계',                 NOW() - INTERVAL '30 days'),
    (4, 'RAINY',   '임원 소개 완료, 예산 검토 중',                   NOW() - INTERVAL '7 days'),
    (4, 'CLOUDY',  'PoC 제안 수락',                                   NOW() - INTERVAL '1 day'),
    -- 롯데쇼핑: CLOUDY → SUNNY
    (5, 'CLOUDY',  '이전 거래 관계 기반 재접촉',                     NOW() - INTERVAL '20 days'),
    (5, 'SUNNY',   '니즈 분석 미팅 완료, 빠른 의사결정 예상',       NOW() - INTERVAL '5 days'),
    -- 카카오: SUNNY → RAINBOW
    (6, 'SUNNY',   'POC 완료, 기술·비용 모두 긍정적',                NOW() - INTERVAL '14 days'),
    (6, 'RAINBOW', '최종 계약 조건 합의, 서명 일정 조율 중',         NOW() - INTERVAL '3 days');

-- ── 10. deals ────────────────────────────────────────────────────
INSERT INTO deals (account_id, team_id, title, expected_close, amount, probability, current_pipeline)
VALUES
    (1, 1, 'AI 솔루션 연장 계약',    '2025-06-30', 850000000,  80, '협상'),
    (1, 1, '클라우드 전환 프로젝트', '2025-08-31', 1200000000, 60, '제안 제출'),
    (2, 1, 'ERP 시스템 고도화',      '2025-07-31', 500000000,  70, '솔루션 설계'),
    (3, 2, '5G 기반 IoT 플랫폼',     '2025-09-30', 2000000000, 45, '가치 제안'),
    (4, 2, '스마트 팩토리 MES',      '2025-12-31', 3500000000, 30, '발굴'),
    (6, 1, '데이터 분석 플랫폼',     '2025-05-31', 400000000,  90, '계약 대기');

-- ── 11. deal_contacts ────────────────────────────────────────────
INSERT INTO deal_contacts (deal_id, contact_id, user_id)
VALUES
    (1, 1, 1), (1, 2, 1),
    (2, 3, 1),
    (3, 4, 1), (3, 5, 1),
    (4, 6, 1),
    (5, 7, 1), (5, 8, 1),
    (6, 10, 1);

-- ── 12. activities ───────────────────────────────────────────────
-- V4: type CHECK ('MEETING','CALL','TASK','EMAIL')
-- V5: user_id NOT NULL, stt_status default 'PENDING'
INSERT INTO activities (user_id, deal_id, pipeline_stage_id, type, title, start_at, end_at)
VALUES
    (1, 1, 5, 'MEETING', '협상 최종 조율 미팅',      NOW() - INTERVAL '2 days',  NOW() - INTERVAL '2 days'  + INTERVAL '1 hour'),
    (1, 1, 4, 'MEETING', '제안서 제출 및 설명회',     NOW() - INTERVAL '7 days',  NOW() - INTERVAL '7 days'  + INTERVAL '2 hours'),
    (1, 1, 3, 'MEETING', '솔루션 아키텍처 논의',      NOW() - INTERVAL '14 days', NOW() - INTERVAL '14 days' + INTERVAL '1 hour'),
    (1, 2, 4, 'MEETING', '클라우드 전환 제안서 발표', NOW() - INTERVAL '5 days',  NOW() - INTERVAL '5 days'  + INTERVAL '1 hour'),
    (1, 3, 3, 'MEETING', 'ERP 요구사항 워크샵',       NOW() - INTERVAL '3 days',  NOW() - INTERVAL '3 days'  + INTERVAL '3 hours'),
    (1, 6, 6, 'MEETING', '최종 계약 조건 확인',       NOW() + INTERVAL '2 days',  NOW() + INTERVAL '2 days'  + INTERVAL '1 hour'),
    (1, 4, 2, 'MEETING', '5G IoT 플랫폼 기술 검토',   NOW() + INTERVAL '5 days',  NOW() + INTERVAL '5 days'  + INTERVAL '2 hours');

-- ── 13. ai_suggestions (V3: pipeline_stage_id NOT NULL) ──────────
INSERT INTO ai_suggestions (account_id, pipeline_stage_id, title, content, related_type, reason)
VALUES
    (1, 5, 'ROI 기반 아키텍처 재설계안 리뷰',
     'ROI 최적화 관점에서 아키텍처를 재설계했습니다. 초기 전환 비용을 40% 절감하는 방향을 제안합니다.',
     'account', '{"source":"DART","content":"IT 인프라 유지비용 분석"}'),
    (1, 5, '시장 점유율 확대를 위한 채널 다각화',
     '현재 단일 채널 의존도가 높아 리스크가 있습니다. 파트너사 채널 확대를 통한 매출 다각화를 검토하세요.',
     'account', '{"source":"NEWS","content":"시장 트렌드 분석"}'),
    (2, 3, 'ERP 연동 표준화 제안',
     'LG CNS 내부 시스템과의 API 연동 표준화를 통해 구축 기간을 2주 단축할 수 있습니다.',
     'account', '{"source":"CRM","content":"이전 미팅 기록"}'),
    (6, 6, '계약 전 최종 기술 검증 제안',
     'POC 결과를 바탕으로 계약 전 최종 기술 검증 미팅을 제안합니다. 성공 확률 88%로 예측됩니다.',
     'meeting', '{"source":"CRM","content":"POC 결과 분석"}');

-- ── 14. dashboards ───────────────────────────────────────────────
INSERT INTO dashboards (owner_user_id, title, last_accessed_at)
VALUES
    (1, '기본',     NOW() - INTERVAL '1 day'),
    (1, '제목없음', NOW() - INTERVAL '1 hour');

-- ── 15. dashboard_queries ────────────────────────────────────────
INSERT INTO dashboard_queries (dashboard_id, input_text, result, completed_at)
VALUES
    (1, '이번 분기 딜 현황 분석해줘',
     '{"labels":["발굴","가치제안","솔루션설계","제안제출","협상","계약대기"],"values":[1,1,1,1,1,1]}',
     NOW() - INTERVAL '1 day'),
    (1, '고객사별 무드 추이 보여줘',
     '{"accounts":["삼성SDS","LG CNS","카카오"],"moods":["RAINBOW","SUNNY","RAINBOW"]}',
     NOW() - INTERVAL '2 hours');

-- ── 16. dashboard_widgets (V6: config/position/dashboard_query_id nullable) ──
INSERT INTO dashboard_widgets (dashboard_id, dashboard_query_id, widget_type, title, config, position)
VALUES
    (1, 1, 'BAR_CHART', '분기 딜 파이프라인 현황',
     '{"labels":["발굴","가치제안","솔루션설계","제안제출","협상","계약대기"],"values":[1,1,1,1,1,1]}',
     '{"x":0,"y":0,"w":6,"h":4}'),
    (1, 2, 'LINE_CHART', '고객사 무드 추이',
     '{"accounts":["삼성SDS","LG CNS","카카오"],"moods":["RAINBOW","SUNNY","RAINBOW"]}',
     '{"x":6,"y":0,"w":6,"h":4}');

-- ── 17. dashboard_templates ──────────────────────────────────────
INSERT INTO dashboard_templates (widget_type, title, config, position)
VALUES
    ('BAR_CHART', '기본 대시보드 1', '{"labels":["리드","제안","계약"]}', '{"x":0,"y":0,"w":6,"h":4}'),
    ('TABLE',     '기본 대시보드 2', '{}',                                '{"x":0,"y":0,"w":6,"h":4}'),
    ('PIE',       '기본 대시보드 3', '{}',                                '{"x":0,"y":0,"w":6,"h":4}');
