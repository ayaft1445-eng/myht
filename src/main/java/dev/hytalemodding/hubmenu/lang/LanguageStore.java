package dev.hytalemodding.hubmenu.lang;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Помнит выбранный язык меню для каждого игрока.
 *
 * Выбор хранится в файле languages.properties в папке данных плагина, поэтому
 * переживает перезапуск сервера. Строка в файле — «uuid=ru».
 */
public class LanguageStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String FILE_NAME = "languages.properties";

    private final Map<UUID, MenuLanguage> chosen = new ConcurrentHashMap<>();
    private final Path file;

    public LanguageStore(@Nonnull Path dataDirectory) {
        this.file = dataDirectory.resolve(FILE_NAME);
        this.load();
    }

    /** Выбирал ли игрок язык хоть раз. Если нет — ему показывается окно выбора. */
    public boolean hasChosen(@Nullable UUID playerId) {
        return playerId != null && this.chosen.containsKey(playerId);
    }

    /** Язык игрока или fallback, если выбора ещё не было. */
    @Nonnull
    public MenuLanguage get(@Nullable UUID playerId, @Nonnull MenuLanguage fallback) {
        if (playerId == null) {
            return fallback;
        }
        return this.chosen.getOrDefault(playerId, fallback);
    }

    /** Запоминает выбор и сразу пишет его на диск. */
    public void set(@Nullable UUID playerId, @Nonnull MenuLanguage language) {
        if (playerId == null) {
            return;
        }
        this.chosen.put(playerId, language);
        this.save();
    }

    private void load() {
        if (!Files.isRegularFile(this.file)) {
            return;
        }

        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(this.file)) {
            properties.load(in);
        } catch (IOException exception) {
            LOGGER.at(Level.WARNING).log("[HubMenu] Не удалось прочитать " + this.file + ": " + exception);
            return;
        }

        for (String key : properties.stringPropertyNames()) {
            try {
                this.chosen.put(UUID.fromString(key), MenuLanguage.fromCode(properties.getProperty(key)));
            } catch (IllegalArgumentException exception) {
                LOGGER.at(Level.WARNING).log("[HubMenu] Строка с неверным uuid в " + FILE_NAME + ": " + key);
            }
        }
    }

    private void save() {
        Properties properties = new Properties();
        this.chosen.forEach((playerId, language) -> properties.setProperty(playerId.toString(), language.getCode()));

        try {
            Path parent = this.file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream out = Files.newOutputStream(this.file)) {
                properties.store(out, "HubMenu: выбранный язык меню для каждого игрока");
            }
        } catch (IOException exception) {
            LOGGER.at(Level.WARNING).log("[HubMenu] Не удалось сохранить " + this.file + ": " + exception);
        }
    }
}
