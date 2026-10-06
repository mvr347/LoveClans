package me.lovelace.loveclans.gui;

import me.lovelace.loveclans.gui.MembersView.Entry;
import me.lovelace.loveclans.gui.MembersView.Filter;
import me.lovelace.loveclans.gui.MembersView.Kind;
import me.lovelace.loveclans.gui.MembersView.Sort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MembersViewTest {

    private static Entry member(String name, int weight, int contribution, long date, boolean online) {
        return new Entry(Kind.MEMBER, UUID.nameUUIDFromBytes(name.getBytes()), name, weight, contribution, date, online);
    }

    private static Entry application(String name, long date) {
        return new Entry(Kind.APPLICATION, UUID.nameUUIDFromBytes(("a" + name).getBytes()), name, 0, 0, date, false);
    }

    private static Entry invite(String name, long date) {
        return new Entry(Kind.INVITE, UUID.nameUUIDFromBytes(("i" + name).getBytes()), name, 0, 0, date, false);
    }

    private final List<Entry> all = List.of(
            member("Zed", 4, 10, 100, true),
            member("amy", 1, 50, 300, false),
            member("Bob", 2, 50, 200, true),
            application("Cat", 10),
            application("Dan", 20),
            invite("Eve", 5));

    private static List<String> names(List<Entry> entries) {
        return entries.stream().map(Entry::name).toList();
    }

    @Test
    void applicationsAndInvitesComeBeforeMembers() {
        assertEquals(List.of("Dan", "Cat", "Eve", "amy", "Bob", "Zed"),
                names(MembersView.apply(all, Filter.ALL, Sort.DATE, true)));
    }

    @Test
    void rankSortPutsHigherRanksFirstWithinMembers() {
        List<Entry> members = MembersView.apply(all, Filter.MEMBERS, Sort.RANK, true);
        assertEquals(List.of("Zed", "Bob", "amy"), names(members));
    }

    @Test
    void contributionTiesFallBackToNameIgnoringCase() {
        assertEquals(List.of("amy", "Bob", "Zed"), names(MembersView.apply(all, Filter.MEMBERS, Sort.CONTRIBUTION, true)));
    }

    @Test
    void nameSortIsCaseInsensitive() {
        assertEquals(List.of("amy", "Bob", "Zed"), names(MembersView.apply(all, Filter.MEMBERS, Sort.NAME, true)));
    }

    @Test
    void dateSortIsNewestFirst() {
        assertEquals(List.of("amy", "Bob", "Zed"), names(MembersView.apply(all, Filter.MEMBERS, Sort.DATE, true)));
        assertEquals(List.of("Dan", "Cat"), names(MembersView.apply(all, Filter.APPLICATIONS, Sort.DATE, true)));
    }

    @Test
    void onlineFilterKeepsOnlyOnlineMembers() {
        assertEquals(List.of("Zed", "Bob"), names(MembersView.apply(all, Filter.ONLINE, Sort.RANK, true)));
    }

    @Test
    void viewersWithoutManagementNeverSeeApplicationsOrInvites() {
        assertEquals(List.of("Zed", "Bob", "amy"), names(MembersView.apply(all, Filter.ALL, Sort.RANK, false)));
        assertTrue(MembersView.apply(all, Filter.APPLICATIONS, Sort.RANK, false).isEmpty());
        assertTrue(MembersView.apply(all, Filter.INVITES, Sort.RANK, false).isEmpty());
    }

    @Test
    void filterCycleSkipsManagerOnlyFiltersForOrdinaryMembers() {
        assertEquals(Filter.MEMBERS, Filter.ALL.step(true, false));
        assertEquals(Filter.ONLINE, Filter.MEMBERS.step(true, false));
        assertEquals(Filter.ALL, Filter.ONLINE.step(true, false));
        assertEquals(Filter.ONLINE, Filter.ALL.step(false, false));
        assertEquals(Filter.APPLICATIONS, Filter.ONLINE.step(true, true));
        assertEquals(Filter.ALL, Filter.INVITES.step(true, true));
        assertEquals(Filter.INVITES, Filter.ALL.step(false, true));
    }

    @Test
    void sortCycleWrapsBothWays() {
        assertEquals(Sort.CONTRIBUTION, Sort.RANK.step(true));
        assertEquals(Sort.RANK, Sort.DATE.step(true));
        assertEquals(Sort.DATE, Sort.RANK.step(false));
    }

    @Test
    void pagesAreClampedAndSizedToTheWorkZone() {
        List<Integer> numbers = new ArrayList<>();
        for (int i = 0; i < 50; i++) numbers.add(i);
        assertEquals(3, MembersView.pageCount(50));
        assertEquals(1, MembersView.pageCount(0));
        assertEquals(21, MembersView.page(numbers, 0).size());
        assertEquals(8, MembersView.page(numbers, 2).size());
        assertEquals(8, MembersView.page(numbers, 99).size(), "an out-of-range page falls back to the last one");
        assertEquals(21, MembersView.page(numbers, -4).size(), "a negative page falls back to the first one");
        assertEquals(0, MembersView.clampPage(5, 0));
    }
}
