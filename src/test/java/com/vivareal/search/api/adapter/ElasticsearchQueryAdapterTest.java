package com.vivareal.search.api.adapter;

import com.google.common.collect.Sets;
import com.vivareal.search.api.model.http.BaseApiRequest;
import com.vivareal.search.api.model.http.SearchApiRequest;
import com.vivareal.search.api.model.mapping.MappingType;
import org.assertj.core.util.Lists;
import org.elasticsearch.action.get.GetRequestBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static com.vivareal.search.api.adapter.ElasticsearchSettingsAdapter.SHARDS;
import static com.vivareal.search.api.configuration.environment.RemoteProperties.*;
import static com.vivareal.search.api.model.http.SearchApiRequestBuilder.INDEX_NAME;
import static com.vivareal.search.api.model.mapping.MappingType.*;
import static com.vivareal.search.api.model.query.LogicalOperator.AND;
import static com.vivareal.search.api.model.query.RelationalOperator.*;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Stream.concat;
import static java.util.stream.Stream.of;
import static org.elasticsearch.index.query.Operator.OR;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.springframework.test.util.ReflectionTestUtils.setField;

public class ElasticsearchQueryAdapterTest extends SearchTransportClientMock {

    private QueryAdapter<GetRequestBuilder, SearchRequestBuilder> queryAdapter;

    @Mock
    private ElasticsearchSettingsAdapter settingsAdapter;

    @Mock
    private SearchAfterQueryAdapter searchAfterQueryAdapter;

    @Mock
    private SortQueryAdapter sortQueryAdapter;

    @Before
    public void setup() {
        initMocks(this);

        QS_MM.setValue(INDEX_NAME,"75%");
        QS_DEFAULT_FIELDS.setValue(INDEX_NAME,"field,field1");
        SOURCE_INCLUDES.setValue(INDEX_NAME, "");
        SOURCE_EXCLUDES.setValue(INDEX_NAME, "");
        ES_QUERY_TIMEOUT_VALUE.setValue(INDEX_NAME, "100");
        ES_QUERY_TIMEOUT_UNIT.setValue(INDEX_NAME, "MILLISECONDS");
        ES_DEFAULT_SIZE.setValue(INDEX_NAME, "20");
        ES_MAX_SIZE.setValue(INDEX_NAME, "200");
        ES_FACET_SIZE.setValue(INDEX_NAME, "20");
        ES_MAPPING_META_FIELDS_ID.setValue(INDEX_NAME, "id");

        ESClient esClient = new ESClient(transportClient);
        SourceFieldAdapter sourceFieldAdapter = new SourceFieldAdapter(settingsAdapter);

        when(settingsAdapter.getFetchSourceIncludeFields(any())).thenCallRealMethod();
        when(settingsAdapter.getFetchSourceExcludeFields(any(), any())).thenCallRealMethod();

        this.queryAdapter = new ElasticsearchQueryAdapter(esClient, settingsAdapter, sourceFieldAdapter, searchAfterQueryAdapter, sortQueryAdapter);

        Map<String, String[]> defaultSourceFields = new HashMap<>();
        defaultSourceFields.put(INDEX_NAME, new String[0]);

        setField(settingsAdapter, "defaultSourceIncludes", defaultSourceFields);
        setField(settingsAdapter, "defaultSourceExcludes", defaultSourceFields);

        doNothing().when(settingsAdapter).checkIndex(any());
        doNothing().when(searchAfterQueryAdapter).apply(any(), any());
        doNothing().when(sortQueryAdapter).apply(any(), any());

        when(settingsAdapter.settingsByKey(INDEX_NAME, SHARDS)).thenReturn("8");
        when(settingsAdapter.isTypeOf(anyString(), anyString(), any(MappingType.class))).thenReturn(false);
    }

    @After
    public void closeClient() {
        this.transportClient.close();
    }

    @Test
    public void shouldReturnGetRequestBuilderByGetId() {
        String id = "123456";

        newArrayList(basicRequest, filterableRequest, fullRequest).parallelStream().forEach(
            request -> {
                BaseApiRequest searchApiRequest = request.build();
                GetRequestBuilder requestBuilder = queryAdapter.getById(searchApiRequest, id);

                assertEquals(id, requestBuilder.request().id());
                assertEquals(searchApiRequest.getIndex(), requestBuilder.request().index());
                assertEquals(searchApiRequest.getIndex(), requestBuilder.request().type());
            }
        );
    }

    private void validateFetchSources(Set<String> includeFields, Set<String> excludeFields, FetchSourceContext fetchSourceContext) {
        assertNotNull(fetchSourceContext);

        // Check include fields
        assertEquals(includeFields.size(), fetchSourceContext.includes().length);
        assertTrue(includeFields.containsAll(asList(fetchSourceContext.includes())));

        // Check exclude fields
        List<String> intersection = newArrayList(excludeFields);
        intersection.retainAll(includeFields);
        List<String> excludedAfterValidation = excludeFields.stream().filter(field -> !intersection.contains(field)).sorted().collect(toList());
        assertEquals(excludeFields.size() - intersection.size(), fetchSourceContext.excludes().length);
        assertEquals(of(fetchSourceContext.excludes()).sorted().collect(toList()), excludedAfterValidation);
    }

    @Test
    public void shouldReturnGetRequestBuilderByGetIdWithIncludeAndExcludeFields() {
        String id = "123456";

        Set<String> includeFields = newHashSet("field1", "field2", "field3");
        Set<String> excludeFields = newHashSet("field3", "field4");

        concat(includeFields.stream(), excludeFields.stream()).forEach(field -> when(settingsAdapter.checkFieldName(INDEX_NAME, field, true)).thenReturn(true));

        newArrayList(basicRequest, filterableRequest, fullRequest).parallelStream().forEach(
            request -> {
                BaseApiRequest searchApiRequest = request.includeFields(includeFields).excludeFields(excludeFields).build();
                GetRequestBuilder requestBuilder = queryAdapter.getById(searchApiRequest, id);
                FetchSourceContext fetchSourceContext = requestBuilder.request().fetchSourceContext();

                assertEquals(id, requestBuilder.request().id());
                assertEquals(searchApiRequest.getIndex(), requestBuilder.request().index());
                assertEquals(searchApiRequest.getIndex(), requestBuilder.request().type());

                validateFetchSources(includeFields, excludeFields, fetchSourceContext);
            }
        );
    }

