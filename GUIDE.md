# Myth of Five 플러그인 가이드

## 플러그인 라이프사이클과 구성
* `Mythof5`는 `onEnable` 단계에서 기본 구성을 저장하고, 하드코딩된 폴백을 등록하며, 더블 점프 튜닝을 읽어 기능 매니저를 활성화합니다. 또한 이벤트 리스너와 명령 실행자를 연결하고, 이미 접속해 있는 플레이어에게 지속 상태를 다시 적용합니다.【F:mythof5/src/main/java/me/j17e4eo/mythof5/Mythof5.java†L48-L144】
* 종료 시에는 YAML 기반 데이터 저장소를 기록하고, 보스 바와 전조(omen) 효과를 정리하며, 임시 비행 허용을 회수하는 등 위 단계들을 역순으로 수행합니다.【F:mythof5/src/main/java/me/j17e4eo/mythof5/Mythof5.java†L88-L121】
* 보스 수치, 계승 버프, 분대 제한, 더블 점프 물리 등 기본 게임 플레이 튜닝은 `config.yml`에 선언되어 있으며 `registerConfigDefaults` 내에도 반영되어 있어, 서버 운영자는 한 곳에서 모든 값을 덮어쓸 수 있습니다.【F:mythof5/src/main/java/me/j17e4eo/mythof5/Mythof5.java†L190-L236】【F:mythof5/src/main/resources/config.yml†L1-L45】

## 도깨비 보스 전투
* `BossManager`는 보스 소환 시 관리자가 값을 입력하지 않았을 때 사용할 HP, 방어력, 이름 등의 폴백 값을 구성에서 읽어옵니다.【F:mythof5/src/main/java/me/j17e4eo/mythof5/boss/BossManager.java†L44-L56】
* 보스를 소환하면 지속적인 생명체를 만들고, 속성치를 조정하며, 모든 온라인 플레이어에게 붉은 보스 바를 적용하고, 이벤트를 기록하며, 구성된 표시 이름으로 등장 사실을 알립니다.【F:mythof5/src/main/java/me/j17e4eo/mythof5/boss/BossManager.java†L58-L95】
* 보스 진행도는 `BossInstance`가 동기화하며, 피해 또는 치유 이벤트 이후 바 제목과 퍼센트를 갱신합니다. `BossListener`는 이러한 갱신을 일정에 맞춰 실행하고 사망 처리를 위임합니다.【F:mythof5/src/main/java/me/j17e4eo/mythof5/boss/BossInstance.java†L11-L68】【F:mythof5/src/main/java/me/j17e4eo/mythof5/listener/BossListener.java†L25-L45】
* 보스를 처치하면 킬러가 있으면 신화 계승을 부여하고 파워 측면을 진전시키며, 킬러가 없으면 혈통을 초기화합니다.【F:mythof5/src/main/java/me/j17e4eo/mythof5/boss/BossManager.java†L115-L133】
* 관리 도구는 활성 보스 목록을 노출하고, 중복 이름 소환을 막으며, 전투를 강제로 종료할 수 있도록 지원합니다.【F:mythof5/src/main/java/me/j17e4eo/mythof5/boss/BossManager.java†L111-L210】

## 계승과 변신 시스템
* `InheritManager`는 단일 신화 계승자, 지속 능력치 버프, 구성으로 사용자 지정 가능한 측면별 변신 프로필을 추적합니다.【F:mythof5/src/main/java/me/j17e4eo/mythof5/inherit/InheritManager.java†L44-L112】
* 상태는 `inherit.yml`에 저장되며, 플레이어가 접속하면 힘을 되찾고, 죽으면 킬러에게 계승을 넘기거나 제거하며, 결과를 방송하도록 선택할 수 있습니다.【F:mythof5/src/main/java/me/j17e4eo/mythof5/inherit/InheritManager.java†L122-L200】
* 계승을 부여하면 구성된 버프를 적용하고, 플레이어를 `power_key`로 태그하며, 변신 상태가 있다면 복원하고, 고블린 화염 트리거 아이템을 지급합니다. 계승을 제거하면 해당 버프와 아이템을 회수하며, 보스 승리나 암살 시에도 같은 루틴을 호출하고 테마 메시지를 방송합니다.【F:mythof5/src/main/java/me/j17e4eo/mythof5/inherit/InheritManager.java†L203-L315】【F:mythof5/src/main/java/me/j17e4eo/mythof5/inherit/InheritManager.java†L224-L244】
* 고블린 화염을 떨어뜨리면 아이템을 생성하는 대신 변신 상태를 전환하며, 다른 재료와 함께 제작할 수 없습니다. 아이템 메타데이터는 복제를 막고 힘이 회수될 때 플레이어 인벤토리를 정리하는 데 사용됩니다.【F:mythof5/src/main/java/me/j17e4eo/mythof5/inherit/InheritManager.java†L317-L417】
* 변신은 측면별 프로필에 따라 계승자의 크기, 공격력, 이동 속도를 조정합니다. 활성화 시 플레이어의 빛남(glow) 여부를 기록하고, 전환 시 고유한 입자 및 사운드 효과를 재생합니다.【F:mythof5/src/main/java/me/j17e4eo/mythof5/inherit/InheritManager.java†L473-L672】

