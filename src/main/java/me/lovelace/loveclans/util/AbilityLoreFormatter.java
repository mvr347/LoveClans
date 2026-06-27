package me.lovelace.loveclans.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Форматирует длинные описания способностей духа (SpiritAbility) для отображения
 * в лоре предмета: разбивает на строки не длиннее заданного лимита символов и подсвечивает
 * баффы/условия цветом (зелёный — позитивный эффект, жёлтый — нейтральное условие/порог,
 * красный — негативный эффект для противника или штраф).
 */
public final class AbilityLoreFormatter {
    private static final int MAX_LINE_LENGTH = 40;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    // Ключевые слова, обозначающие положительный эффект на самого носителя способности.
    private static final String[] POSITIVE_KEYWORDS = {
            "устойчивость к огню", "регенерация", "ускорение", "сопротивление урону",
            "скорость атаки", "к броне", "сердца"
    };

    // Ключевые слова, обозначающие негативный эффект, накладываемый на противника.
    private static final String[] NEGATIVE_KEYWORDS = {
            "поджигает атакующего", "замедляет удары атакующего"
    };

    // Условия/пороги (нейтральная информация, не баф и не дебаф сам по себе).
    private static final Pattern THRESHOLD_PATTERN = Pattern.compile("Здоровье\\s*<\\s*\\d+%|Во время войны клана|Базово");

    private AbilityLoreFormatter() {
    }

    /**
     * Возвращает раскрашенные и перенесённые по строкам компоненты описания способности.
     */
    public static List<Component> format(String rawDescription) {
        List<Component> result = new ArrayList<>();
        String colorized = colorize(rawDescription);
        for (String line : wrap(colorized, MAX_LINE_LENGTH)) {
            result.add(MINI_MESSAGE.deserialize("<gray>" + line));
        }
        return result;
    }

    /**
     * Расставляет MiniMessage-теги цвета вокруг известных баффов/условий в исходном тексте.
     * Делается до переноса по строкам, чтобы цветовые теги не оказались разорваны между строками.
     */
    private static String colorize(String text) {
        String result = text;

        for (String keyword : NEGATIVE_KEYWORDS) {
            result = replaceCaseInsensitive(result, keyword, "<red>" + keyword + "</red>");
        }
        for (String keyword : POSITIVE_KEYWORDS) {
            result = replaceCaseInsensitive(result, keyword, "<green>" + keyword + "</green>");
        }

        Matcher matcher = THRESHOLD_PATTERN.matcher(result);
        StringBuilder withThresholds = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            withThresholds.append(result, lastEnd, matcher.start());
            withThresholds.append("<yellow>").append(matcher.group()).append("</yellow>");
            lastEnd = matcher.end();
        }
        withThresholds.append(result.substring(lastEnd));

        return withThresholds.toString();
    }

    private static String replaceCaseInsensitive(String text, String target, String replacement) {
        // Простая регистронезависимая замена без regex-спецсимволов (все наши ключевые слова — обычный текст).
        Pattern pattern = Pattern.compile(Pattern.quote(target), Pattern.CASE_INSENSITIVE);
        return pattern.matcher(text).replaceAll(Matcher.quoteReplacement(replacement));
    }

    /**
     * Переносит текст по словам так, чтобы видимая (без учёта MiniMessage-тегов) длина строки
     * не превышала maxLength символов. MiniMessage-теги, открытые в одной строке, переоткрываются
     * на следующей, если effect не был закрыт — но т.к. мы оборачиваем целые фразы тегами целиком,
     * на практике тег открывается и закрывается в пределах одного слова/фразы, так что разрывов не будет.
     */
    private static List<String> wrap(String text, int maxLength) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder currentLine = new StringBuilder();
        int currentVisibleLength = 0;

        for (String word : words) {
            int visibleWordLength = stripTags(word).length();
            int separatorLength = currentLine.length() > 0 ? 1 : 0;

            if (currentLine.length() > 0 && currentVisibleLength + separatorLength + visibleWordLength > maxLength) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder();
                currentVisibleLength = 0;
                separatorLength = 0;
            }

            if (currentLine.length() > 0) {
                currentLine.append(' ');
            }
            currentLine.append(word);
            currentVisibleLength += separatorLength + visibleWordLength;
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines;
    }

    private static String stripTags(String text) {
        return text.replaceAll("</?[a-zA-Z]+>", "");
    }
}
