# Бой из седла (удар оружием с лошади)

Как сделать, чтобы, сидя на лошади, можно было держать оружие и бить им.

## Почему в ваниле удар с лошади не работает

Это ограничение **клиента**, а не сервера. По декомпилированному серверу 0.6.x видно:

- клик мышью приходит пакетом `MouseInteraction` (ID 111) в
  `InteractionModule.doMouseInteraction(...)`, и там **нет** ни одной проверки на то, что
  игрок в седле: сервер только сверяет активный слот, обновляет `CameraManager` и
  рассылает событие `PlayerMouseButtonEvent`;
- сами удары — это «цепочки взаимодействий» (`InteractionChain`), и **запускает их клиент**,
  синхронизируя пакетом `SyncInteractionChains` (ID 290). Пока игрок едет, клиент держит
  поводья и цепочку атаки не начинает;
- верховая езда лошади — это `NPCMountComponent` на самой лошади, `Player.mountEntityId` у
  игрока и другой `MovementConfig` (только физика передвижения). Ни запрета на предмет в руке,
  ни запрета на урон там нет. Проверок посадки нет и в `InteractionValidation`.

Вывод: клиент переписать с сервера нельзя, **но сам удар сервер выполнить может**. Плагин
ловит клик и выполняет атаку серверной стороной.

## Что делает плагин

Класс `dev.hytalemodding.hubmenu.mountedcombat.MountedCombat` подписывается на
`PlayerMouseButtonEvent`. Если игрок в седле (проверяются и лошадь через
`Player.getMountEntityId()`, и вагонетка/ездовая сущность через `MountedComponent`), плагин
выполняет удар одним из двух способов.

### Режим `Interaction` (по умолчанию)

Сервер запускает ту же цепочку взаимодействий, что и обычный клик с этим предметом:

```java
InteractionManager manager = store.getComponent(ref, InteractionModule.get().getInteractionManagerComponent());
InteractionContext context = InteractionContext.forInteraction(manager, ref, InteractionType.Primary, store);
String rootId = context.getRootInteractionId(InteractionType.Primary);       // берётся из предмета в руке
RootInteraction root = RootInteraction.getAssetMap().getAsset(rootId);
InteractionChain chain = manager.initChain(InteractionType.Primary, context, root, targetNetworkId, blockPosition, false);
manager.queueExecuteChain(chain);
```

Плюсы: урон, эффекты, звуки, износ предмета и перезарядка оружия — родные, из ассетов.
Цель ищет серверный селектор оружия (`RaycastSelector` и т.п.) по повороту головы игрока,
плюс мы передаём цель, которую клиент и так прислал в пакете.

Важная деталь: последний аргумент `initChain` (`forceRemoteSync`) должен быть `false`.
Внутри он становится флагом `requiresClient`, и если его включить, цепочка будет ждать
подтверждения от клиента, который её не начинал, и отменится по таймауту.

### Режим `Damage`

Полностью серверный удар, не зависящий от поведения клиента: луч из головы игрока
(`HeadRotation.getDirection()`), ближайшая живая цель на луче (как в серверном
`RaycastSelector`: сбор сущностей из `SpatialResource` + `CollisionMath.intersectRayAABB`),
затем урон и отбрасывание:

```java
Damage damage = new Damage(new Damage.EntitySource(ref), DamageCause.PHYSICAL, settings.getDamage());
damage.putMetaObject(Damage.HIT_LOCATION, hitPosition);
damage.putMetaObject(Damage.KNOCKBACK_COMPONENT, knockback);
DamageSystems.executeDamage(targetRef, store, damage);
```

Этот режим не тратит прочность оружия и стамину и не смотрит на характеристики предмета —
урон задаётся в конфиге. Зато он срабатывает всегда, когда клик вообще доходит до сервера.

## Конфигурация

Файл `mounted_combat.json` в папке данных плагина (создаётся при первом запуске):

| Поле | По умолчанию | Значение |
|------|--------------|----------|
| `Enabled` | `true` | Включить бой из седла |
| `Mode` | `"Interaction"` | `"Interaction"` или `"Damage"` |
| `AllowSecondary` | `false` | Обрабатывать правую кнопку (блок, стрельба) |
| `CooldownMs` | `400` | Минимальная пауза между ударами |
| `Range` | `4.0` | Дистанция удара, блоки (режим `Damage`) |
| `Damage` | `6.0` | Урон за удар (режим `Damage`) |
| `KnockbackStrength` | `6.0` | Сила отбрасывания, `0` — выключить (режим `Damage`) |
| `ProtectMount` | `true` | Не бить собственную лошадь (режим `Damage`) |
| `HitPlayers` | `true` | Разрешить удары по игрокам (режим `Damage`) |
| `Debug` | `false` | Писать в консоль каждый клик, пойманный в седле |

## Сборка и проверка

```bash
./gradlew setupHytaleDev   # один раз: подготовка окружения
./gradlew build            # jar в build/libs
./gradlew runServer        # локальный сервер с плагином
```

Проверка на сервере:

1. поставьте `"Debug": true` в `mounted_combat.json` и перезапустите сервер;
2. сядьте на лошадь (`F`), возьмите меч и кликните левой кнопкой;
3. в консоли должна появиться строка `[MountedCombat] <ник>: Left в седле, предмет: ...`.

Если строки нет — клиент вообще не присылает клики во время езды. Тогда серверный плагин
бессилен: понадобится клиентская часть (мод/ассет-пак). Если строка есть, а урона нет —
переключите `"Mode": "Damage"`.

## Чего плагин не делает

- Не меняет картинку: как оружие лежит в руках всадника и есть ли замах — решает клиент.
  Урон при этом наносится.
- Не трогает посадку на стул и кровать — только ездовых сущностей.
- Режим `Damage` не расходует прочность и стамину (в режиме `Interaction` всё это работает
  штатно, потому что удар выполняет родная цепочка предмета).

## Где код

- `src/main/java/dev/hytalemodding/hubmenu/mountedcombat/MountedCombat.java` — обработчик клика и оба режима удара;
- `src/main/java/dev/hytalemodding/hubmenu/mountedcombat/MountedCombatConfig.java` — конфиг;
- `src/main/java/dev/hytalemodding/hubmenu/HubMenuPlugin.java` — регистрация в `setup()`.

Пакет `mountedcombat` самодостаточен: его можно скопировать в любой другой плагин и вызвать
`MountedCombat.register(plugin, config)` из `setup()`.
