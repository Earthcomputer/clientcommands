package net.earthcomputer.clientcommands;

import java.util.List;

public class Paginator<T> {

    private final List<T> list;
    public final int pageSize;

    public Paginator(List<T> list, int pageSize) {
        this.list = list;
        this.pageSize = pageSize;
    }

    public Page<T> getPage(int pageNumber) {

        if(!isValidPage(pageNumber)) {
            throw new IndexOutOfBoundsException("Page number must start from 1 to 'N' Pages");
        }

        int index = pageNumber - 1;
        int pageStart = index * this.pageSize;
        int indexofEnd = 0;

        if(pageStart + this.pageSize <= getItemsTotal()) {
            indexofEnd = pageStart + this.pageSize;
        }
        else {
            indexofEnd = getItemsTotal();
        }

        return new Page<T>(this.list.subList(pageStart, indexofEnd), pageNumber);
    }

    public boolean isValidPage(int pageNumber) {
        return !(pageNumber > getPageCount() || pageNumber < 1);
    }

    public int getItemsTotal() {
        return this.list.size();
    }

    public int getPageCount() {
        return (int) Math.ceil((float) getItemsTotal() / (float) pageSize);
    }



}