    @Test
    public void shouldApplyTimeoutOnQueryBody() {
        SearchApiRequest request = fullRequest.build();
        SearchRequestBuilder searchRequestBuilder = queryAdapter.query(request);
        assertEquals(new TimeValue(100, TimeUnit.MILLISECONDS), searchRequestBuilder.request().source().timeout());
    }

    @Test
    public void shouldReturnSimpleSearchRequestBuilderWithBasicRequestPagination() {
        SearchApiRequest request = fullRequest.build();
        SearchRequestBuilder searchRequestBuilder = queryAdapter.query(request);
        SearchSourceBuilder source = searchRequestBuilder.request().source();

        assertEquals(request.getIndex(), searchRequestBuilder.request().indices()[0]);
        assertEquals(request.getFrom(), source.from());
        assertEquals(request.getSize(), source.size());
    }

    @Test
    public void shouldReturnSearchRequestBuilderWithSimpleNestedObject() {
        final String field = "nested.field";
        final Object value = "Lorem Ipsum";

        when(settingsAdapter.isTypeOf(INDEX_NAME, field.split("\\.")[0], MappingType.FIELD_TYPE_NESTED)).thenReturn(true);

        newArrayList(filterableRequest, fullRequest).parallelStream().forEach(
            request -> {
                SearchRequestBuilder searchRequestBuilder = queryAdapter.query(request.filter(format(field, value, EQUAL.name())).build());

                NestedQueryBuilder nestedQueryBuilder = (NestedQueryBuilder) ((BoolQueryBuilder) searchRequestBuilder.request().source().query()).must().get(0);
                assertNotNull(nestedQueryBuilder);
                assertTrue(nestedQueryBuilder.toString().contains("\"path\" : \"" + field.split("\\.")[0] + "\""));

                MatchQueryBuilder must = (MatchQueryBuilder) ((BoolQueryBuilder) nestedQueryBuilder.query()).must().get(0);
                assertNotNull(must);
                assertEquals(field, must.fieldName());
                assertEquals(value, must.value());
            }
        );
    }

    @Test
    public void shouldReturnSearchRequestBuilderWithSingleFilterDifferent() {
        final String field = "field1";
        final Object value = "Lorem Ipsum";

        DIFFERENT.getAlias().parallelStream().forEach(
            op -> newArrayList(filterableRequest, fullRequest).parallelStream().forEach(
                request -> {
                    SearchRequestBuilder searchRequestBuilder = queryAdapter.query(request.filter(format(field, value, op)).build());
                    MatchQueryBuilder mustNot = (MatchQueryBuilder) ((BoolQueryBuilder) searchRequestBuilder.request().source().query()).mustNot().get(0);

                    assertNotNull(mustNot);
                    assertEquals(field, mustNot.fieldName());
                    assertEquals(value, mustNot.value());
                }
            )
        );
    }

    @Test
    public void shouldReturnSearchRequestBuilderWithSingleFilterEqual() {
        final String field = "field1";
        final Object value = "Lorem Ipsum";

        EQUAL.getAlias().parallelStream().forEach(
            op -> newArrayList(filterableRequest, fullRequest).parallelStream().forEach(
                request -> {
                    SearchRequestBuilder searchRequestBuilder = queryAdapter.query(request.filter(format(field, value, op)).build());
                    MatchQueryBuilder must = (MatchQueryBuilder) ((BoolQueryBuilder) searchRequestBuilder.request().source().query()).must().get(0);

                    assertNotNull(must);
                    assertEquals(field, must.fieldName());
                    assertEquals(value, must.value());
                }
            )
        );
    }

    @Test
    public void shouldReturnSearchRequestBuilderByTwoFragmentLevelsUsingOR() {
        EQUAL.getAlias().parallelStream().forEach(
            op -> newArrayList(filterableRequest, fullRequest).parallelStream().forEach(
                request -> {
                    SearchRequestBuilder searchRequestBuilder = queryAdapter.query(request.filter("(x1:1 AND y1:1) OR (x1:2 AND y2:2)").build());
                    List<QueryBuilder> should = ((BoolQueryBuilder) searchRequestBuilder.request().source().query()).should();

                    assertNotNull(should);
                    assertEquals(2, should.size());
                    assertEquals(2, ((BoolQueryBuilder) should.get(0)).must().size());
                    assertEquals(2, ((BoolQueryBuilder) should.get(1)).must().size());
                }
            )
        );
    }

    @Test
    public void shouldReturnSearchRequestBuilderByTwoFragmentLevelsUsingAND() {
        EQUAL.getAlias().parallelStream().forEach(
            op -> newArrayList(filterableRequest, fullRequest).parallelStream().forEach(
                request -> {
                    SearchRequestBuilder searchRequestBuilder = queryAdapter.query(request.filter("(x1:1 OR y1:1) AND (x1:2 OR y2:2)").build());
                    List<QueryBuilder> must = ((BoolQueryBuilder) searchRequestBuilder.request().source().query()).must();

                    assertNotNull(must);
                    assertEquals(2, must.size());
                    assertEquals(2, ((BoolQueryBuilder) must.get(0)).should().size());
                    assertEquals(2, ((BoolQueryBuilder) must.get(1)).should().size());
                }
            )
        );
    }

    @Test
    public void shouldReturnSearchRequestBuilderByTwoFragmentLevelsUsingNOT() {
        EQUAL.getAlias().parallelStream().forEach(
            op -> newArrayList(filterableRequest, fullRequest).parallelStream().forEach(
                request -> {
                    SearchRequestBuilder searchRequestBuilder = queryAdapter.query(request.filter("NOT((x1:1 AND y1:1) OR (x1:2 AND y2:2))").build());
                    List<QueryBuilder> mustNot = ((BoolQueryBuilder) searchRequestBuilder.request().source().query()).mustNot();

                    assertNotNull(mustNot);
                    assertEquals(1, mustNot.size());

                    List<QueryBuilder> should = ((BoolQueryBuilder) mustNot.get(0)).should();
                    assertNotNull(should);
                    assertEquals(2, should.size());
                    assertEquals(2, ((BoolQueryBuilder) should.get(0)).must().size());
                    assertEquals(2, ((BoolQueryBuilder) should.get(1)).must().size());
                }
            )
        );
    }

