package com.vivareal.search.api.model;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
public class SearchRequestTest {

    @Test
    public void shouldValidateQueryString() {
        SearchRequest request = new SearchRequest();
        System.out.println(request);
    }

}
