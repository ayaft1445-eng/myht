package dev.hytalemodding.hubmenu.lang;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Язык меню. Разметка на каждый язык лежит отдельным файлом с суффиксом кода:
 * Main_ru.ui, Main_en.ui и так далее.
 */
public enum MenuLanguage {

    RU("ru", "#LangRu"),
    EN("en", "#LangEn");

    /** Язык по умолчанию, если игрок ещё ничего не выбрал и клиент молчит. */
    public static final MenuLanguage DEFAULT = RU;

    private final String code;
    private final String buttonId;

    MenuLanguage(@Nonnull String code, @Nonnull String buttonId) {
        this.code = code;
        this.buttonId = buttonId;
    }

    @Nonnull
    public String getCode() {
        return this.code;
    }

    /** Кнопка этого языка в окне выбора. */
    @Nonnull
    public String getButtonId() {
        return this.buttonId;
    }

    /** Путь к разметке окна: «Main» -> «HubMenu/Main_ru.ui». */
    @Nonnull
    public String layout(@Nonnull String window) {
        return "HubMenu/" + window + "_" + this.code + ".ui";
    }

    /**
     * Разбирает код языка вида «ru», «ru-RU», «en_US». Неизвестный код или null
     * дают язык по умолчанию — так игрок в любом случае увидит рабочее меню.
     */
    @Nonnull
    public static MenuLanguage fromCode(@Nullable String code) {
        if (code == null || code.isEmpty()) {
            return DEFAULT;
        }

        String head = code.toLowerCase();
        int separator = head.indexOf('-');
        if (separator < 0) {
            separator = head.indexOf('_');
        }
        if (separator > 0) {
            head = head.substring(0, separator);
        }

        for (MenuLanguage language : values()) {
            if (language.code.equals(head)) {
                return language;
            }
        }
        return DEFAULT;
    }
}