    @Test
    public void shouldReturnSearchRequestBuilderWithSingleFilterGreater() {
        final String field = "field1";
        final Object value = 10;

        GREATER.getAlias().forEach(
            op -> newArrayList(filterableRequest, fullRequest).parallelStream().forEach(
                request -> {
                    SearchRequestBuilder searchRequestBuilder = queryAdapter.query(request.filter(format(field, value, op)).build());
                    RangeQueryBuilder range = (RangeQueryBuilder) ((BoolQueryBuilder) searchRequestBuilder.request().source().query()).must().get(0);

                    assertEquals(field, range.fieldName());
                    assertEquals(value, range.from());
                    assertNull(range.to());
                    assertFalse(range.includeLower());
                    assertTrue(range.includeUpper());
                }
            )
        );
    }

    @Test
    public void shouldReturnSearchRequestBuilderWithSingleFilterGreaterEqual() {
        final String field = "field1";
        final Object value = 10;

        GREATER_EQUAL.getAlias().parallelStream().forEach(
            op -> newArrayList(filterableRequest, fullRequest).parallelStream().forEach(
                request -> {
                    SearchRequestBuilder searchRequestBuilder = queryAdapter.query(request.filter(format(field, value, op)).build());
                    RangeQueryBuilder range = (RangeQueryBuilder) ((BoolQueryBuilder) searchRequestBuilder.request().source().query()).must().get(0);

                    assertEquals(field, range.fieldName());
                    assertEquals(value, range.from());
                    assertNull(range.to());
                    assertTrue(range.includeLower());
                    assertTrue(range.includeUpper());
                }
            )
        );
    }

    @Test
    public void shouldReturnSearchRequestBuilderWithSingleFilterLess() {
        final String field = "field1";
        final Object value = 10;

        LESS.getAlias().parallelStream().forEach(
            op -> newArrayList(filterableRequest, fullRequest).parallelStream().forEach(
                request -> {
                    SearchRequestBuilder searchRequestBuilder = queryAdapter.query(request.filter(format(field, value, op)).build());
                    RangeQueryBuilder range = (RangeQueryBuilder) ((BoolQueryBuilder) searchRequestBuilder.request().source().query()).must().get(0);

                    assertEquals(field, range.fieldName());
                    assertEquals(value, range.to());
                    assertNull(range.from());
                    assertTrue(range.includeLower());
                    assertFalse(range.includeUpper());
                }
            )
        );
    }

    @Test
    public void shouldReturnSearchRequestBuilderWithSingleFilterLessEqual() {
        final String field = "field1";
        final Object value = 10;

        LESS_EQUAL.getAlias().parallelStream().forEach(
            op -> newArrayList(filterableRequest, fullRequest).parallelStream().forEach(
                request -> {
                    SearchRequestBuilder searchRequestBuilder = queryAdapter.query(request.filter(format(field, value, op)).build());
                    RangeQueryBuilder range = (RangeQueryBuilder) ((BoolQueryBuilder) searchRequestBuilder.request().source().query()).must().get(0);

                    assertEquals(field, range.fieldName());
                    assertEquals(value, range.to());
                    assertNull(range.from());
                    assertTrue(range.includeLower());
                    assertTrue(range.includeUpper());
                }
            )
        );
    }

    @Test
    public void shouldReturnSearchRequestBuilderByViewport() {

        String field = "field.location";

        // Google nomenclature
        double northEastLat = 42.0;
        double northEastLon = -74.0;
        double southWestLat = -40.0;
        double southWestLon = -72.0;

        when(settingsAdapter.isTypeOf(INDEX_NAME, field, MappingType.FIELD_TYPE_GEOPOINT)).thenReturn(true);

        VIEWPORT.getAlias().parallelStream().forEach(
            op -> newArrayList(filterableRequest, fullRequest).parallelStream().forEach(
                request -> {
                    SearchRequestBuilder searchRequestBuilder = queryAdapter.query(request.filter(String.format("%s %s [[%s,%s],[%s,%s]]", field, op, northEastLon, northEastLat, southWestLon, southWestLat)).build());
                    GeoBoundingBoxQueryBuilder geoBoundingBoxQueryBuilder = (GeoBoundingBoxQueryBuilder) ((BoolQueryBuilder) searchRequestBuilder.request().source().query()).must().get(0);

                    int delta = 0;
                    assertNotNull(geoBoundingBoxQueryBuilder);
                    assertEquals(field, geoBoundingBoxQueryBuilder.fieldName());
                    assertEquals(northEastLat, geoBoundingBoxQueryBuilder.topLeft().getLat(), delta);
                    assertEquals(southWestLon, geoBoundingBoxQueryBuilder.topLeft().getLon(), delta);
                    assertEquals(southWestLat, geoBoundingBoxQueryBuilder.bottomRight().getLat(), delta);
                    assertEquals(northEastLon, geoBoundingBoxQueryBuilder.bottomRight().getLon(), delta);
                }
            )
        );
    }

    @Test
    public void shouldReturnSearchRequestBuilderByPolygon() {

        String query = "field.location POLYGON [[-1.1,2.2],[3.3,-4.4],[5.5,6.6]]";

        when(settingsAdapter.isTypeOf(INDEX_NAME, "field.location", MappingType.FIELD_TYPE_GEOPOINT)).thenReturn(true);

        POLYGON.getAlias().parallelStream().forEach(
            op -> newArrayList(filterableRequest, fullRequest).parallelStream().forEach(
                request -> {
                    SearchRequestBuilder searchRequestBuilder = queryAdapter.query(request.filter(query).build());
                    GeoPolygonQueryBuilder polygon = (GeoPolygonQueryBuilder) ((BoolQueryBuilder) searchRequestBuilder.request().source().query()).must().get(0);

                    assertNotNull(polygon);
                    assertFalse(polygon.ignoreUnmapped());
                    assertEquals("field.location", polygon.fieldName());
                    assertEquals(GeoValidationMethod.STRICT, polygon.getValidationMethod());

                    // if the last point is different of the first, ES add a copy of the first point in the last array position
                    assertEquals(4, polygon.points().size());

                    // inverted (lat/lon) to adapt GeoJson format (lon/lat)
                    assertEquals(new GeoPoint(2.2, -1.1), polygon.points().get(0));
                    assertEquals(new GeoPoint(-4.4, 3.3), polygon.points().get(1));
                    assertEquals(new GeoPoint(6.6, 5.5), polygon.points().get(2));
                    assertEquals(new GeoPoint(2.2, -1.1), polygon.points().get(3));
                }
            )
        );
    }

