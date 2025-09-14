# 설화전 플러그인 최소 기능 사양

## A) 도깨비 보스

### 1. 관리자 명령
- `/myth admin spawnboss <이름:도깨비> [hp] [armor] [world] [x y z]`
- `/myth admin bosslist`
- `/myth admin endboss <bossId>`
  - 권한: `myth.admin.*`

### 2. 방송(채팅)
- 소환: `「[방송] 태초의 도깨비가 나타났다!」`
- 처치: `「[방송] <플레이어>가 태초의 도깨비를 쓰러뜨리고 힘을 계승했다!」`

### 3. 기본 동작
- 소환 시 보스바 출력, 체력 및 방어력 적용.
- 마지막 일격을 가한 플레이어를 단일 계승자로 지정.
- 이미 계승 중인 플레이어가 다시 처치해도 갱신만 수행.
- 이벤트 기록 키 예시: `BOSS_SPAWNED`, `BOSS_DEFEATED`, `MYTH_INHERITED`.

## B) 계승 & 소멸

### 1. 계승
- 보스 처치 직후 계승자에게 “도깨비 힘” 부여.
- 상태 플래그: `isInheritor=true`, `powerKey="dokkaebi.core"`.
- 즉시 방송 및 보스바 종료.

### 2. 소멸
- 계승자가 다른 플레이어에게 사망 시 즉시 능력 삭제.
- 방송: `「[방송] <계승자>가 쓰러져 도깨비의 힘이 사라졌다.」`
- 상태 플래그 초기화: `isInheritor=false`, `powerKey=null`.

### 3. 충돌 처리
- 서버 리스타트나 로그인 시 상태 재적용 필요.
- 동일 틱 다중 이벤트 대비 토큰으로 중복 부여/제거 방지.
- 이벤트 기록 키: `MYTH_LOST`.

## C) 부대(Squad)

### 1. 일반 명령
- `/squad create <이름>`
- `/squad invite <플레이어>`
- `/squad accept <부대>`
- `/squad leave`
- `/squad disband`
- `/squad status`
  - 권한: `myth.user.squad.*`

### 2. 규칙
- 부대장은 생성자이며 계승 여부와 무관.
- 최대 인원은 설정값(기본 5).
- 부대원 목록 및 온라인 수 상태표시 제공.
- 우호 피해 옵션을 on/off 설정 가능.

### 3. 알림
- 초대: `「<보낸이>가 <받는이>를 부대에 초대했습니다. /squad accept 로 수락」`
- 가입, 탈퇴, 해산 시 간단 방송 출력.
- 이벤트 기록 키: `SQUAD_CREATED`, `SQUAD_INVITED`, `SQUAD_JOINED`, `SQUAD_LEFT`, `SQUAD_DISBANDED`.

## D) 설정(config.yml 키)
```
boss:
  hp_default: 10000
  armor_default: 50
  name: "태초의 도깨비"
inherit:
  power_key: "dokkaebi.core"
  buffs:
    - "speed:1"
    - "strength:1"
  announce: true
squad:
  max_members: 5
  friendly_fire: false
```

## E) 권한 요약
- `myth.admin.*` — spawnboss, bosslist, endboss
- `myth.user.squad.*` — create, invite, accept, leave, disband, status

## F) 확인 시나리오

### 시나리오 1 — 계승
1. `/myth admin spawnboss 도깨비 10000 50 world 0 80 0`
2. 플레이어 A가 보스 처치 → A가 계승자 지정, 버프 적용, 방송 출력.

### 시나리오 2 — 소멸
1. 계승자 A가 PvP로 사망.
2. 능력 즉시 제거, `isInheritor=false`, 방송 출력.

### 시나리오 3 — 부대
1. 플레이어 B가 `/squad create 늑대들` 실행 후 `/squad invite C`.
2. 플레이어 C가 `/squad accept 늑대들` 후 `/squad status`로 인원 확인.
3. `/squad leave`, `/squad disband` 동작 확인.
