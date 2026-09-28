package dev.hytalemodding.hubmenu.mountedcombat;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.ChangeVelocityType;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.MouseButtonState;
import com.hypixel.hytale.protocol.MouseButtonType;
import com.hypixel.hytale.server.core.entity.InteractionChain;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.knockback.KnockbackComponent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.modules.collision.CollisionMath;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import com.hypixel.hytale.builtin.mounts.MountPlugin;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.builtin.mounts.NPCMountComponent;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import org.joml.Vector2d;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Vector4d;

/**
 * Бой из седла.
 *
 * Сервер ничего не запрещает всаднику: удар в седле не проходит потому, что клиент
 * во время езды держит поводья и не запускает цепочку атаки. Поэтому плагин ловит сам
 * клик мышью (пакет MouseInteraction -> {@link PlayerMouseButtonEvent}) и выполняет удар
 * серверной стороной:
 *
 * <ul>
 *   <li>режим {@code Interaction} — запускает ту же цепочку взаимодействий, что и обычный
 *       клик с этим предметом в руке: урон, эффекты, звуки и перезарядка берутся из ассетов;</li>
 *   <li>режим {@code Damage} — сам ищет цель лучом из головы игрока и наносит урон
 *       через {@link DamageSystems}. Не зависит от того, что делает клиент.</li>
 * </ul>
 */
public final class MountedCombat {

    @Nonnull
    private final JavaPlugin plugin;
    @Nonnull
    private final Config<MountedCombatConfig> config;
    /** Время последнего удара по UUID игрока. Обращение идёт из потока мира. */
    @Nonnull
    private final Map<UUID, Long> lastAttackMs = new HashMap<>();

    private MountedCombat(@Nonnull JavaPlugin plugin, @Nonnull Config<MountedCombatConfig> config) {
        this.plugin = plugin;
        this.config = config;
    }

    /** Подписывается на клики мышью. Вызывать из {@code setup()} плагина. */
    public static void register(@Nonnull JavaPlugin plugin, @Nonnull Config<MountedCombatConfig> config) {
        MountedCombat feature = new MountedCombat(plugin, config);
        plugin.getEventRegistry().registerGlobal(PlayerMouseButtonEvent.class, feature::onMouseButton);
    }

    private void onMouseButton(@Nonnull PlayerMouseButtonEvent event) {
        MountedCombatConfig settings = this.config.get();
        if (settings == null || !settings.isEnabled()) {
            return;
        }

        if (event.getMouseButton() == null || event.getMouseButton().state != MouseButtonState.Pressed) {
            return;
        }

        InteractionType interactionType;
        if (event.getMouseButton().mouseButtonType == MouseButtonType.Left) {
            interactionType = InteractionType.Primary;
        } else if (event.getMouseButton().mouseButtonType == MouseButtonType.Right && settings.isAllowSecondary()) {
            interactionType = InteractionType.Secondary;
        } else {
            return;
        }

        Ref<EntityStore> ref = event.getPlayerRef();
        if (!ref.isValid()) {
            return;
        }

        Store<EntityStore> store = ref.getStore();
        Ref<EntityStore> mountRef = findMount(store, ref, event.getPlayer());
        if (mountRef == null) {
            // Игрок не в седле — обычный бой работает сам, вмешиваться не нужно.
            return;
        }

        List<String> mountIdentifiers = collectMountIdentifiers(store, mountRef);
        if (settings.isDebug()) {
            this.plugin.getLogger().at(Level.INFO).log(
                    "[MountedCombat] %s: %s в седле, предмет: %s, маунт: %s",
                    event.getPlayerRefComponent().getUsername(),
                    event.getMouseButton().mouseButtonType.name(),
                    event.getItemInHand() == null ? "нет" : event.getItemInHand().getId(),
                    mountIdentifiers.isEmpty() ? "неизвестен" : String.join(", ", mountIdentifiers)
            );
        }

        if (!matchesConfiguredMount(mountIdentifiers, settings.getMountIds())) {
            return;
        }

        if (!this.consumeCooldown(event.getPlayerRefComponent().getUuid(), settings.getCooldownMs())) {
            return;
        }

        switch (settings.getMode()) {
            case Interaction -> this.runItemInteraction(store, ref, interactionType, event);
            case Damage -> this.dealDirectDamage(store, ref, mountRef, settings);
        }
    }

    /**
     * Возвращает существо, на котором едет игрок, либо {@code null}.
     *
     * Лошадь (NPC-маунт) хранится не в компоненте всадника, а в {@code Player#getMountEntityId()};
     * вагонетка и другие ездовые сущности — в {@link MountedComponent}. Сидение на блоке
     * (стул, кровать) здесь сознательно игнорируется.
     */
    @Nullable
    private static Ref<EntityStore> findMount(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull Player player) {
        int mountNetworkId = player.getMountEntityId();
        if (mountNetworkId != 0) {
            Ref<EntityStore> mountRef = store.getExternalData().getRefFromNetworkId(mountNetworkId);
            if (mountRef != null && mountRef.isValid()) {
                return mountRef;
            }
        }

        if (MountPlugin.getInstance() == null) {
            return null;
        }

        MountedComponent mounted = store.getComponent(ref, MountedComponent.getComponentType());
        if (mounted != null) {
            Ref<EntityStore> mountedTo = mounted.getMountedToEntity();
            if (mountedTo != null && mountedTo.isValid()) {
                return mountedTo;
            }
        }

        return null;
    }