    @Test
    public void shouldReturnSearchRequestBuilderWithSingleFilterWithLike() {
        final String field = "field1";
        String value = "Break line\\nNew line with special chars: % \\% _ \\_ * ? \\a!";
        String expected = "Break line\nNew line with special chars: * % ? _ \\* \\? \\a!";

        when(settingsAdapter.isTypeOf(INDEX_NAME, field, FIELD_TYPE_KEYWORD)).thenReturn(true);

        LIKE.getAlias().parallelStream().forEach(
            op -> newArrayList(filterableRequest, fullRequest).parallelStream().forEach(
                request -> {
                    SearchRequestBuilder searchRequestBuilder = queryAdapter.query(request.filter(format(field, value, op)).build());
                    WildcardQueryBuilder wildcardQueryBuilder = (WildcardQueryBuilder) ((BoolQueryBuilder) searchRequestBuilder.request().source().query()).must().get(0);

                    assertNotNull(wildcardQueryBuilder);
                    assertEquals(field, wildcardQueryBuilder.fieldName());
                    assertEquals(expected, wildcardQueryBuilder.value());
                }
            )
        );
    }

    @Test
    public void shouldReturnSearchRequestBuilderWithSingleFilterWithRange() {
        final String field = "field";
        final int from = 3, to = 5;

        RANGE.getAlias().parallelStream().forEach(
            op -> newArrayList(filterableRequest, fullRequest).parallelStream().forEach(
                request -> {
                    SearchRequestBuilder searchRequestBuilder = queryAdapter.query(request.filter(String.format("%s %s [%d,%d]", field, op, from, to)).build());
                    RangeQueryBuilder rangeQueryBuilder = (RangeQueryBuilder) ((BoolQueryBuilder) searchRequestBuilder.request().source().query()).must().get(0);

                    assertNotNull(rangeQueryBuilder);
                    assertEquals(field, rangeQueryBuilder.fieldName());
                    assertEquals(from, rangeQueryBuilder.from());
                    assertEquals(to, rangeQueryBuilder.to());
                    assertTrue(rangeQueryBuilder.includeLower());
                    assertTrue(rangeQueryBuilder.includeUpper());
                }
            )
        );
    }

    @Test
    public void shouldReturnSearchRequestBuilderWithSingleFilterWithRangeWhenNot() {
        final String field = "field";
        final int from = 5, to = 10;

        RANGE.getAlias().parallelStream().forEach(
            op -> newArrayList(filterableRequest, fullRequest).parallelStream().forEach(
                request -> {
                    SearchRequestBuilder searchRequestBuilder = queryAdapter.query(request.filter(String.format("NOT %s %s [%d,%d]", field, op, from, to)).build());
                    RangeQueryBuilder rangeQueryBuilder = (RangeQueryBuilder) ((BoolQueryBuilder) searchRequestBuilder.request().source().query()).mustNot().get(0);

                    assertNotNull(rangeQueryBuilder);
                    assertEquals(field, rangeQueryBuilder.fieldName());
                    assertEquals(from, rangeQueryBuilder.from());
                    assertEquals(to, rangeQueryBuilder.to());
                    assertTrue(rangeQueryBuilder.includeLower());
                    assertTrue(rangeQueryBuilder.includeUpper());
                }
            )
        );
    }

    @Test
    public void shouldReturnSearchRequestBuilderWithSingleFilterIn() {
        final String field = "field1";
        final Object[] values = new Object[]{1, "\"string\"", 1.2, true};

        IN.getAlias().parallelStream().forEach(
            op -> newArrayList(filterableRequest, fullRequest).parallelStream().forEach(
                request -> {
                    SearchRequestBuilder searchRequestBuilder = queryAdapter.query(request.filter(String.format("%s %s %s", field, op, Arrays.toString(values))).build());
                    TermsQueryBuilder terms = (TermsQueryBuilder) ((BoolQueryBuilder) searchRequestBuilder.request().source().query()).must().get(0);

                    assertEquals(field, terms.fieldName());
                    assertTrue(asList(stream(values).map(value -> {

                        if (value instanceof String) {
                            String s = String.valueOf(value);
                            return s.replaceAll("\"", "");
                        }

                        return value;
                    }).toArray()).equals(terms.values()));
                }
            )
        );
    }

    @Test
    public void shouldValidateQueyUsingInOperatorByIds() {
        final String field = "id";
        final Set<Object> values = newHashSet("\"123\"", 456, "\"7a8b9\"");

        IN.getAlias().parallelStream().forEach(
            op -> newArrayList(filterableRequest, fullRequest).parallelStream().forEach(
                request -> {
                    SearchRequestBuilder searchRequestBuilder = queryAdapter.query(request.filter(String.format("%s %s %s", field, op, Arrays.toString(values.toArray()))).build());
                    IdsQueryBuilder idsQueryBuilder = (IdsQueryBuilder) ((BoolQueryBuilder) searchRequestBuilder.request().source().query()).must().get(0);

                    assertEquals("ids", idsQueryBuilder.getName());
                    assertEquals(values.stream().map(value -> value.toString().replaceAll("\"", "")).collect(toSet()), idsQueryBuilder.ids());
                }
            )
        );
    }

    @Test
    public void shouldReturnSearchRequestBuilderWithSingleOperatorAnd() {
        String fieldName1 = "field1";
        Object fieldValue1 = "string";

        String fieldName2 = "field2";
        Object fieldValue2 = 12345;

        newArrayList(filterableRequest, fullRequest).parallelStream().forEach(
            request -> {
                SearchRequestBuilder searchRequestBuilder = queryAdapter.query(request.filter(String.format("%s:\"%s\" AND %s:%s", fieldName1, fieldValue1, fieldName2, fieldValue2)).build());
                List<QueryBuilder> must = ((BoolQueryBuilder) searchRequestBuilder.request().source().query()).must();

                assertNotNull(must);
                assertTrue(must.size() == 2);
                assertEquals(fieldName1, ((MatchQueryBuilder) must.get(0)).fieldName());
                assertEquals(fieldValue1, ((MatchQueryBuilder) must.get(0)).value());
                assertEquals(fieldName2, ((MatchQueryBuilder) must.get(1)).fieldName());
                assertEquals(fieldValue2, ((MatchQueryBuilder) must.get(1)).value());
            }
        );
    }

