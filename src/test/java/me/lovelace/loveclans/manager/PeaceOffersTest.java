package me.lovelace.loveclans.manager;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeaceOffersTest {
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    @Test
    void firstProposalWaitsForTheOtherSide() {
        PeaceOffers offers = new PeaceOffers(1000);
        assertFalse(offers.offerOrAccept(a, b, 0));
        assertTrue(offers.hasOffer(a, b, 10));
        assertFalse(offers.hasOffer(b, a, 10));
    }

    @Test
    void answeringWithOwnProposalMakesPeaceAndClearsBoth() {
        PeaceOffers offers = new PeaceOffers(1000);
        offers.offerOrAccept(a, b, 0);
        assertTrue(offers.offerOrAccept(b, a, 500));
        assertFalse(offers.hasOffer(a, b, 600));
        assertFalse(offers.hasOffer(b, a, 600));
    }

    @Test
    void anExpiredProposalIsNotAnswered() {
        PeaceOffers offers = new PeaceOffers(1000);
        offers.offerOrAccept(a, b, 0);
        assertFalse(offers.offerOrAccept(b, a, 1500), "the old offer is gone, this is a new proposal");
        assertTrue(offers.hasOffer(b, a, 1600));
    }

    @Test
    void repeatingYourOwnProposalNeverAcceptsIt() {
        PeaceOffers offers = new PeaceOffers(1000);
        offers.offerOrAccept(a, b, 0);
        assertFalse(offers.offerOrAccept(a, b, 100));
    }

    @Test
    void clearingAClanDropsItsOffers() {
        PeaceOffers offers = new PeaceOffers(1000);
        offers.offerOrAccept(a, b, 0);
        offers.clear(b);
        assertFalse(offers.hasOffer(a, b, 10));
    }
}