## 고블린 측면과 스킬
* `AspectManager`는 다섯 가지 측면을 모두 관리하며, `goblins.yml`에서 계승자, 공유 수혜자, 진행도 추적기를 불러오고, 플레이어가 재접속할 때 체력 조정과 패시브를 복원합니다.【F:mythof5/src/main/java/me/j17e4eo/mythof5/inherit/AspectManager.java†L70-L255】
* 파워는 구성 가능한 보스 이름 키워드 일치로, 스피드는 두 개의 수집 가능한 흔적 아이템으로, 장난(Mischief)은 키워드가 들어 있는 계약 아이템으로, 화염은 의식 블록에서 촉매를 소모해, 대장간(Forge)은 대장간 구조물 근처에서 촉매와 연료를 소모해 획득합니다.【F:mythof5/src/main/java/me/j17e4eo/mythof5/inherit/AspectManager.java†L119-L420】
* 관리자와 계승자는 `setInheritor`, `clearInheritor`, `sharePower`, `reclaimPower`를 통해 강제로 이전하거나 공유할 수 있으며, 이 과정에서 체력 페널티를 갱신하고 연대기를 기록하며, 측면별 최대 체력 기준을 준수합니다.【F:mythof5/src/main/java/me/j17e4eo/mythof5/inherit/AspectManager.java†L475-L575】【F:mythof5/src/main/java/me/j17e4eo/mythof5/inherit/AspectManager.java†L947-L1007】
* 측면 스킬은 키로 조회되며, 계승자와 공유 대상의 재사용 대기시간을 구분하고, 러시 스트라이크 돌진, 퍼슈트 마크 추적, 비전 트위스트 군중 제어, 엠버 지원 폭발, 무기 강화 또는 전설 무기 소환 등 맞춤형 전투/지원 행동을 수행합니다.【F:mythof5/src/main/java/me/j17e4eo/mythof5/inherit/AspectManager.java†L576-L799】
* 패시브 오라는 저항 또는 속도를 부여하고(주기적인 "향기" 힌트 포함), 힘이 공유될 때 최대 체력 페널티를 적용하며, 이에 따라 능력치 수정치를 조정합니다.【F:mythof5/src/main/java/me/j17e4eo/mythof5/inherit/AspectManager.java†L875-L973】
* 플레이어는 각 측면의 진행 힌트—사용 가능한 훅, 남은 수집품, 필요 재료—를 조회할 수 있으며, 이는 `Messages` 키를 통해 현지화됩니다.【F:mythof5/src/main/java/me/j17e4eo/mythof5/inherit/AspectManager.java†L1029-L1145】
* `GoblinAspect`는 파워, 스피드, 장난, 화염, 대장간 측면에 대한 서사적 분위기, 획득 방법, 스킬 목록을 문서화하여 명령과 메뉴가 일관된 설명을 렌더링할 수 있도록 합니다.【F:mythof5/src/main/java/me/j17e4eo/mythof5/inherit/aspect/GoblinAspect.java†L15-L156】

## 플레이어용 고블린 명령어
* `/goblin`은 스킬 사용, 스킬 목록, 스토리 중심 정보 패널, 의식 진행도, 측면 공유 및 회수, 장난 계약 또는 대장간 의식 트리거에 대한 바로가기를 지원하며, 보유 스킬과 온라인 플레이어 범위에 맞춘 탭 완성을 제공합니다.【F:mythof5/src/main/java/me/j17e4eo/mythof5/command/GoblinCommand.java†L33-L304】

## 유물 시스템
* 유물 소유권은 `relics.yml`에 저장되며, `RelicManager`는 유물을 부여하거나 제거하고, 획득을 방송하며, 결정론적 융합 레시피를 확인하고, 모든 획득 및 융합을 연대기에 기록합니다.【F:mythof5/src/main/java/me/j17e4eo/mythof5/relic/RelicManager.java†L34-L166】
* `RelicType`은 각 설화에서 영감을 받은 유물을 안정적인 키, 표시 이름, 효과 요약과 함께 열거하며, `RelicFusion`은 재료 세트를 결과 업그레이드와 매칭합니다.【F:mythof5/src/main/java/me/j17e4eo/mythof5/relic/RelicType.java†L8-L51】【F:mythof5/src/main/java/me/j17e4eo/mythof5/relic/RelicFusion.java†L9-L35】
* `/relic list`는 플레이어의 현재 수집품을 표시하고 `/relic fusions`는 매니저가 유지하는 데이터를 사용해 가능한 레시피를 나열합니다.【F:mythof5/src/main/java/me/j17e4eo/mythof5/command/RelicCommand.java†L28-L95】

