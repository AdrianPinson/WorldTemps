package io.github.adrianpinson.worldtemps.collection;

import io.github.adrianpinson.worldtemps.model.Reading;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/** Custom append-only linked list; nodes are independent of the reading model. */
public final class ReadingList implements Iterable<Reading> {
    private Node head;
    private Node tail;
    private int size;

    public void add(Reading reading) {
        Node node = new Node(Objects.requireNonNull(reading, "Reading is required"));
        if (tail == null) {
            head = node;
        } else {
            tail.next = node;
        }
        tail = node;
        size++;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public Iterator<Reading> iterator() {
        return new Iterator<>() {
            private Node current = head;

            public boolean hasNext() {
                return current != null;
            }

            public Reading next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                Reading value = current.value;
                current = current.next;
                return value;
            }
        };
    }

    private static final class Node {
        private final Reading value;
        private Node next;

        private Node(Reading value) {
            this.value = value;
        }
    }
}
