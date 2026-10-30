package me.lovelace.loveclans.util;

import static org.junit.jupiter.api.Assertions.*;

import me.lovelace.loveclans.integration.NameCheck;
import me.lovelace.loveclans.util.ClanInputValidator.Field;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ClanInputValidatorTest {
    private static final ClanNameRules.Limits NAME = new ClanNameRules.Limits(4, 10, "^[\\p{L}\\p{N} _-]+$");
    private static final ClanNameRules.Limits TAG = new ClanNameRules.Limits(3, 6, "^[A-Za-z0-9_]+$");

    private static NameCheck.Filter filter(String swear, String ad) {
        return new NameCheck.Filter() {
            public boolean isProfane(String text) { return text.equals(swear); }
            public boolean containsAdvertising(String text) { return text.equals(ad); }
        };
    }

    private static ClanInputValidator validator(boolean enabled) {
        return new ClanInputValidator(NAME, TAG, new NameCheck(filter("Дурак", "mysrv.ru"), () -> enabled));
    }

    @Test
    void goodNameAndTagPass() {
        var v = validator(true);
        assertEquals(Optional.empty(), v.firstProblem(Field.NAME, "Волки", s -> false));
        assertEquals(Optional.empty(), v.firstProblem(Field.TAG, "WOLF", s -> false));
    }

    @Test
    void lengthAndCharactersComeFirst() {
        var v = validator(true);
        assertEquals(Optional.of("invalid-name"), v.firstProblem(Field.NAME, "Ab", s -> false));
        assertEquals(Optional.of("invalid-name"), v.firstProblem(Field.NAME, "Очень длинное название", s -> false));
        assertEquals(Optional.of("invalid-name"), v.firstProblem(Field.NAME, "<red>Клан", s -> false));
        assertEquals(Optional.of("invalid-tag"), v.firstProblem(Field.TAG, "ВОЛК", s -> false));
        assertEquals(Optional.of("invalid-tag"), v.firstProblem(Field.TAG, "AB", s -> false));
    }

    @Test
    void swearingAndAdvertisingAreRefusedForNameAndTag() {
        var swearing = new ClanInputValidator(NAME, TAG, new NameCheck(filter("Дурак", "none"), () -> true));
        assertEquals(Optional.of("name-profane"), swearing.firstProblem(Field.NAME, "Дурак", s -> false));
        var ads = new ClanInputValidator(NAME, new ClanNameRules.Limits(3, 8, "^[A-Za-z0-9_.]+$"),
                new NameCheck(filter("none", "mysrv.ru"), () -> true));
        assertEquals(Optional.of("tag-ad"), ads.firstProblem(Field.TAG, "mysrv.ru", s -> false));
    }

    @Test
    void theFilterCanBeSwitchedOff() {
        assertEquals(Optional.empty(), validator(false).firstProblem(Field.NAME, "Дурак", s -> false));
    }

    @Test
    void anAbsentFilterAnswersNo() {
        var none = new NameCheck.Filter() {
            public boolean isProfane(String text) { return false; }
            public boolean containsAdvertising(String text) { return false; }
        };
        var v = new ClanInputValidator(NAME, TAG, new NameCheck(none, () -> true));
        assertEquals(Optional.empty(), v.firstProblem(Field.NAME, "Дурак", s -> false));
    }

    @Test
    void aTakenTagIsReportedLastAndOnlyForTags() {
        var v = validator(true);
        assertEquals(Optional.of("tag-exists"), v.firstProblem(Field.TAG, "WOLF", s -> s.equals("WOLF")));
        assertEquals(Optional.empty(), v.firstProblem(Field.NAME, "Волки", s -> true));
    }

    @Test
    void theTakenCheckIsNotAskedWhenAnEarlierRuleFails() {
        var asked = new AtomicBoolean();
        validator(true).firstProblem(Field.TAG, "AB", s -> { asked.set(true); return true; });
        assertFalse(asked.get());
    }

    @Test
    void aBrokenPatternInTheConfigRefusesEverything() {
        var broken = new ClanNameRules.Limits(1, 10, "([");
        assertEquals(ClanNameRules.Problem.BAD_CHARACTERS, ClanNameRules.check("Anything", broken));
    }

    @Test
    void typedTextLosesSurroundingSpaces() {
        assertEquals("Волки", ClanNameRules.clean("  Волки "));
        assertEquals("", ClanNameRules.clean(null));
    }
}
