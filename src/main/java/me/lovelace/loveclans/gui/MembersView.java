package me.lovelace.loveclans.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Pure logic of the combined "members, applications and invitations" screen: what a filter shows, how the
 * list is sorted and cut into pages. No Bukkit here, so it is unit-tested without a server.
 */
public final class MembersView {
    private MembersView() {}

    /** Work zone of a 54-slot menu: three rows of seven. */
    public static final int PAGE_SIZE = 21;

    public enum Kind { APPLICATION, INVITE, MEMBER }

    public enum Filter {
        ALL, MEMBERS, ONLINE, APPLICATIONS, INVITES;

        /** Applications and invitations are only shown to those who may handle them. */
        public boolean needsManager() {
            return this == APPLICATIONS || this == INVITES;
        }

        /** Next filter in the cycle (or the previous one), skipping the ones this viewer may not use. */
        public Filter step(boolean forward, boolean manager) {
            Filter[] all = values();
            Filter current = this;
            for (int i = 0; i < all.length; i++) {
                int next = (current.ordinal() + (forward ? 1 : all.length - 1)) % all.length;
                current = all[next];
                if (manager || !current.needsManager()) return current;
            }
            return ALL;
        }
    }

    public enum Sort {
        RANK, CONTRIBUTION, NAME, DATE;

        public Sort step(boolean forward) {
            Sort[] all = values();
            return all[(ordinal() + (forward ? 1 : all.length - 1)) % all.length];
        }
    }

    /**
     * One row of the list. {@code date} is when the member joined, the application was sent or - for an
     * invitation, which only stores its expiry - when it expires; "newest first" therefore puts the
     * freshest invitation first as well.
     */
    public record Entry(Kind kind, UUID id, String name, int rankWeight, int contribution, long date, boolean online) {}

    /** Entries the filter lets through, in display order. */
    public static List<Entry> apply(List<Entry> entries, Filter filter, Sort sort, boolean manager) {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : entries) {
            if (matches(entry, filter, manager)) result.add(entry);
        }
        // Whatever needs an answer comes first: applications, then invitations, then the members themselves.
        result.sort(Comparator.comparingInt((Entry e) -> e.kind().ordinal()).thenComparing(comparatorFor(sort)));
        return result;
    }

    private static boolean matches(Entry entry, Filter filter, boolean manager) {
        if (entry.kind() != Kind.MEMBER && !manager) return false;
        return switch (filter) {
            case ALL -> true;
            case MEMBERS -> entry.kind() == Kind.MEMBER;
            case ONLINE -> entry.kind() == Kind.MEMBER && entry.online();
            case APPLICATIONS -> entry.kind() == Kind.APPLICATION;
            case INVITES -> entry.kind() == Kind.INVITE;
        };
    }

    private static Comparator<Entry> comparatorFor(Sort sort) {
        Comparator<Entry> byName = Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Entry::id);
        return switch (sort) {
            case RANK -> Comparator.comparingInt(Entry::rankWeight).reversed().thenComparing(byName);
            case CONTRIBUTION -> Comparator.comparingInt(Entry::contribution).reversed().thenComparing(byName);
            case NAME -> byName;
            case DATE -> Comparator.comparingLong(Entry::date).reversed().thenComparing(byName);
        };
    }

    public static int pageCount(int total) {
        return Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    public static int clampPage(int page, int total) {
        return Math.max(0, Math.min(page, pageCount(total) - 1));
    }

    public static <T> List<T> page(List<T> all, int page) {
        int from = Math.min(all.size(), clampPage(page, all.size()) * PAGE_SIZE);
        return all.subList(from, Math.min(all.size(), from + PAGE_SIZE));
    }
}