    @Test
    public void shouldReturnSearchRequestBuilderWithSingleOperatorOr() {
        String fieldName1 = "field1";
        Object fieldValue1 = "string";

        String fieldName2 = "field2";
        Object fieldValue2 = 12345;

        newArrayList(filterableRequest, fullRequest).parallelStream().forEach(
            request -> {
                SearchRequestBuilder searchRequestBuilder = queryAdapter.query(request.filter(String.format("%s:\"%s\" OR %s:%s", fieldName1, fieldValue1, fieldName2, fieldValue2)).build());
                List<QueryBuilder> should = ((BoolQueryBuilder) searchRequestBuilder.request().source().query()).should();

                assertNotNull(should);
                assertTrue(should.size() == 2);
                assertEquals(fieldName1, ((MatchQueryBuilder) should.get(0)).fieldName());
                assertEquals(fieldValue1, ((MatchQueryBuilder) should.get(0)).value());
                assertEquals(fieldName2, ((MatchQueryBuilder) should.get(1)).fieldName());
                assertEquals(fieldValue2, ((MatchQueryBuilder) should.get(1)).value());
            }
        );
    }

    @Test
    public void shouldReturnSearchRequestBuilderWhenValueIsNullOnOperatorIsEqual() {
        String fieldName = "field1";
        List<Object> nullValues = newArrayList("NULL", null, "null");

        nullValues.parallelStream().forEach(
            nullValue -> newArrayList(filterableRequest, fullRequest).parallelStream().forEach(
                request -> {
                    SearchRequestBuilder searchRequestBuilder = queryAdapter.query(request.filter(String.format("%s:%s", fieldName, nullValue)).build());
                    List<QueryBuilder> mustNot = ((BoolQueryBuilder) searchRequestBuilder.request().source().query()).mustNot();

                    ExistsQueryBuilder existsQueryBuilder = (ExistsQueryBuilder) mustNot.get(0);
                    assertNotNull(existsQueryBuilder);
                    assertEquals(fieldName, existsQueryBuilder.fieldName());
                }
            )
        );
    }

    @Test
    public void shouldReturnSearchRequestBuilderWhenValueIsNullOnOperatorIsEqualWithNot() {
        String fieldName = "field1";
        List<Object> nullValues = newArrayList("NULL", null, "null");

        nullValues.parallelStream().forEach(
            nullValue -> newArrayList(filterableRequest, fullRequest).parallelStream().forEach(
                request -> {
                    SearchRequestBuilder searchRequestBuilder = queryAdapter.query(request.filter(String.format("NOT %s:%s", fieldName, nullValue)).build());
                    List<QueryBuilder> must = ((BoolQueryBuilder) searchRequestBuilder.request().source().query()).must();

                    ExistsQueryBuilder existsQueryBuilder = (ExistsQueryBuilder) must.get(0);
                    assertNotNull(existsQueryBuilder);
                    assertEquals(fieldName, existsQueryBuilder.fieldName());
                }
            )
        );
    }

    @Test
    public void shouldReturnSearchRequestBuilderWhenValueIsNullOnOperatorIsDifferent() {
        String fieldName = "field1";
        List<Object> nullValues = newArrayList("NULL", null, "null");

        nullValues.parallelStream().forEach(
            nullValue -> newArrayList(filterableRequest, fullRequest).parallelStream().forEach(
                request -> {
                    SearchRequestBuilder searchRequestBuilder = queryAdapter.query(request.filter(String.format("%s<>%s", fieldName, nullValue)).build());
                    List<QueryBuilder> must = ((BoolQueryBuilder) searchRequestBuilder.request().source().query()).must();

                    ExistsQueryBuilder existsQueryBuilder = (ExistsQueryBuilder) must.get(0);
                    assertNotNull(existsQueryBuilder);
                    assertEquals(fieldName, existsQueryBuilder.fieldName());
                }
            )
        );
    }

    @Test
    public void shouldReturnSearchRequestBuilderWhenValueIsNullOnOperatorIsDifferentWithNot() {
        String fieldName = "field1";
        List<Object> nullValues = newArrayList("NULL", null, "null");

        nullValues.parallelStream().forEach(
            nullValue -> newArrayList(filterableRequest, fullRequest).parallelStream().forEach(
                request -> {
                    SearchRequestBuilder searchRequestBuilder = queryAdapter.query(request.filter(String.format("NOT %s<>%s", fieldName, nullValue)).build());
                    List<QueryBuilder> mustNot = ((BoolQueryBuilder) searchRequestBuilder.request().source().query()).mustNot();

                    ExistsQueryBuilder existsQueryBuilder = (ExistsQueryBuilder) mustNot.get(0);
                    assertNotNull(existsQueryBuilder);
                    assertEquals(fieldName, existsQueryBuilder.fieldName());
                }
            )
        );
    }

    @Test
    public void shouldReturnSearchRequestBuilderWithSingleOperatorNot() {
        String fieldName1 = "field1";
        Object fieldValue1 = 1234324;

        newArrayList(filterableRequest, fullRequest).parallelStream().forEach(
            request -> {
                SearchRequestBuilder searchRequestBuilder = queryAdapter.query(request.filter(String.format("NOT %s:%s", fieldName1, fieldValue1)).build());
                MatchQueryBuilder mustNot = (MatchQueryBuilder) ((BoolQueryBuilder) searchRequestBuilder.request().source().query()).mustNot().get(0);

                assertNotNull(mustNot);
                assertEquals(fieldName1, mustNot.fieldName());
                assertEquals(fieldValue1, mustNot.value());
            }
        );
    }

    @Test
    public void shouldReturnSearchRequestBuilderByFacets() {
        Set<String> facets = newHashSet("field1", "field2", "field3", "nested1.field4", "nested1.field5", "nested2.field6");

        when(settingsAdapter.isTypeOf(INDEX_NAME, "nested1", FIELD_TYPE_NESTED)).thenReturn(true);
        when(settingsAdapter.isTypeOf(INDEX_NAME, "nested2", FIELD_TYPE_NESTED)).thenReturn(true);

        SearchApiRequest searchApiRequest = fullRequest.facets(facets).facetSize(10).build();
        SearchRequestBuilder searchRequestBuilder = queryAdapter.query(searchApiRequest);
        List<AggregationBuilder> aggregations = searchRequestBuilder.request().source().aggregations().getAggregatorFactories();

        assertNotNull(aggregations);
        assertTrue(aggregations.size() == 5);

        assertTrue(searchRequestBuilder.toString().contains("\"size\" : 10"));
        assertTrue(searchRequestBuilder.toString().contains("\"shard_size\" : 8"));
        assertTrue(facets.stream().map(s -> s.split("\\.")[0]).collect(toSet()).containsAll(aggregations.stream().map(AggregationBuilder::getName).collect(toSet())));
    }