    /**
     * Собирает всё, чем можно опознать маунт: текущую роль NPC, роль до посадки (её подменяют
     * на Empty_Role, когда игрок садится), роль при спавне и модель. Кастомный маунт вроде
     * Npc_Tyrel опознаётся любым из этих значений — какое именно подойдёт, видно в логе при
     * включённом Debug.
     */
    @Nonnull
    private static List<String> collectMountIdentifiers(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> mountRef) {
        List<String> identifiers = new ArrayList<>(4);

        NPCEntity npcEntity = store.getComponent(mountRef, NPCEntity.getComponentType());
        if (npcEntity != null) {
            addIdentifier(identifiers, npcEntity.getRoleName());
            addIdentifier(identifiers, roleName(npcEntity.getSpawnRoleIndex()));
        }

        if (MountPlugin.getInstance() != null) {
            NPCMountComponent npcMount = store.getComponent(mountRef, NPCMountComponent.getComponentType());
            if (npcMount != null) {
                addIdentifier(identifiers, roleName(npcMount.getOriginalRoleIndex()));
            }
        }

        ModelComponent modelComponent = store.getComponent(mountRef, ModelComponent.getComponentType());
        if (modelComponent != null) {
            addIdentifier(identifiers, modelComponent.getModel().getModelAssetId());
            addIdentifier(identifiers, modelComponent.getModel().getModel());
        }

        return identifiers;
    }

    @Nullable
    private static String roleName(int roleIndex) {
        if (roleIndex < 0 || NPCPlugin.get() == null) {
            return null;
        }

        return NPCPlugin.get().getName(roleIndex);
    }

    private static void addIdentifier(@Nonnull List<String> identifiers, @Nullable String value) {
        if (value != null && !value.isEmpty() && !identifiers.contains(value)) {
            identifiers.add(value);
        }
    }

    /** Пустой список в конфиге — любой маунт; иначе сравнение по вхождению подстроки. */
    private static boolean matchesConfiguredMount(@Nonnull List<String> identifiers, @Nonnull String[] mountIds) {
        if (mountIds.length == 0) {
            return true;
        }

        for (String mountId : mountIds) {
            if (mountId == null || mountId.isEmpty()) {
                continue;
            }

            String needle = mountId.toLowerCase(java.util.Locale.ROOT);
            for (String identifier : identifiers) {
                if (identifier.toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                    return true;
                }
            }
        }

        return false;
    }

    /** Запускает родную цепочку взаимодействий предмета в руке (режим Interaction). */
    private void runItemInteraction(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull InteractionType interactionType,
            @Nonnull PlayerMouseButtonEvent event
    ) {
        InteractionManager manager = store.getComponent(ref, InteractionModule.get().getInteractionManagerComponent());
        if (manager == null) {
            return;
        }

        InteractionContext context = InteractionContext.forInteraction(manager, ref, interactionType, store);
        String rootId = context.getRootInteractionId(interactionType);
        if (rootId == null) {
            return;
        }

        RootInteraction root = RootInteraction.getAssetMap().getAsset(rootId);
        if (root == null) {
            this.plugin.getLogger().at(Level.WARNING).log("[MountedCombat] Не найден ассет взаимодействия %s", rootId);
            return;
        }

        // Цель клиент уже посчитал за нас: в пакете приходит сущность и блок под прицелом.
        int targetNetworkId = -1;
        Ref<EntityStore> targetRef = event.getTargetEntityRef();
        if (targetRef != null && targetRef.isValid()) {
            NetworkId networkId = store.getComponent(targetRef, NetworkId.getComponentType());
            if (networkId != null) {
                targetNetworkId = networkId.getId();
            }
        }

        Vector3i targetBlock = event.getTargetBlock();
        BlockPosition blockPosition = targetBlock == null
                ? null
                : new BlockPosition(targetBlock.x, targetBlock.y, targetBlock.z);

        // forceRemoteSync = false: цепочку начал сервер, ждать подтверждения от клиента нельзя,
        // иначе она повиснет и через таймаут отменится.
        InteractionChain chain = manager.initChain(interactionType, context, root, targetNetworkId, blockPosition, false);
        manager.queueExecuteChain(chain);
    }

    /** Ищет цель лучом из головы игрока и наносит урон напрямую (режим Damage). */
    private void dealDirectDamage(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Ref<EntityStore> mountRef,
            @Nonnull MountedCombatConfig settings
    ) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        HeadRotation headRotation = store.getComponent(ref, HeadRotation.getComponentType());
        if (transform == null || headRotation == null) {
            return;
        }

        ModelComponent model = store.getComponent(ref, ModelComponent.getComponentType());
        double eyeHeight = model == null ? 1.6 : model.getModel().getEyeHeight(ref, store);
        Vector3d origin = new Vector3d(transform.getPosition()).add(0.0, eyeHeight, 0.0);
        Vector3d direction = headRotation.getDirection().normalize();

