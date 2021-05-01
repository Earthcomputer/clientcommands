package net.earthcomputer.clientcommands;

import java.util.List;

public class Page<T>
{
    public final List<T> Items;
    public final int PageNumber;

    public Page(List<T> arr, int pageNumber)
    {
        Items = arr;
        PageNumber = pageNumber;
    }
}