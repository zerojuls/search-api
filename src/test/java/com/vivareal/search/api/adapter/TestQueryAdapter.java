package com.vivareal.search.api.adapter;

import com.vivareal.search.api.model.SearchApiRequest;

import java.util.List;

public class TestQueryAdapter extends AbstractQueryAdapter<Void, Void, Void> {

    @Override
    public Void getQuery(SearchApiRequest request) {
        return null;
    }

    @Override
    protected Void getFilter(List<String> filter) {
        return null;
    }

    @Override
    protected Void getSort(List<String> sort) {
        return null;
    }

}