    @Test
    public void shouldReturnSearchRequestBuilderWithSpecifiedFieldSources() {
        Set<String> includeFields = newHashSet("field1", "field2", "field3");
        Set<String> excludeFields = newHashSet("field3", "field4");

        concat(includeFields.stream(), excludeFields.stream()).forEach(field -> when(settingsAdapter.checkFieldName(INDEX_NAME, field, true)).thenReturn(true));

        newArrayList(filterableRequest, fullRequest).parallelStream().forEach(
            request -> {
                SearchRequestBuilder searchRequestBuilder = queryAdapter.query(request.includeFields(includeFields).excludeFields(excludeFields).build());
                FetchSourceContext fetchSourceContext = searchRequestBuilder.request().source().fetchSource();

                validateFetchSources(includeFields, excludeFields, fetchSourceContext);
            }
        );
    }

    @Test
    public void shouldReturnSimpleSearchRequestBuilderByQueryString() {
        String q = "Lorem Ipsum is simply dummy text of the printing and typesetting";

        newArrayList(filterableRequest, fullRequest).parallelStream().forEach(
            request -> {
                SearchRequestBuilder searchRequestBuilder = queryAdapter.query(request.q(q).build());
                QueryStringQueryBuilder queryStringQueryBuilder = (QueryStringQueryBuilder) ((BoolQueryBuilder) searchRequestBuilder.request().source().query()).should().get(0);

                assertNotNull(queryStringQueryBuilder);
                assertEquals(q, queryStringQueryBuilder.queryString());
                assertEquals(OR, queryStringQueryBuilder.defaultOperator());
            }
        );
    }

    @Test
    public void shouldReturnSimpleSearchRequestBuilderByQueryStringWithSpecifiedFieldToSearch() {
        String q = "Lorem Ipsum is simply dummy text of the printing and typesetting";

        String fieldName1 = "field1";
        float boostValue1 = 1.0f; // default boost value

        String fieldName2 = "field2";
        float boostValue2 = 2.0f;

        String fieldName3 = "field3";
        float boostValue3 = 5.0f;

        when(settingsAdapter.isTypeOf(INDEX_NAME, fieldName1, FIELD_TYPE_STRING)).thenReturn(true);
        when(settingsAdapter.isTypeOf(INDEX_NAME, fieldName2, FIELD_TYPE_STRING)).thenReturn(false);
        when(settingsAdapter.isTypeOf(INDEX_NAME, fieldName3, FIELD_TYPE_STRING)).thenReturn(false);

        Set<String> fields = Sets.newLinkedHashSet(newArrayList(String.format("%s", fieldName1), String.format("%s:%s", fieldName2, boostValue2), String.format("%s:%s", fieldName3, boostValue3)));

        newArrayList(filterableRequest, fullRequest).parallelStream().forEach(
            request -> {
                SearchRequestBuilder searchRequestBuilder = queryAdapter.query(request.q(q).fields(fields).build());
                QueryStringQueryBuilder queryStringQueryBuilder = (QueryStringQueryBuilder) ((BoolQueryBuilder) searchRequestBuilder.request().source().query()).should().get(0);

                assertNotNull(queryStringQueryBuilder);
                assertEquals(q, queryStringQueryBuilder.queryString());

                Map<String, Float> fieldsAndWeights = new HashMap<>(3);
                fieldsAndWeights.put(fieldName1 + ".raw", boostValue1);
                fieldsAndWeights.put(fieldName2, boostValue2);
                fieldsAndWeights.put(fieldName3, boostValue3);

                assertTrue(fieldsAndWeights.equals(queryStringQueryBuilder.fields()));
            }
        );
    }

    @Test
    public void shouldReturnSearchRequestBuilderByQueryStringWithValidMinimalShouldMatch() {
        String q = "Lorem Ipsum is simply dummy text of the printing and typesetting";
        List<String> validMMs = Lists.newArrayList("-100%", "100%", "75%", "-2");

        validMMs.forEach(
            mm -> newArrayList(filterableRequest, fullRequest).parallelStream().forEach(
                request -> {
                    SearchRequestBuilder searchRequestBuilder = queryAdapter.query(request.q(q).mm(mm).build());
                    QueryStringQueryBuilder queryStringQueryBuilder = (QueryStringQueryBuilder) ((BoolQueryBuilder) searchRequestBuilder.request().source().query()).should().get(0);

                    assertNotNull(queryStringQueryBuilder);
                    assertEquals(q, queryStringQueryBuilder.queryString());
                    assertEquals(mm, queryStringQueryBuilder.minimumShouldMatch());
                    assertEquals(OR, queryStringQueryBuilder.defaultOperator());
                }
            )
        );
    }

    @Test
    public void shouldThrowExceptionWhenMinimalShouldMatchIsInvalid() {
        String q = "Lorem Ipsum is simply dummy text of the printing and typesetting";
        List<String> invalidMMs = Lists.newArrayList("-101%", "101%", "75%.1", "75%,1", "75%123", "75%a");

        invalidMMs.forEach(
            mm -> newArrayList(filterableRequest, fullRequest).parallelStream().forEach(
                request -> {
                    boolean throwsException = false;
                    try {
                        queryAdapter.query(request.q(q).mm(mm).build());
                    } catch (IllegalArgumentException e) {
                        throwsException = true;
                    }
                    assertTrue(throwsException);
                }
            )
        );
    }

