package net.earthcomputer.clientcommands;

import java.util.List;

public class Page<T> {
    public final List<T> items;
    public final int pageNumber;

    public Page(List<T> arr, int pageNumber) {
        this.items = arr;
        this.pageNumber = pageNumber;
    }
}