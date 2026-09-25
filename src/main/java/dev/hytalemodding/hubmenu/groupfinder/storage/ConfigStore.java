package dev.hytalemodding.hubmenu.groupfinder.storage;

import dev.hytalemodding.hubmenu.groupfinder.model.GroupFinderConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * Читает и пишет файл настроек поиска группы.
 *
 * Файл: <папка плагина>/groupfinder.json. Если файла нет — создаётся с
 * четырьмя выключенными режимами под карточки меню. Если файл битый —
 * он переименовывается в .broken, а мод поднимается на настройках по
 * умолчанию: сервер не должен падать из-за лишней запятой.
 */
public class ConfigStore {

    private static final String FILE_NAME = "groupfinder.json";

    private final Path file;
    /** Последняя ошибка чтения или записи — панель показывает её админу. */
    private String lastError = "";

    public ConfigStore(Path dataDirectory) {
        this.file = dataDirectory.resolve(FILE_NAME);
    }

    public Path getFile() {
        return this.file;
    }

    public String getLastError() {
        return this.lastError;
    }

    /** Читает файл. При любой беде возвращает настройки по умолчанию. */
    public GroupFinderConfig load() {
        this.lastError = "";
        try {
            if (!Files.exists(this.file)) {
                GroupFinderConfig config = GroupFinderConfig.defaults();
                save(config);
                return config;
            }
            String text = new String(Files.readAllBytes(this.file), StandardCharsets.UTF_8);
            Map<String, Object> json = Json.asObject(Json.parse(text));
            return GroupFinderConfig.fromJson(json);
        } catch (Json.JsonException exception) {
            this.lastError = "файл настроек битый (" + exception.getMessage() + ")";
            quarantine();
            return GroupFinderConfig.defaults();
        } catch (IOException exception) {
            this.lastError = "не читается файл настроек: " + exception.getMessage();
            return GroupFinderConfig.defaults();
        } catch (RuntimeException exception) {
            this.lastError = "странные настройки: " + exception;
            return GroupFinderConfig.defaults();
        }
    }

    /** Пишет файл через временный, чтобы не потерять его при сбое. */
    public boolean save(GroupFinderConfig config) {
        try {
            Path parent = this.file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporary = this.file.resolveSibling(FILE_NAME + ".tmp");
            Files.write(temporary, Json.write(config.toJson()).getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temporary, this.file,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(temporary, this.file, StandardCopyOption.REPLACE_EXISTING);
            }
            this.lastError = "";
            return true;
        } catch (IOException exception) {
            this.lastError = "не сохранить файл настроек: " + exception.getMessage();
            return false;
        }
    }

    /** Отодвигает битый файл в сторону, чтобы админ мог в него заглянуть. */
    private void quarantine() {
        try {
            Files.move(this.file, this.file.resolveSibling(FILE_NAME + ".broken"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // не вышло — не страшно, настройки всё равно поднимутся по умолчанию
        }
    }
}