## 분대 시스템
* `SquadManager`는 YAML에서 분대와 대기 중인 초대를 불러오고, 구성 가능한 최대 인원 제한을 적용하며, 맵 간에 멤버십 및 아군 사격(friendly fire) 메타데이터를 동기화합니다.【F:mythof5/src/main/java/me/j17e4eo/mythof5/squad/SquadManager.java†L41-L400】
* 플레이어 명령을 통해 분대 생성, 초대, 수락, 탈퇴, 해산, 상태 조회가 가능하며, 분대 명령은 별도의 권한 검사 없이 모든 플레이어에게 개방되어 있습니다.【F:mythof5/src/main/java/me/j17e4eo/mythof5/command/SquadCommand.java†L31-L149】
* `SquadListener`는 설정에서 PvP를 허용하지 않는 한 분대원 간의 아군 사격을 차단하고, 공격이 무효화되면 공격자에게 메시지를 보냅니다.【F:mythof5/src/main/java/me/j17e4eo/mythof5/listener/SquadListener.java†L21-L44】【F:mythof5/src/main/java/me/j17e4eo/mythof5/Mythof5.java†L75-L76】【F:mythof5/src/main/resources/config.yml†L38-L40】

## 연대기와 전조
* `ChronicleManager`는 `chronicle.yml`에 타임스탬프가 있는 서사 로그를 유지하고, 목격자를 태그하며, 항목을 저장하고, 필요할 때 최근 이야기를 포맷합니다. 연대기 이벤트는 계승, 유물, 스킬, 전조 등 유형별 카테고리를 가집니다.【F:mythof5/src/main/java/me/j17e4eo/mythof5/chronicle/ChronicleManager.java†L35-L131】【F:mythof5/src/main/java/me/j17e4eo/mythof5/chronicle/ChronicleEventType.java†L4-L15】
* `OmenManager`는 STARSHIFT, GHOST_FIRE, SKYBREAK 단계를 트리거하며, 분위기 있는 텍스트를 방송하고, 연대기에 기록하고, 배경 효과(음악 큐, 입자, 폭풍)를 적용하며, 플러그인이 종료될 때 정리합니다.【F:mythof5/src/main/java/me/j17e4eo/mythof5/omens/OmenManager.java†L33-L110】【F:mythof5/src/main/java/me/j17e4eo/mythof5/omens/OmenStage.java†L3-L7】

## 이동 강화와 플레이어 이벤트
* `PlayerListener`는 접속/퇴장/사망 시 보스 바, 계승, 측면 패시브를 동기화하고, 흔적 아이템 획득 및 의식 상호작용을 측면 시스템으로 전달하며, 고블린 화염 드랍을 가로채 변신을 전환합니다.【F:mythof5/src/main/java/me/j17e4eo/mythof5/listener/PlayerListener.java†L48-L108】
* 이 리스너는 구성 가능한 더블 점프도 구현하여, 생존/어드벤처 플레이어가 지상에 있을 때 비행을 회복하고, 이를 소비해 설정된 수직/전방 속도로 도약하며, 로켓 음향을 재생합니다. 활공/크리에이티브 모드에서는 토글이 억제됩니다.【F:mythof5/src/main/java/me/j17e4eo/mythof5/listener/PlayerListener.java†L110-L200】【F:mythof5/src/main/java/me/j17e4eo/mythof5/Mythof5.java†L239-L253】【F:mythof5/src/main/resources/config.yml†L41-L45】

## 관리 도구와 접근
* `/myth admin`은 보스 소환/목록/종료, 계승 강제 변경, 유물 부여 또는 제거, 최근 연대기 항목 출력, 전조 트리거, 밸런스 표 참조 덤프 등의 하위 명령을 제공합니다.【F:mythof5/src/main/java/me/j17e4eo/mythof5/command/MythAdminCommand.java†L64-L425】
* myth, squad, goblin, relic, hunter, seal 명령은 `plugin.yml`에 등록된 그대로 제공되며, 실행 시 별도의 권한 검증 없이 모든 플레이어가 하위 명령을 사용할 수 있습니다.【F:mythof5/src/main/resources/plugin.yml†L1-L24】【F:mythof5/src/main/java/me/j17e4eo/mythof5/command/MythAdminCommand.java†L63-L86】【F:mythof5/src/main/java/me/j17e4eo/mythof5/command/SquadCommand.java†L31-L149】【F:mythof5/src/main/java/me/j17e4eo/mythof5/command/GoblinCommand.java†L33-L119】【F:mythof5/src/main/java/me/j17e4eo/mythof5/command/RelicCommand.java†L28-L95】【F:mythof5/src/main/java/me/j17e4eo/mythof5/hunter/command/HunterCommand.java†L49-L119】【F:mythof5/src/main/java/me/j17e4eo/mythof5/hunter/seal/command/SealCommand.java†L36-L135】

## 밸런스 참고와 메시징
* `BalanceTable`은 `/myth admin balance`가 빠르게 디자인을 검토할 수 있도록 목표 밸런스 지표 목록을 제공합니다.【F:mythof5/src/main/java/me/j17e4eo/mythof5/balance/BalanceTable.java†L14-L25】
* `Messages`는 `messages.yml`을 로드해 플레이스홀더를 치환하고 누락된 키를 기록하여, 모든 방송과 UI 텍스트가 구성 가능하도록 보장합니다.【F:mythof5/src/main/java/me/j17e4eo/mythof5/config/Messages.java†L22-L88】
