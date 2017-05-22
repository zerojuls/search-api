package com.vivareal.search.api.controller.v2;

import com.vivareal.search.api.model.SearchApiRequest;
import com.vivareal.search.api.model.SearchApiResponse;
import com.vivareal.search.api.service.ListingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
@RequestMapping({"/v2/listing", "/v2/listings"})
public class ListingController {

    @Autowired
    private ListingService listingService;

    @GetMapping("/{id:[a-z0-9\\-]+}")
    public SearchApiResponse getListingById(SearchApiRequest request, @PathVariable String id) { // FIXME accept request for non-filter params
        SearchApiResponse searchApiResponse = new SearchApiResponse();
        searchApiResponse.addListing(listingService.getListingById(request, id));
        return searchApiResponse;
    }

    @RequestMapping
    public SearchApiResponse getListings(SearchApiRequest request) {
        return listingService.getListings(request);
    }

    @RequestMapping(value = "/stream", method = GET, produces = "application/x-ndjson")
    public StreamingResponseBody stream(SearchApiRequest request) {
        return out -> listingService.stream(request, out);
    }
}