    /**
    * Full Tree Objects Representation (Recursive)
    *
    * Request: SearchApiRequest {
    *   index=my_index,
    *   mm=50%,
    *   fields=[field1, field2.raw:2.0, field3:5.0],
    *   includeFields=[field1, field2],
    *   excludeFields=[field3, field4],
    *   sort=field1 ASC field2 DESC field3 ASC,
    *   facets=[field1, field2],
    *   facetSize=10,
    *   q=Lorem Ipsum is simply dummy text of the printing and typesetting,
    *   from=0,
    *   size=20,
    *   filter=
    *       (field1 EQUAL "string" OR field2 DIFFERENT 5432 AND
    *           (field3 GREATER 3 AND
    *               (field4 LESS 8 OR field5 IN [1, "string", 1.2, true] AND
    *                   (field6.location VIEWPORT [[42.0, -74.0], [-40.0, -72.0]]))))
    * }
    *
    * 1 + BoolQueryBuilder
    * 	+ must
    * 		- QueryStringQueryBuilder
    * 		2 + BoolQueryBuilder
    * 			+ must
    * 				- RangeQueryBuilder (field3)
    * 				3 + BoolQueryBuilder
    * 					+ must
    * 						- TermsQueryBuilder (field5)
    * 						4 + BoolQueryBuilder
    * 							+ must
    * 								- GeoBoundingBoxQueryBuilder (field6)
    * 					+ should
    * 						3 - RangeQueryBuilder (field4)
    * 	+ must_not
    * 		- MatchQueryBuilder (field2)
    * 	+ should
    * 		- MatchQueryBuilder (field1)
    */
    @Test
    public void shouldReturnSimpleSearchRequestBuilderWithRecursiveRequest() {

        // QueryString
        String q = "Lorem Ipsum is simply dummy text of the printing and typesetting";
        String fieldName1 = "field1";
        float boostValue1 = 1.0f; // default boost value

        String fieldName2 = "field2.raw";
        float boostValue2 = 2.0f;

        String fieldName3 = "field3";
        float boostValue3 = 5.0f;
        Set<String> fields = Sets.newLinkedHashSet(newArrayList(String.format("%s", fieldName1), String.format("%s:%s", fieldName2, boostValue2), String.format("%s:%s", fieldName3, boostValue3)));
        String mm = "50%";

        // Filters
        String field1Name = "field1";
        String field1RelationalOperator = EQUAL.name();
        Object field1Value = "\"string\"";

        String field2Name = "field2";
        String field2RelationalOperator = DIFFERENT.name();
        Object field2Value = 5432;

        String field3Name = "field3";
        String field3RelationalOperator = GREATER.name();
        Object field3Value = 3;

        String field4Name = "field4";
        String field4RelationalOperator = LESS.name();
        Object field4Value = 8;

        String field5Name = "field5";
        String field5RelationalOperator = IN.name();
        Object[] field5Value = new Object[]{1, "\"string\"", 1.2, true};


        String field6Name = "field6.location";
        String field6RelationalOperator = VIEWPORT.name();
        double northEastLat = 42.0;
        double northEastLon = -74.0;
        double southWestLat = -40.0;
        double southWestLon = -72.0;

        when(settingsAdapter.isTypeOf(INDEX_NAME, field6Name, MappingType.FIELD_TYPE_GEOPOINT)).thenReturn(true);

        String filter = String.format("%s %s %s %s %s %s %s %s (%s %s %s %s (%s %s %s %s %s %s %s %s (%s %s [[%s,%s],[%s,%s]])))",
            field1Name, field1RelationalOperator, field1Value, OR.name(),
            field2Name, field2RelationalOperator, field2Value, AND.name(),
            field3Name, field3RelationalOperator, field3Value, AND.name(),
            field4Name, field4RelationalOperator, field4Value, OR.name(),
            field5Name, field5RelationalOperator, Arrays.toString(field5Value), AND.name(),
            field6Name, field6RelationalOperator, northEastLon, northEastLat, southWestLon, southWestLat
        );

        SearchApiRequest searchApiRequest = fullRequest
            .filter(filter)
            .q(q)
            .fields(fields)
            .mm(mm)
            .build();

        SearchRequestBuilder searchRequestBuilder = queryAdapter.query(searchApiRequest);
        SearchSourceBuilder source = searchRequestBuilder.request().source();

        // index
        assertEquals(searchApiRequest.getIndex(), searchRequestBuilder.request().indices()[0]);

        // filters
        List<QueryBuilder> mustFirstLevel = ((BoolQueryBuilder) source.query()).must();
        List<QueryBuilder> mustNotFirstLevel = ((BoolQueryBuilder) source.query()).mustNot();
        List<QueryBuilder> shouldFirstLevel = ((BoolQueryBuilder) source.query()).should();

        assertNotNull(mustFirstLevel);
        assertNotNull(mustNotFirstLevel);
        assertNotNull(shouldFirstLevel);
        assertEquals(1, mustFirstLevel.size());
        assertEquals(1, mustNotFirstLevel.size());
        assertEquals(2, shouldFirstLevel.size());

        // querystring
        QueryStringQueryBuilder queryStringQueryBuilder = (QueryStringQueryBuilder) shouldFirstLevel.get(0);
        Map<String, Float> fieldsAndWeights = new HashMap<>(3);
        fieldsAndWeights.put(fieldName1, boostValue1);
        fieldsAndWeights.put(fieldName2, boostValue2);
        fieldsAndWeights.put(fieldName3, boostValue3);
        assertNotNull(queryStringQueryBuilder);
        assertEquals(q, queryStringQueryBuilder.queryString());
        assertEquals(mm, queryStringQueryBuilder.minimumShouldMatch());
        assertEquals(OR, queryStringQueryBuilder.defaultOperator());
        assertTrue(fieldsAndWeights.equals(queryStringQueryBuilder.fields()));

        // field 1
        MatchQueryBuilder shouldMatchField1 = (MatchQueryBuilder) shouldFirstLevel.get(1);
        assertEquals(field1Name, shouldMatchField1.fieldName());
        assertEquals(String.valueOf(field1Value).replaceAll("\"", ""), shouldMatchField1.value());
        assertEquals(OR, shouldMatchField1.operator());

        // field 2
        MatchQueryBuilder mustNotMatchField2 = (MatchQueryBuilder) mustNotFirstLevel.get(0);
        assertEquals(field2Name, mustNotMatchField2.fieldName());
        assertEquals(field2Value, mustNotMatchField2.value());

        // Second Level
        List<QueryBuilder> mustSecondLevel = ((BoolQueryBuilder) mustFirstLevel.get(0)).must();
        assertTrue(mustSecondLevel.size() == 2);

        // field 3
        RangeQueryBuilder mustRangeSecondLevelField3 = (RangeQueryBuilder) mustSecondLevel.get(0);
        assertEquals(field3Name, mustRangeSecondLevelField3.fieldName());
        assertEquals(field3Value, mustRangeSecondLevelField3.from());
        assertNull(mustRangeSecondLevelField3.to());
        assertFalse(mustRangeSecondLevelField3.includeLower());
        assertTrue(mustRangeSecondLevelField3.includeUpper());

        BoolQueryBuilder queryBuilderThirdLevel = (BoolQueryBuilder) mustSecondLevel.get(1);
        List<QueryBuilder> shouldThirdLevel = queryBuilderThirdLevel.should(); //1
        List<QueryBuilder> mustThirdLevel = queryBuilderThirdLevel.must(); //2

        assertTrue(shouldThirdLevel.size() == 1);
        assertTrue(mustThirdLevel.size() == 2);

        // field 4
        RangeQueryBuilder shouldRangeThirdLevelField4 = (RangeQueryBuilder) shouldThirdLevel.get(0);
        assertEquals(field4Name, shouldRangeThirdLevelField4.fieldName());
        assertEquals(field4Value, shouldRangeThirdLevelField4.to());
        assertNull(shouldRangeThirdLevelField4.from());
        assertTrue(shouldRangeThirdLevelField4.includeLower());
        assertFalse(shouldRangeThirdLevelField4.includeUpper());

        // field 5
        TermsQueryBuilder mustTermsThirdLevelField5 = (TermsQueryBuilder) mustThirdLevel.get(0);
        assertEquals(field5Name, mustTermsThirdLevelField5.fieldName());
        assertTrue(asList(stream(field5Value).map(value -> {

            if (value instanceof String) {
                String s = String.valueOf(value);
                return s.replaceAll("\"", "");
            }

            return value;
        }).toArray()).equals(mustTermsThirdLevelField5.values()));

        // field 6
        GeoBoundingBoxQueryBuilder mustViewportFouthLevelField6 = (GeoBoundingBoxQueryBuilder) ((BoolQueryBuilder) mustThirdLevel.get(1)).must().get(0);
        int delta = 0;
        assertEquals(field6Name, mustViewportFouthLevelField6.fieldName());
        assertEquals(northEastLat, mustViewportFouthLevelField6.topLeft().getLat(), delta);
        assertEquals(southWestLon, mustViewportFouthLevelField6.topLeft().getLon(), delta);
        assertEquals(southWestLat, mustViewportFouthLevelField6.bottomRight().getLat(), delta);
        assertEquals(northEastLon, mustViewportFouthLevelField6.bottomRight().getLon(), delta);
    }

