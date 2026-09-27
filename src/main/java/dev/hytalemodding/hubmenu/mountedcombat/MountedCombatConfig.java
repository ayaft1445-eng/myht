package dev.hytalemodding.hubmenu.mountedcombat;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;

/**
 * Настройки боя из седла (файл конфигурации {@code mounted_combat.json} в папке плагина).
 *
 * Значения полей ниже — значения по умолчанию: именно они попадают в файл при первом запуске.
 */
public class MountedCombatConfig {

    /** Как именно наносится удар из седла. */
    public enum Mode {
        /**
         * Запустить на сервере ту же цепочку взаимодействий (interaction chain), которую
         * обычно запускает клиент при клике: урон, эффекты, звук, износ предмета и
         * перезарядка оружия берутся из ассетов самого предмета.
         */
        Interaction,
        /**
         * Простой серверный удар: луч из головы игрока, ближайшая живая цель получает
         * фиксированный урон из {@code Damage} и отбрасывание. Работает даже если
         * клиент полностью игнорирует цепочки взаимодействий во время езды.
         */
        Damage
    }

    public static final BuilderCodec<MountedCombatConfig> CODEC = BuilderCodec.builder(
                    MountedCombatConfig.class, MountedCombatConfig::new)
            .append(
                    new KeyedCodec<>("Enabled", Codec.BOOLEAN),
                    (config, value) -> config.enabled = value,
                    config -> config.enabled
            )
            .documentation("Включить бой из седла.")
            .add()
            .<Mode>append(
                    new KeyedCodec<>("Mode", new EnumCodec<>(Mode.class)),
                    (config, value) -> config.mode = value,
                    config -> config.mode
            )
            .documentation("Interaction — родная атака предмета, Damage — простой серверный удар.")
            .add()
            .append(
                    new KeyedCodec<>("AllowSecondary", Codec.BOOLEAN),
                    (config, value) -> config.allowSecondary = value,
                    config -> config.allowSecondary
            )
            .documentation("Обрабатывать правую кнопку мыши (блок щитом, стрельба) — только в режиме Interaction.")
            .add()
            .append(
                    new KeyedCodec<>("CooldownMs", Codec.INTEGER),
                    (config, value) -> config.cooldownMs = value,
                    config -> config.cooldownMs
            )
            .documentation("Минимальная пауза между ударами из седла в миллисекундах (защита от автокликера).")
            .add()
            .append(
                    new KeyedCodec<>("Range", Codec.FLOAT),
                    (config, value) -> config.range = value,
                    config -> config.range
            )
            .documentation("Дистанция удара в блоках (режим Damage).")
            .add()
            .append(
                    new KeyedCodec<>("Damage", Codec.FLOAT),
                    (config, value) -> config.damage = value,
                    config -> config.damage
            )
            .documentation("Урон одного удара (режим Damage).")
            .add()
            .append(
                    new KeyedCodec<>("DamageCauseId", Codec.STRING),
                    (config, value) -> config.damageCauseId = value,
                    config -> config.damageCauseId
            )
            .documentation("Идентификатор ассета DamageCause для удара (режим Damage).")
            .add()
            .append(
                    new KeyedCodec<>("KnockbackStrength", Codec.FLOAT),
                    (config, value) -> config.knockbackStrength = value,
                    config -> config.knockbackStrength
            )
            .documentation("Сила отбрасывания цели, 0 — выключить (режим Damage).")
            .add()
            .append(
                    new KeyedCodec<>("ProtectMount", Codec.BOOLEAN),
                    (config, value) -> config.protectMount = value,
                    config -> config.protectMount
            )
            .documentation("Не давать игроку попадать по собственной лошади (режим Damage).")
            .add()
            .append(
                    new KeyedCodec<>("HitPlayers", Codec.BOOLEAN),
                    (config, value) -> config.hitPlayers = value,
                    config -> config.hitPlayers
            )
            .documentation("Разрешить удары по другим игрокам (режим Damage).")
            .add()
            .append(
                    new KeyedCodec<>("Debug", Codec.BOOLEAN),
                    (config, value) -> config.debug = value,
                    config -> config.debug
            )
            .documentation("Писать в консоль каждый клик, пойманный во время езды.")
            .add()
            .build();

    private boolean enabled = true;
    private Mode mode = Mode.Interaction;
    private boolean allowSecondary = false;
    private int cooldownMs = 400;
    private float range = 4.0F;
    private float damage = 6.0F;
    private String damageCauseId = "Physical";
    private float knockbackStrength = 6.0F;
    private boolean protectMount = true;
    private boolean hitPlayers = true;
    private boolean debug = false;

    private MountedCombatConfig() {
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public Mode getMode() {
        return this.mode == null ? Mode.Interaction : this.mode;
    }

    public boolean isAllowSecondary() {
        return this.allowSecondary;
    }

    public int getCooldownMs() {
        return Math.max(0, this.cooldownMs);
    }

    public float getRange() {
        return Math.max(0.5F, this.range);
    }

    public float getDamage() {
        return Math.max(0.0F, this.damage);
    }

    public String getDamageCauseId() {
        return this.damageCauseId == null || this.damageCauseId.isEmpty() ? "Physical" : this.damageCauseId;
    }

    public float getKnockbackStrength() {
        return Math.max(0.0F, this.knockbackStrength);
    }

    public boolean isProtectMount() {
        return this.protectMount;
    }

    public boolean isHitPlayers() {
        return this.hitPlayers;
    }

    public boolean isDebug() {
        return this.debug;
    }
}
