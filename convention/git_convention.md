# 1. 브랜치

dev에서 분기해서 사용

- 티켓별로 작성

```html
**feat/domain/issue-num/task**
ex) feat/fe/S14P31A301-1/user-signup
ex) feat/be/S14P31A301-2/user-signup
ex) feat/app/S14P31A301-3/user-signup
```

### domain 리스트:

- fe: 프론트엔드 작업
- be : 백엔드 작업
- ai: 모델 트레이닝, 추론 로직 (얼굴 인식 등)
- infra: 인프라 작업
- app: 앱 프론트엔드 작업

<aside>
✂️

`Merge`가 완료되면 해당 브랜치 삭제하기

</aside>

# 2. 커밋

| 커밋 유형 | 의미 |
| --- | --- |
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 코드 리팩토링 |
| `test` | 테스트 코드 추가, 수정, 삭제 |
| `chore` | 빌드, 패키지 매니저 관련 수정 (Dockerfile, gradle, sh, yml 등) |
| `docs` | 문서 추가, 수정, 삭제 |
| `!Hotfix` | 급하게 치명적인 버그를 수정할 때 |


```
태그 : <간단한 작업 요약>
feat : 회원가입 DTO 클래스 추가
```

# 3. MR

커밋 메시지 제목과 동일하게 작성합니다.

- 프론트 화면 개발 완료 시에 UI 피드백 가능하도록 이미지, gif, 영상 등 남겨주시고 리뷰어 지정해 주시면 좋을 것 같습니다 👍

**PR 제목 형식 - 추후 수정 예정**

- `[지라-이슈번호] [[대표 태그]]: [PR의 전체 기능 요약]`
- `ex)S14P31A301-63 [feat]: 사용자 프로필 조회 기능 구현`
- 템플릿

    ```
    ## ✅ PR 유형
    
    - [ ] feat: 새로운 기능 추가
    - [ ] fix: 버그 수정
    - [ ] refactor: 기능 변경 없는 코드 개선
    - [ ] docs: 문서 수정
    - [ ] test: 테스트 코드
    - [ ] chore: 설정, 빌드, 기타 작업
    - [ ] !Hotfix: 급하게 치명적인 버그를 수정할 때
    
    ## 🚀 작업 내용
    
    ---
    
    ## 💬 기타 사항 or 추가 코멘트
    
    ## 🎯 Resolve
    - closes S14P31A301-()
    ```