    @Test
    public void testFetchSourceFields() {
        String[] includes = {"field1", "field2"}, excludes = {"field3", "field4"};

        concat(stream(includes), stream(excludes)).forEach(field -> when(settingsAdapter.checkFieldName(INDEX_NAME, field, true)).thenReturn(true));

        newArrayList(filterableRequest, fullRequest).parallelStream().forEach(
            request -> {
                SearchRequestBuilder requestBuilder = queryAdapter.query(request.index(INDEX_NAME).includeFields(newHashSet(includes)).excludeFields(newHashSet(excludes)).build());
                FetchSourceContext fetchSourceContext = requestBuilder.request().source().fetchSource();

                assertNotNull(fetchSourceContext);
                assertThat(fetchSourceContext.includes(), arrayWithSize(2));
                assertThat(fetchSourceContext.includes(), arrayContainingInAnyOrder(includes));

                assertThat(fetchSourceContext.excludes(), arrayWithSize(2));
                assertThat(fetchSourceContext.excludes(), arrayContainingInAnyOrder(excludes));
            }
        );
    }

    @Test
    public void testFetchSourceEmptyFields() {
        newArrayList(filterableRequest, fullRequest).parallelStream().forEach(
            request -> {
                SearchRequestBuilder requestBuilder = queryAdapter.query(request.index(INDEX_NAME).build());

                FetchSourceContext fetchSourceContext = requestBuilder.request().source().fetchSource();
                assertNotNull(fetchSourceContext);
                assertThat(fetchSourceContext.includes(), emptyArray());
                assertThat(fetchSourceContext.excludes(), emptyArray());
            }
        );
    }

    @Test
    public void testFetchSourceIncludesEmptyFields() {
        String[] excludes = {"field3", "field4"};

        stream(excludes).forEach(field -> when(settingsAdapter.checkFieldName(INDEX_NAME, field, true)).thenReturn(true));

        newArrayList(filterableRequest, fullRequest).parallelStream().forEach(
            request -> {
                SearchRequestBuilder requestBuilder = queryAdapter.query(request.index(INDEX_NAME).excludeFields(newHashSet(excludes)).build());

                FetchSourceContext fetchSourceContext = requestBuilder.request().source().fetchSource();
                assertNotNull(fetchSourceContext);
                assertThat(fetchSourceContext.includes(), emptyArray());
                assertThat(fetchSourceContext.excludes(), arrayContainingInAnyOrder(excludes));
            }
        );
    }

    @Test
    public void testFetchSourceFilterExcludeFields() {
        String[] includes = {"field1", "field2"}, excludes = {"field1", "field3"};

        concat(stream(includes), stream(excludes)).forEach(field -> when(settingsAdapter.checkFieldName(INDEX_NAME, field, true)).thenReturn(true));

        newArrayList(filterableRequest, fullRequest).parallelStream().forEach(
            request -> {
                SearchRequestBuilder requestBuilder = queryAdapter.query(request.index(INDEX_NAME).includeFields(newHashSet(includes)).excludeFields(newHashSet(excludes)).build());
                FetchSourceContext fetchSourceContext = requestBuilder.request().source().fetchSource();
                assertNotNull(fetchSourceContext);
                assertThat(fetchSourceContext.includes(), arrayWithSize(2));
                assertThat(fetchSourceContext.includes(), arrayContainingInAnyOrder(includes));

                assertThat(fetchSourceContext.excludes(), arrayWithSize(1));
                assertThat(fetchSourceContext.excludes(), hasItemInArray("field3"));
            }
        );
    }

    @Test
    public void testPreparationQuery() {
        newArrayList(filterableRequest, fullRequest).parallelStream().forEach(
        request -> {
            SearchRequestBuilder builder = queryAdapter.query(request.build());

            assertEquals(request.build().getIndex(), builder.request().indices()[0]);
            assertThat(builder.request().source().query(), instanceOf(BoolQueryBuilder.class));
        }
        );
    }

    private String format(final String field, final Object value, final String relationalOperator) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(field).append(" ").append(relationalOperator).append(" ");

        if (value instanceof String) {
            stringBuilder.append("\"").append(value).append("\"");
        } else {
            stringBuilder.append(value);
        }

        return stringBuilder.toString();
    }
}
