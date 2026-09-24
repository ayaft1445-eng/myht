#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Проверка окон мода до сборки.

Ошибка в .ui или в селекторе не ломает компиляцию — она рвёт соединение
игроку уже на сервере, поэтому ловим её здесь. Что проверяем:

1. Скобки в .ui сходятся, каждый Style: @X объявлен в том же файле.
2. Словарь разметки не шире того, что клиент уже разбирает в рабочих окнах
   мода: элементы, свойства, значения-слова и то, у каких элементов вообще
   бывает ID. Плюс явный чёрный список значений, на которых клиент уже падал.
3. Каждый селектор из Java есть в разметке соответствующего окна.
4. Страница загружает разметку (cmd.append) раньше, чем правит значения
   (cmd.set) — иначе set целится в пустой документ.

Запуск: python3 tools/check_ui.py
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UI_DIR = os.path.join(ROOT, "src/main/resources/Common/UI/Custom/HubMenu")
JAVA_DIR = os.path.join(ROOT, "src/main/java/dev/hytalemodding/hubmenu")

# Окна, которые уже открывались у живых игроков — по ним и сверяем словарь.
BASELINE = ["Main.ui", "Section_Minigames.ui", "Section_News.ui",
            "Section_Rules.ui", "Section_Discord.ui"]

# Значения, на которых клиент уже отваливался (коммит e237605).
FORBIDDEN = ["HorizontalAlignment: Start", "VerticalAlignment: Bottom", "RenderBold: false"]

problems = []


def fail(where, text):
    problems.append("%s: %s" % (where, text))


def strip_comments(text):
    return re.sub(r"//[^\n]*", "", text)


def read(path):
    with open(path, encoding="utf-8") as handle:
        return strip_comments(handle.read())


def ui_facts(names):
    """Словарь разметки: элементы, свойства, значения-слова, владельцы ID, стили."""
    elements, props, values, id_owners, styles = set(), set(), set(), set(), set()
    for name in names:
        text = read(os.path.join(UI_DIR, name))
        for match in re.finditer(r"\b(Group|Label|TextButton)\s*(#[A-Za-z0-9_]+)?\s*\{", text):
            elements.add(match.group(1))
            if match.group(2):
                id_owners.add(match.group(1))
        for match in re.finditer(r"\b([A-Z][A-Za-z]*)\s*:", text):
            props.add(match.group(1))
        for match in re.finditer(r"\b([A-Z][A-Za-z]*)\s*:\s*([A-Za-z][A-Za-z0-9]*)\s*[;,)]", text):
            values.add(match.group(1) + ": " + match.group(2))
        for match in re.finditer(r"([A-Za-z]+Style)\s*\(", text):
            styles.add(match.group(1))
    return elements, props, values, id_owners, styles


def check_markup():
    known = ui_facts(BASELINE)
    names = sorted(n for n in os.listdir(UI_DIR) if n.endswith(".ui"))
    for name in names:
        text = read(os.path.join(UI_DIR, name))

        if text.count("{") != text.count("}"):
            fail(name, "фигурные скобки не сходятся")
        if text.count("(") != text.count(")"):
            fail(name, "круглые скобки не сходятся")

        declared = set(re.findall(r"^@([A-Za-z0-9_]+)\s*=", text, re.M))
        for used in set(re.findall(r"Style:\s*@([A-Za-z0-9_]+)\s*;", text)):
            if used not in declared:
                fail(name, "стиль @%s не объявлен" % used)

        ids = re.findall(r"#([A-Za-z0-9_]+)\s*\{", text)
        for duplicate in sorted({i for i in ids if ids.count(i) > 1}):
            fail(name, "ID #%s встречается больше одного раза" % duplicate)

        for value in FORBIDDEN:
            if value in text:
                fail(name, "«%s» — на этом значении клиент уже падал" % value)

        if name in BASELINE:
            continue

        mine = ui_facts([name])
        titles = ["элемент", "свойство", "значение", "ID у элемента", "тип стиля"]
        for title, used, allowed in zip(titles, mine, known):
            for item in sorted(used - allowed):
                fail(name, "%s «%s» не встречается в рабочих окнах — клиент может его не разобрать"
                     % (title, item))
    return names


def java_files():
    for folder, _, names in os.walk(JAVA_DIR):
        for name in names:
            if name.endswith(".java"):
                yield os.path.join(folder, name)


def method_bodies(text):
    """Тела методов, которые получают UICommandBuilder."""
    for match in re.finditer(r"(?:private|public|protected)[^;{]*UICommandBuilder[^;{]*\{", text):
        start = match.end() - 1
        depth = 0
        for index in range(start, len(text)):
            if text[index] == "{":
                depth += 1
            elif text[index] == "}":
                depth -= 1
                if depth == 0:
                    yield match.group(0), text[start:index]
                    break


def check_java():
    layouts = {}
    for path in java_files():
        text = read(path)
        name = os.path.basename(path)

        # 1. append раньше set
        for signature, body in method_bodies(text):
            first_set = body.find("cmd.set(")
            first_append = body.find("cmd.append(")
            if first_set >= 0 and (first_append < 0 or first_append > first_set):
                fail(name, "в методе «%s» значения ставятся раньше загрузки разметки"
                     % signature.strip().rstrip("{").strip())

        # 2. селекторы против разметки этого окна
        used_layouts = re.findall(r'"(HubMenu/[A-Za-z0-9_]+\.ui)"', text)
        if not used_layouts:
            continue
        layouts[name] = used_layouts
        available = set()
        for layout in used_layouts:
            ui_path = os.path.join(UI_DIR, os.path.basename(layout))
            if not os.path.exists(ui_path):
                fail(name, "разметка %s не найдена" % layout)
                continue
            available |= set(re.findall(r"#([A-Za-z0-9_]+)\s*\{", read(ui_path)))

        for match in re.finditer(r'"#([A-Za-z0-9_]+)([^"]*)"(\s*\+)?', text):
            ident, tail, concatenated = match.group(1), match.group(2), match.group(3)
            # "#Name" + row  → строки нумеруются, проверяем нулевую
            candidate = ident + "0" if concatenated and not tail else ident
            if candidate not in available:
                fail(name, "селектор #%s%s не найден в %s"
                     % (ident, " (строка с номером)" if concatenated else "",
                        ", ".join(used_layouts)))
    return layouts


def main():
    names = check_markup()
    layouts = check_java()

    print("окон проверено: %d" % len(names))
    for name, used in sorted(layouts.items()):
        print("  %s → %s" % (name, ", ".join(used)))

    if problems:
        print("\nнайдено проблем: %d" % len(problems))
        for problem in problems:
            print("  - " + problem)
        return 1

    print("\nразметка и селекторы в порядке")
    return 0


if __name__ == "__main__":
    sys.exit(main())
