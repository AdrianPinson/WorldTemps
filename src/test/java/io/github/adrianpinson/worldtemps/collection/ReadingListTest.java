package io.github.adrianpinson.worldtemps.collection;

import io.github.adrianpinson.worldtemps.model.Reading;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReadingListTest {
    private Reading reading(String city) {
        return new Reading("Europe", "UK", "", city, LocalDate.of(2025, 1, 1), 40);
    }

    @Test void emptyListHasNoReadings() {
        ReadingList list = new ReadingList();
        assertTrue(list.isEmpty());
        assertEquals(0, list.size());
        assertFalse(list.iterator().hasNext());
        assertThrows(NoSuchElementException.class, () -> list.iterator().next());
    }

    @Test void appendPreservesEveryReadingInOrder() {
        ReadingList list = new ReadingList();
        Reading a = reading("London");
        Reading b = reading("Oxford");
        Reading c = reading("York");
        list.add(a);
        list.add(b);
        list.add(c);
        List<Reading> actual = new ArrayList<>();
        list.forEach(actual::add);
        assertEquals(List.of(a, b, c), actual);
        assertEquals(3, list.size());
        assertFalse(list.isEmpty());
    }

    @Test void sizesAreIndependentAndReadingsCanBeSharedWithoutRelinking() {
        ReadingList first = new ReadingList();
        ReadingList second = new ReadingList();
        Reading shared = reading("London");
        first.add(shared);
        first.add(reading("York"));
        second.add(shared);
        assertEquals(2, first.size());
        assertEquals(1, second.size());
        assertSame(shared, second.iterator().next());
    }

    @Test void rejectsNullWithoutChangingSize() {
        ReadingList list = new ReadingList();
        assertThrows(NullPointerException.class, () -> list.add(null));
        assertEquals(0, list.size());
    }
}
