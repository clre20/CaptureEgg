# CaptureEgg
丟雞蛋把生物包成生怪蛋，放出來還是原本那一隻——村民的交易、等級、名稱、背包全部保留。

## 功能特性
- **雞蛋捕捉**：
  - 玩家丟出的雞蛋打中生物，100% 捕捉成對應種類的生怪蛋
  - 雞蛋擊中時不會孵出小雞
  - 不能捕捉時，丟中的當下直接在聊天室說明原因
- **完整保存生物資料**：
  - 村民：職業、等級、經驗、所有交易（含已使用次數）、聲望
  - 名稱、血量、藥水效果、裝備、幼年狀態、品種與花色、馬的速度與跳躍力、寵物主人、羊毛顏色……
  - 背包內容一起封進蛋裡：驢子／騾子的箱子、村民背包、馬鞍、馬鎧
- **名稱同步**：
  - 生物有名稱 → 捕捉蛋的名稱就是生物的名稱
  - 用鐵砧把捕捉蛋改名 → 放出來的生物也是新名稱
- **放出**：
  - 拿捕捉蛋對方塊按右鍵，放出的是包之前那一隻
  - 發射器射出捕捉蛋，同樣放出原本那一隻
  - 對箱子、門等方塊按右鍵照常開啟；蹲下按右鍵才會放出
- **防濫用**：
  - 捕捉蛋不能用在生怪磚上、不能對生物使用、不能對空氣使用
  - 領地中只有地主或有權限的玩家能抓（支援 GriefPrevention、Lands、WorldGuard、Towny、Residence 等，不需額外相依）
  - 別人馴服的寵物不能抓，自己的可以
  - Boss 類生物、盔甲座、NPC 不能抓
  - 單顆捕捉蛋資料有上限，防止塞滿界伏盒的生物變成超大物品造成踢人或區塊損壞
  - 掉在地上的捕捉蛋不會被岩漿、火、爆炸摧毀
- **相容 EggEmAll2 舊蛋**：
  - 從 EggEmAll2 換過來，玩家身上原本的蛋照樣能放出，資料完整保留
  - 1.20.5～1.21.4 時期抓的蛋會自動升級成新格式（名稱、裝備不會跑掉）
  - 舊蛋同樣不能用在生怪磚上
- **自動更新檢查**：
  - 啟動時在背景檢查 GitHub 新版本
  - 有新版本時，管理員登入 3 秒後收到提醒

## 指令與權限
主指令為 `/captureegg`。

| 指令 | 說明 | 執行端 | 權限節點 | 預設 |
|------|------|--------|----------|------|
| `/captureegg` | 顯示說明 | 玩家／主控台 | `captureegg.use` | 所有人 |
| `/captureegg version` | 檢查 GitHub 更新 | 玩家／主控台 | `captureegg.version` | OP |
| `/captureegg reload` | 重新載入 config.yml 與 messages.yml | 玩家／主控台 | `captureegg.reload` | OP |
| （丟雞蛋） | 捕捉生物 | 玩家 | `captureegg.capture` | 所有人 |
| （右鍵捕捉蛋） | 放出生物 | 玩家 | `captureegg.release` | 所有人 |
| — | 可以捕捉別人的寵物 | 玩家 | `captureegg.bypass.tamed` | OP |
| — | 以上全部 | — | `captureegg.*` | OP |

> 指令別名：`/ce`

## 使用範例
### 1. 把村民搬到交易所
1. 對有交易的村民丟一顆雞蛋，地上掉出「村民捕捉蛋」
2. 撿起來帶到交易所
3. 對地板按右鍵，放出來就是原本那位村民，交易一筆都沒少

### 2. 幫寵物取名
1. 捕捉自己的狼
2. 用鐵砧把捕捉蛋改名為「小黑」
3. 放出來的狼就叫「小黑」

### 3. 重新載入設定
```bash
/ce reload
```

## 設定檔
| 欄位 | 說明 |
|------|------|
| `capture.check-protection` | 捕捉前檢查領地／保護插件 |
| `capture.allow-others-pets` | 是否可以捕捉別人的寵物 |
| `capture.prevent-baby` | 是否禁止捕捉幼年生物 |
| `capture.prevent-named` | 是否禁止捕捉已命名的生物 |
| `capture.drop-mode` | `ground` 掉在原地；`inventory` 直接進背包 |
| `capture.deny-message-cooldown` | 拒絕提示的最短間隔（秒） |
| `capture.max-data-kb` | 單顆捕捉蛋的資料上限（KB） |
| `capture.entity-blacklist` | 不能捕捉的生物 |
| `worlds.mode` / `worlds.list` | 世界黑名單或白名單 |
| `release.dispenser` | 發射器是否放出捕捉蛋裡的生物 |
| `compatibility.eggemall2` | 是否可放出 EggEmAll2 的舊蛋 |
| `effects.capture` / `effects.release` | 粒子與音效 |
| `update-checker.check-on-startup` | 啟動時檢查更新 |
| `update-checker.notify-on-join` | 管理員登入提醒 |