        float range = settings.getRange();
        Vector4d hitPosition = new Vector4d();
        Ref<EntityStore> targetRef = findTarget(store, ref, mountRef, origin, direction, range, settings, hitPosition);
        if (targetRef == null) {
            return;
        }

        // Причина урона берётся по идентификатору ассета: статические поля DamageCause помечены
        // как устаревшие и могут быть null.
        int damageCauseIndex = DamageCause.getAssetMap().getIndex(settings.getDamageCauseId());
        if (damageCauseIndex < 0) {
            this.plugin.getLogger().at(Level.WARNING).log(
                    "[MountedCombat] Не найден DamageCause '%s', удар из седла пропущен", settings.getDamageCauseId());
            return;
        }

        Damage damage = new Damage(new Damage.EntitySource(ref), damageCauseIndex, settings.getDamage());
        damage.putMetaObject(Damage.HIT_LOCATION, hitPosition);

        if (settings.getKnockbackStrength() > 0.0F) {
            KnockbackComponent knockback = store.getComponent(targetRef, KnockbackComponent.getComponentType());
            if (knockback == null) {
                knockback = new KnockbackComponent();
                store.putComponent(targetRef, KnockbackComponent.getComponentType(), knockback);
            }

            knockback.setVelocity(new Vector3d(direction.x, 0.25, direction.z)
                    .normalize()
                    .mul(settings.getKnockbackStrength()));
            knockback.setVelocityType(ChangeVelocityType.Add);
            knockback.setDuration(0.0F);
            damage.putMetaObject(Damage.KNOCKBACK_COMPONENT, knockback);
        }

        DamageSystems.executeDamage(targetRef, store, damage);
    }

    /**
     * Ближайшая живая цель на луче. Логика повторяет серверный {@code RaycastSelector}:
     * собрать сущности около середины луча и проверить пересечение луча с их хитбоксом.
     */
    @Nullable
    private static Ref<EntityStore> findTarget(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Ref<EntityStore> mountRef,
            @Nonnull Vector3d origin,
            @Nonnull Vector3d direction,
            float range,
            @Nonnull MountedCombatConfig settings,
            @Nonnull Vector4d hitPositionOut
    ) {
        Vector3d searchCenter = new Vector3d(direction).mul(range * 0.5).add(origin);
        List<Ref<EntityStore>> candidates = SpatialResource.getThreadLocalReferenceList();
        store.getResource(EntityModule.get().getEntitySpatialResourceType())
                .getSpatialStructure()
                .collect(searchCenter, range, candidates);
        if (settings.isHitPlayers()) {
            store.getResource(EntityModule.get().getPlayerSpatialResourceType())
                    .getSpatialStructure()
                    .collect(searchCenter, range, candidates);
        }

        Ref<EntityStore> bestMatch = null;
        double bestDistanceSq = Double.MAX_VALUE;
        Vector2d minMax = new Vector2d();

        for (Ref<EntityStore> candidate : candidates) {
            if (candidate == null || !candidate.isValid() || candidate.equals(ref)) {
                continue;
            }
            if (settings.isProtectMount() && candidate.equals(mountRef)) {
                continue;
            }
            // Цель должна быть живой: есть характеристики (здоровье) и она ещё не мертва.
            if (store.getComponent(candidate, EntityStatMap.getComponentType()) == null) {
                continue;
            }
            if (store.getComponent(candidate, DeathComponent.getComponentType()) != null) {
                continue;
            }
            if (!settings.isHitPlayers() && store.getComponent(candidate, Player.getComponentType()) != null) {
                continue;
            }

            BoundingBox boundingBox = store.getComponent(candidate, BoundingBox.getComponentType());
            TransformComponent targetTransform = store.getComponent(candidate, TransformComponent.getComponentType());
            if (boundingBox == null || targetTransform == null) {
                continue;
            }

            Vector3d targetPosition = targetTransform.getPosition();
            boolean hit = CollisionMath.intersectRayAABB(
                    origin,
                    direction,
                    targetPosition.x(),
                    targetPosition.y(),
                    targetPosition.z(),
                    boundingBox.getBoundingBox(),
                    minMax
            );
            if (!hit || minMax.x < 0.0 || minMax.x > range) {
                continue;
            }

            double distanceSq = minMax.x * minMax.x;
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                bestMatch = candidate;
                hitPositionOut.set(
                        origin.x + direction.x * minMax.x,
                        origin.y + direction.y * minMax.x,
                        origin.z + direction.z * minMax.x,
                        0.0
                );
            }
        }

        return bestMatch;
    }

    /** Простая защита от спама кликами: не чаще одного удара в {@code cooldownMs}. */
    private boolean consumeCooldown(@Nonnull UUID uuid, int cooldownMs) {
        if (cooldownMs <= 0) {
            return true;
        }

        long now = System.currentTimeMillis();
        Long last = this.lastAttackMs.get(uuid);
        if (last != null && now - last < cooldownMs) {
            return false;
        }

        this.lastAttackMs.put(uuid, now);
        return true;
    }
}
