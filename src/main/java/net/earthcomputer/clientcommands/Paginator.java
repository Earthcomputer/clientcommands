package net.earthcomputer.clientcommands;

import java.util.List;

public class Paginator<T>
{

    private final List<T> _list;
    public final int Pagesize;

    public Paginator(List<T> list, int pagesize)
    {
        _list = list;
        Pagesize = pagesize;
    }

    public Page<T> getPage(int pageNumber)
    {

        if(!isValidPage(pageNumber))
        {
            throw new IndexOutOfBoundsException("Page number must start from 1 to 'N' Pages");
        }

        int index = pageNumber - 1;
        int pageStart = index * Pagesize;
        int indexofEnd = 0;

        if(pageStart + Pagesize <= getItemsTotal())
        {
            indexofEnd = pageStart + Pagesize;
        }
        else
        {
            indexofEnd = getItemsTotal();
        }

        return new Page<T>(_list.subList(pageStart, indexofEnd), pageNumber);
    }

    public boolean isValidPage(int pageNumber)
    {
        return !(pageNumber > getPageCount() || pageNumber < 1);
    }

    public int getItemsTotal()
    {
        return _list.size();
    }

    public int getPageCount()
    {
        return (int) Math.ceil((float) getItemsTotal() / (float) Pagesize);
    }



}