所有文字都在 `messages.yml`，可自由修改。缺少的項目會在啟動與 `/ce reload` 時自動補上，並保留原本的值。

## 從 EggEmAll2 轉移
1. 關服，移除 EggEmAll2，放入 CaptureEgg
2. 開服即可，玩家身上的舊蛋直接右鍵放出
3. 舊蛋沒有改過名稱時，生物保留原本的名稱；用鐵砧改過名稱則使用新名稱

> 請不要同時安裝兩個插件。

## 相容性
- Paper 26.1、26.2、26.3（Java 25）
- 不支援 Spigot、Folia

---

# CaptureEgg
Throw an egg to turn a mob into a spawn egg, and get the very same mob back when you release it — villager trades, levels, names and inventories are all kept.

## Features
- **Egg capture**:
  - An egg thrown by a player always captures the mob it hits
  - No chicken hatches from the egg
  - If a mob can't be captured, the player is told why right away
- **Full mob data**:
  - Villagers: profession, level, experience, every trade (including uses) and reputation
  - Name, health, potion effects, equipment, baby state, variants, horse speed and jump, pet owner, wool colour…
  - Inventories are sealed in the egg: donkey/mule chests, villager inventory, saddles, horse armor
- **Name sync**:
  - A named mob gives an egg with the same name
  - Rename the egg on an anvil and the released mob gets the new name
- **Release**:
  - Right-click a block with the egg to release the original mob
  - Dispensers release the original mob too
  - Containers and doors still open normally; sneak to release on them
- **Abuse protection**:
  - Eggs can't be used on spawners, on mobs or in the air
  - In claims, only owners and trusted players can capture (works with GriefPrevention, Lands, WorldGuard, Towny, Residence and more, no hard dependency)
  - Other players' pets can't be captured; your own can
  - Bosses, armor stands and NPCs can't be captured
  - Per-egg data size limit stops oversized items that could kick players or corrupt chunks
  - Dropped capture eggs survive lava, fire and explosions
- **EggEmAll2 compatibility**:
  - Switching from EggEmAll2? Players' existing eggs still release, with all data kept
  - Eggs caught on 1.20.5–1.21.4 are upgraded automatically (names and equipment stay correct)
  - Old eggs are also blocked on spawners
- **Update checker**:
  - Checks GitHub for new versions in the background on startup
  - Admins are notified 3 seconds after joining

## Commands & Permissions
The primary command is `/captureegg`.

| Command | Description | Executor | Permission Node | Default |
|---------|-------------|----------|-----------------|---------|
| `/captureegg` | Show help | Player / Console | `captureegg.use` | Everyone |
| `/captureegg version` | Check GitHub for updates | Player / Console | `captureegg.version` | OP |
| `/captureegg reload` | Reload config.yml and messages.yml | Player / Console | `captureegg.reload` | OP |
| (throw egg) | Capture a mob | Player | `captureegg.capture` | Everyone |
| (right-click egg) | Release a mob | Player | `captureegg.release` | Everyone |
| — | Capture other players' pets | Player | `captureegg.bypass.tamed` | OP |
| — | All of the above | — | `captureegg.*` | OP |

> Command Alias: `/ce`

## Usage Examples
### 1. Move a villager to your trading hall
1. Throw an egg at a villager with trades; a villager capture egg drops
2. Carry it to your trading hall
3. Right-click the floor — the same villager comes out with every trade intact

### 2. Name a pet
1. Capture your own wolf
2. Rename the egg on an anvil to "Shadow"
3. The released wolf is named "Shadow"

### 3. Reload
```bash
/ce reload
```

## Configuration
| Key | Description |
|-----|-------------|
| `capture.check-protection` | Ask claim/protection plugins before capturing |
| `capture.allow-others-pets` | Allow capturing other players' pets |
| `capture.prevent-baby` | Block capturing baby mobs |
| `capture.prevent-named` | Block capturing named mobs |
| `capture.drop-mode` | `ground` drops in place; `inventory` goes to the thrower |
| `capture.deny-message-cooldown` | Minimum seconds between denial messages |
| `capture.max-data-kb` | Max stored data per egg (KB) |
| `capture.entity-blacklist` | Mobs that can't be captured |
| `worlds.mode` / `worlds.list` | World blacklist or whitelist |
| `release.dispenser` | Whether dispensers release captured mobs |
| `compatibility.eggemall2` | Allow releasing old EggEmAll2 eggs |
| `effects.capture` / `effects.release` | Particles and sounds |
| `update-checker.check-on-startup` | Check for updates on startup |
| `update-checker.notify-on-join` | Notify admins on join |

All text lives in `messages.yml`. Missing keys are added automatically on startup and `/ce reload`, keeping your existing values.

## Migrating from EggEmAll2
1. Stop the server, remove EggEmAll2 and add CaptureEgg
2. Start the server — players can release their old eggs right away
3. Old eggs that were never renamed keep the mob's original name; renamed eggs use the new name

> Don't run both plugins at the same time.

## Compatibility
- Paper 26.1, 26.2, 26.3 (Java 25)
- Spigot and Folia are not supported
