package com.vivareal.search.api.model.http;

import java.util.Set;

import static com.google.common.collect.Sets.newHashSet;
import static org.apache.commons.lang3.ObjectUtils.allNotNull;

public class SearchApiRequestBuilder {
    public static final String INDEX_NAME = "my_index";

    private SearchApiRequestBuilder() {}

    public static BasicRequestBuilder basic() {
        return BasicRequestBuilder.create();
    }

    public static ComplexRequestBuilder create() {
        return ComplexRequestBuilder.create();
    }

    public static class BasicRequestBuilder {
        protected String index;
        protected Set<String> includeFields;
        protected Set<String> excludeFields;

        private BasicRequestBuilder() {}

        public static BasicRequestBuilder create() {
            return new BasicRequestBuilder();
        }

        public BasicRequestBuilder index(final String index) {
            this.index = index;
            return this;
        }

        public BasicRequestBuilder includeFields(Set<String> includeFields) {
            this.includeFields = includeFields;
            return this;
        }

        public BasicRequestBuilder excludeFields(Set<String> excludeFields) {
            this.excludeFields = excludeFields;
            return this;
        }

        public BaseApiRequest build() {
            BaseApiRequest request = new BaseApiRequest();

            if (allNotNull(index))
                request.setIndex(index);

            if (allNotNull(includeFields))
                request.setIncludeFields(includeFields);

            if (allNotNull(excludeFields))
                request.setExcludeFields(excludeFields);

            return request;
        }
    }

    public static class ComplexRequestBuilder extends BasicRequestBuilder {
        private String mm;
        private Set<String> fields;

        private String filter;
        private Set<String> sort;
        private Set<String> facets;
        private int facetSize;
        private String q;
        private int from;
        private int size;

        private ComplexRequestBuilder() {}

        public static ComplexRequestBuilder create() {
            return new ComplexRequestBuilder();
        }

        @Override
        public ComplexRequestBuilder index(final String index) {
            this.index = index;
            return this;
        }

        @Override
        public ComplexRequestBuilder includeFields(Set<String> includeFields) {
            this.includeFields = includeFields;
            return this;
        }

        @Override
        public ComplexRequestBuilder excludeFields(Set<String> excludeFields) {
            this.excludeFields = excludeFields;
            return this;
        }

        public ComplexRequestBuilder mm(String mm) {
            this.mm = mm;
            return this;
        }

        public ComplexRequestBuilder fields(Set<String> fields) {
            this.fields = fields;
            return this;
        }

        public ComplexRequestBuilder filter(String filter) {
            this.filter = filter;
            return this;
        }

        public ComplexRequestBuilder sort(String sort) {
            return sort(newHashSet(sort));

        }

        public ComplexRequestBuilder sort(Set<String> sort) {
            this.sort = sort;
            return this;
        }

        public ComplexRequestBuilder facets(Set<String> facets) {
            this.facets = facets;
            return this;
        }

        public ComplexRequestBuilder facetSize(int facetSize) {
            this.facetSize = facetSize;
            return this;
        }

        public ComplexRequestBuilder q(String q) {
            this.q = q;
            return this;
        }

        public ComplexRequestBuilder from(int from) {
            this.from = from;
            return this;
        }

        public ComplexRequestBuilder size(int size) {
            this.size = size;
            return this;
        }

        public SearchApiRequest build() {
            SearchApiRequest request = new SearchApiRequest();

            if (allNotNull(index))
                request.setIndex(index);

            if (allNotNull(includeFields))
                request.setIncludeFields(includeFields);

            if (allNotNull(excludeFields))
                request.setExcludeFields(excludeFields);

            if (allNotNull(mm))
                request.setMm(mm);

            if (allNotNull(fields))
                request.setFields(fields);

            if (allNotNull(filter))
                request.setFilter(filter);

            if (allNotNull(sort))
                request.setSort(sort);

            if (allNotNull(facets))
                request.setFacets(facets);

            if (allNotNull(facetSize))
                request.setFacetSize(facetSize);

            if (allNotNull(q))
                request.setQ(q);

            if (allNotNull(from))
                request.setFrom(from);

            if (allNotNull(size))
                request.setSize(size);

            return request;
        }

        public BasicRequestBuilder basic() {
            return this;
        }
    }
}