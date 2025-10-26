package nl.han.ica.datastructures;

import java.util.Iterator;
import java.util.LinkedList;

// Eenvoudige implementatie van IHANLinkedList
public class HANLinkedList<T> implements IHANLinkedList<T>, Iterable<T> {

    private LinkedList<T> list = new LinkedList<>();

    @Override
    public void addFirst(T value) {
        list.addFirst(value);
    }

    @Override
    public void clear() {

    }

    @Override
    public void insert(int index, T value) {

    }

    @Override
    public void delete(int pos) {

    }

    @Override
    public T get(int pos) {
        return null;
    }

    @Override
    public void removeFirst() {
        list.removeFirst();
    }

    @Override
    public T getFirst() {
        return list.getFirst();
    }

    @Override
    public int getSize() {
        return 0;
    }

    @Override
    public Iterator<T> iterator() {
        return list.iterator();
    }

    @Override
    public String toString() {
        return list.toString();
    }
}
